package com.java.hotelbooking.aspect;

import com.java.hotelbooking.domain.annotation.Idempotent;
import com.java.hotelbooking.exception.BadRequestException;
import com.java.hotelbooking.exception.ConflictException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyAspectTest {

    private static final Idempotent DEFAULT = idempotent(600);

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-24T12:00:00Z"));
    private IdempotencyAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new IdempotencyAspect(clock);
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.toLongString()).thenReturn("BookingResponse BookingController.create(..)");
        lenient().when(signature.toShortString()).thenReturn("BookingController.create(..)");
        authenticateAs("bob");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private static Idempotent idempotent(long ttlSeconds) {
        return new Idempotent() {
            @Override
            public String header() {
                return "Idempotency-Key";
            }

            @Override
            public long ttlSeconds() {
                return ttlSeconds;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Idempotent.class;
            }
        };
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private void requestWithKey(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bookings");
        if (key != null) {
            request.addHeader("Idempotency-Key", key);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void noHeader_alwaysProceeds() throws Throwable {
        requestWithKey(null);
        when(joinPoint.proceed()).thenReturn("first", "second");

        assertThat(aspect.replayOrProceed(joinPoint, DEFAULT)).isEqualTo("first");
        assertThat(aspect.replayOrProceed(joinPoint, DEFAULT)).isEqualTo("second");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void outsideHttpRequest_proceeds() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        assertThat(aspect.replayOrProceed(joinPoint, DEFAULT)).isEqualTo("result");
        verify(joinPoint).proceed();
    }

    @Test
    void repeatedKey_returnsStoredResultWithoutProceeding() throws Throwable {
        requestWithKey("abc-1");
        Object created = new Object();
        when(joinPoint.proceed()).thenReturn(created);

        Object first = aspect.replayOrProceed(joinPoint, DEFAULT);
        Object second = aspect.replayOrProceed(joinPoint, DEFAULT);

        assertThat(second).isSameAs(first).isSameAs(created);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void differentKeys_eachProceed() throws Throwable {
        when(joinPoint.proceed()).thenReturn("a", "b");

        requestWithKey("key-a");
        Object a = aspect.replayOrProceed(joinPoint, DEFAULT);
        requestWithKey("key-b");
        Object b = aspect.replayOrProceed(joinPoint, DEFAULT);

        assertThat(a).isEqualTo("a");
        assertThat(b).isEqualTo("b");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void sameKeyFromAnotherUser_isNotReplayed() throws Throwable {
        requestWithKey("shared");
        when(joinPoint.proceed()).thenReturn("bob's booking", "eve's booking");

        aspect.replayOrProceed(joinPoint, DEFAULT);
        authenticateAs("eve");
        Object eveResult = aspect.replayOrProceed(joinPoint, DEFAULT);

        assertThat(eveResult).isEqualTo("eve's booking");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void expiredKey_proceedsAgain() throws Throwable {
        requestWithKey("abc");
        when(joinPoint.proceed()).thenReturn("old", "new");
        Idempotent shortLived = idempotent(60);

        aspect.replayOrProceed(joinPoint, shortLived);
        clock.advance(Duration.ofSeconds(59));
        Object stillReplayed = aspect.replayOrProceed(joinPoint, shortLived);
        clock.advance(Duration.ofSeconds(1));
        Object afterExpiry = aspect.replayOrProceed(joinPoint, shortLived);

        assertThat(stillReplayed).isEqualTo("old");
        assertThat(afterExpiry).isEqualTo("new");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void failedCall_isNotStored_soRetryWithSameKeyProceeds() throws Throwable {
        requestWithKey("abc");
        when(joinPoint.proceed()).thenThrow(new ConflictException("Room is already booked")).thenReturn("booked");

        assertThatThrownBy(() -> aspect.replayOrProceed(joinPoint, DEFAULT)).isInstanceOf(ConflictException.class);
        Object retry = aspect.replayOrProceed(joinPoint, DEFAULT);

        assertThat(retry).isEqualTo("booked");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void tooLongKey_isRejectedWithoutProceeding() throws Throwable {
        requestWithKey("k".repeat(IdempotencyAspect.MAX_KEY_LENGTH + 1));

        assertThatThrownBy(() -> aspect.replayOrProceed(joinPoint, DEFAULT))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Idempotency-Key must be at most 128 characters");
        verify(joinPoint, never()).proceed();
    }
}
