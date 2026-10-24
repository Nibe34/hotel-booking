package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.model.BookingAuditEntry;
import com.java.hotelbooking.repository.BookingAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;

/**
 * Writes the booking audit trail. Used by BookingAuditAspect for user actions and directly by background
 * jobs. The actor is the authenticated user, or "system" when there is none (scheduled jobs, async tasks).
 */
@Component
@RequiredArgsConstructor
public class AuditTrail {

    public static final String SYSTEM_ACTOR = "system";

    private final BookingAuditRepository auditRepository;
    private final Clock clock;

    public BookingAuditEntry record(BookingResponse booking, AuditAction action) {
        BookingAuditEntry entry = new BookingAuditEntry();
        entry.setBookingId(booking.id());
        entry.setAction(action);
        entry.setStatus(booking.status());
        entry.setActor(currentActor());
        entry.setCreatedAt(clock.instant());
        return auditRepository.save(entry);
    }

    public static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !StringUtils.hasText(authentication.getName())) {
            return SYSTEM_ACTOR;
        }
        return authentication.getName();
    }
}
