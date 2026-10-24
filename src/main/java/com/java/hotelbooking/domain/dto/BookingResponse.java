package com.java.hotelbooking.domain.dto;

import com.java.hotelbooking.domain.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        Long id,
        Long roomId,
        String roomNumber,
        Long hotelId,
        String hotelName,
        String guestUsername,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer guestsCount,
        BigDecimal totalPrice,
        BookingStatus status,
        Instant createdAt
) {
}
