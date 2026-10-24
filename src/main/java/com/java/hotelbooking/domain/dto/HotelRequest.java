package com.java.hotelbooking.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HotelRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 128) String city,
        @NotBlank @Size(max = 255) String address,
        @NotNull @Min(1) @Max(5) Integer stars,
        @Size(max = 2000) String description
) {
}
