package com.java.hotelbooking.domain.dto;

public record HotelResponse(
        Long id,
        String name,
        String city,
        String address,
        Integer stars,
        String description
) {
}
