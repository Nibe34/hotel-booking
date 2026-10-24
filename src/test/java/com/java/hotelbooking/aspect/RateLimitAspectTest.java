package com.java.hotelbooking.aspect;

import com.java.hotelbooking.domain.annotation.RateLimited;
import com.java.hotelbooking.exception.RateLimitExceededException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    private static final RateLimited THREE_PER_MINUTE = limit(3, 60);

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private Signature signature;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-24T12:00:00Z"));
    private RateLimitAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new RateLimitAspect(clock);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("BookingService.create(..)");
        authenticateAs("bob");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static RateLimited limit(int permits, long perSeconds) {
        return new RateLimited() {
            @Override
            public int permits() {
                return permits;
            }

            @Override
            public long perSeconds() {
                return perSeconds;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RateLimited.class;
            }
        };
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    @Test
    void callsWithinLimit_passThrough() {
        assertThatCode(() -> {
            aspect.checkLimit(joinPoint, THREE_PER_MINUTE);
            aspect.checkLimit(joinPoint, THREE_PER_MINUTE);
            aspect.checkLimit(joinPoint, THREE_PER_MINUTE);
        }).doesNotThrowAnyException();
    }

    @Test
    void callOverLimit_isRejectedWithRetryAfter() {
        aspect.checkLimit(joinPoint, THREE_PER_MINUTE);
        clock.advance(Duration.ofSeconds(20));
        aspect.checkLimit(joinPoint, THREE_PER_MINUTE);
        aspect.checkLimit(joinPoint, THREE_PER_MINUTE);
        clock.advance(Duration.ofSeconds(10));

        assertThatThrownBy(() -> aspect.checkLimit(joinPoint, THREE_PER_MINUTE))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many calls of BookingService.create(..): at most 3 per 60 s, retry in 30 s")
                .extracting("retryAfterSeconds").isEqualTo(30L);
    }

    @Test
    void oldCallsLeaveTheWindow_andFreePermits() {
        aspect.checkLimit(joinPoint, THREE_PER_MINUTE);
        aspect.checkLimit(joinPoint, THREE_PER_MINUTE);
        aspect.checkLimit(joinPoint, THREE_PER_MINUTE);

        clock.advance(Duration.ofSeconds(60));

        assertThatCode(() -> aspect.checkLimit(joinPoint, THREE_PER_MINUTE)).doesNotThrowAnyException();
    }

    @Test
    void rejectedCall_doesNotConsumePermit() {
        RateLimited onePerMinute = limit(1, 60);
        aspect.checkLimit(joinPoint, onePerMinute);
        clock.advance(Duration.ofSeconds(30));
        assertThatThrownBy(() -> aspect.checkLimit(joinPoint, onePerMinute))
                .isInstanceOf(RateLimitExceededException.class);

        clock.advance(Duration.ofSeconds(30));

        assertThatCode(() -> aspect.checkLimit(joinPoint, onePerMinute)).doesNotThrowAnyException();
    }

    @Test
    void limitsAreCountedPerUser() {
        RateLimited onePerMinute = limit(1, 60);
        aspect.checkLimit(joinPoint, onePerMinute);

        authenticateAs("alice");

        assertThatCode(() -> aspect.checkLimit(joinPoint, onePerMinute)).doesNotThrowAnyException();
    }
}
