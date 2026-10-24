package com.java.hotelbooking.domain.dto;

import com.java.hotelbooking.domain.enums.RoomType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RoomRequest(
        @NotBlank @Size(max = 16) String roomNumber,
        @NotNull RoomType type,
        @NotNull @Min(1) @Max(10) Integer capacity,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal pricePerNight
) {
}
