package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.model.BookingAuditEntry;
import com.java.hotelbooking.repository.BookingAuditRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditTrailTest {

    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    @Mock
    private BookingAuditRepository auditRepository;

    private AuditTrail auditTrail;

    @BeforeEach
    void setUp() {
        auditTrail = new AuditTrail(auditRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private BookingResponse booking() {
        return new BookingResponse(9L, 1L, "101", 1L, "Grand", "bob", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 2), 1, BigDecimal.TEN, BookingStatus.CANCELLED, null);
    }

    @Test
    void record_savesEntryForCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        when(auditRepository.save(any(BookingAuditEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingAuditEntry entry = auditTrail.record(booking(), AuditAction.CANCEL);

        assertThat(entry.getBookingId()).isEqualTo(9L);
        assertThat(entry.getAction()).isEqualTo(AuditAction.CANCEL);
        assertThat(entry.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(entry.getActor()).isEqualTo("alice");
        assertThat(entry.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void currentActor_noAuthentication_isSystem() {
        assertThat(AuditTrail.currentActor()).isEqualTo("system");
    }

    @Test
    void currentActor_unauthenticatedToken_isSystem() {
        UsernamePasswordAuthenticationToken unauthenticated = new UsernamePasswordAuthenticationToken("bob", "pw");
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);

        assertThat(AuditTrail.currentActor()).isEqualTo("system");
    }

    @Test
    void currentActor_anonymousUser_isReportedByName() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(AuditTrail.currentActor()).isEqualTo("anonymousUser");
    }
}
