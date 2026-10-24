package com.java.hotelbooking.domain.dto;

import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.enums.BookingStatus;

import java.time.Instant;

public record BookingAuditResponse(
        Long id,
        Long bookingId,
        AuditAction action,
        BookingStatus status,
        String actor,
        Instant createdAt
) {
}
