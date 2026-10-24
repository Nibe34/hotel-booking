package com.java.hotelbooking.domain.dto;

import com.java.hotelbooking.domain.annotation.ValidStayPeriod;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@ValidStayPeriod(maxNights = 30)
public record BookingCreateRequest(
        @NotNull Long roomId,
        @NotNull @FutureOrPresent LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @NotNull @Min(1) Integer guestsCount
) implements StayPeriod {
}
