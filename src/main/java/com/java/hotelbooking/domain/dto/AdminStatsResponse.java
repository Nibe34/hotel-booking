package com.java.hotelbooking.domain.dto;

import com.java.hotelbooking.domain.enums.BookingStatus;

import java.util.Map;

public record AdminStatsResponse(
        long hotels,
        long rooms,
        Map<BookingStatus, Long> bookingsByStatus
) {
}
