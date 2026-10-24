package com.java.hotelbooking.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BookingTest {

    @Test
    void onCreate_createdAtNotSet_fillsIt() {
        Booking booking = new Booking();

        booking.onCreate();

        assertThat(booking.getCreatedAt()).isNotNull();
    }

    @Test
    void onCreate_createdAtAlreadySet_keepsOriginalValue() {
        Instant original = Instant.parse("2026-01-01T00:00:00Z");
        Booking booking = new Booking();
        booking.setCreatedAt(original);

        booking.onCreate();

        assertThat(booking.getCreatedAt()).isEqualTo(original);
    }
}
