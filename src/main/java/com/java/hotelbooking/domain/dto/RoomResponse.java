package com.java.hotelbooking.domain.dto;

import com.java.hotelbooking.domain.enums.RoomType;

import java.math.BigDecimal;

public record RoomResponse(
        Long id,
        Long hotelId,
        String hotelName,
        String roomNumber,
        RoomType type,
        Integer capacity,
        BigDecimal pricePerNight
) {
}
