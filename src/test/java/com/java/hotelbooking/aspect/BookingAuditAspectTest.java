package com.java.hotelbooking.aspect;

import com.java.hotelbooking.domain.annotation.AuditBooking;
import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.model.BookingAuditEntry;
import com.java.hotelbooking.repository.BookingAuditRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingAuditAspectTest {

    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    @Mock
    private BookingAuditRepository auditRepository;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private Signature signature;

    private BookingAuditAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new BookingAuditAspect(auditRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("BookingService.confirm(..)");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
    }

    private AuditBooking auditing(AuditAction action) {
        return new AuditBooking() {
            @Override
            public AuditAction value() {
                return action;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return AuditBooking.class;
            }
        };
    }

    private BookingResponse booking(long id, BookingStatus status) {
        return new BookingResponse(id, 1L, "101", 1L, "Grand", "bob", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 2), 1, new BigDecimal("100.00"), status, null);
    }

    @Test
    void audit_returnedBooking_savesEntryWithActionStatusActorAndTime() {
        authenticateAs("alice");

        aspect.audit(joinPoint, auditing(AuditAction.CONFIRM), booking(7L, BookingStatus.CONFIRMED));

        ArgumentCaptor<BookingAuditEntry> captor = ArgumentCaptor.forClass(BookingAuditEntry.class);
        verify(auditRepository).save(captor.capture());
        BookingAuditEntry entry = captor.getValue();
        assertThat(entry.getBookingId()).isEqualTo(7L);
        assertThat(entry.getAction()).isEqualTo(AuditAction.CONFIRM);
        assertThat(entry.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(entry.getActor()).isEqualTo("alice");
        assertThat(entry.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void audit_withoutAuthenticatedUser_recordsSystemAsActor() {
        aspect.audit(joinPoint, auditing(AuditAction.CANCEL), booking(3L, BookingStatus.CANCELLED));

        ArgumentCaptor<BookingAuditEntry> captor = ArgumentCaptor.forClass(BookingAuditEntry.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getActor()).isEqualTo("system");
        assertThat(captor.getValue().getAction()).isEqualTo(AuditAction.CANCEL);
    }

    @Test
    void audit_resultIsNotABooking_savesNothing() {
        aspect.audit(joinPoint, auditing(AuditAction.CREATE), "unexpected");

        verify(auditRepository, never()).save(any());
    }

    @Test
    void audit_nullResult_savesNothing() {
        aspect.audit(joinPoint, auditing(AuditAction.CREATE), null);

        verify(auditRepository, never()).save(any());
    }
}
