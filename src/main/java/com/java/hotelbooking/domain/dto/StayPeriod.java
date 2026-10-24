package com.java.hotelbooking.domain.dto;

import java.time.LocalDate;

/**
 * Anything that describes a hotel stay; lets {@code @ValidStayPeriod} be reused on any such DTO.
 */
public interface StayPeriod {

    LocalDate checkIn();

    LocalDate checkOut();
}
