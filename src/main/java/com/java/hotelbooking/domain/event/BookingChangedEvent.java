package com.java.hotelbooking.domain.event;

import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.AuditAction;

/** Published by BookingService after a booking was created, confirmed or cancelled. */
public record BookingChangedEvent(BookingResponse booking, AuditAction action) {
}
