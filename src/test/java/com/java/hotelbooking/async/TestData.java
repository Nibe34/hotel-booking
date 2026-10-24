package com.java.hotelbooking.async;

import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

final class TestData {

    private TestData() {
    }

    static BookingResponse booking(long id) {
        return new BookingResponse(id, 1L, "101", 1L, "Grand", "bob", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 3), 2, new BigDecimal("100.00"), BookingStatus.CONFIRMED, null);
    }
}
