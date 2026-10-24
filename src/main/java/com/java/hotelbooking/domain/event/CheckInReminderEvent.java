package com.java.hotelbooking.domain.event;

import com.java.hotelbooking.domain.dto.BookingResponse;

/** Published by CheckInReminderService for each booking that should get a reminder. */
public record CheckInReminderEvent(BookingResponse booking, int daysBefore) {
}
