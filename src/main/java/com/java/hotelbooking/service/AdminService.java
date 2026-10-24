package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.AdminStatsResponse;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.repository.BookingRepository;
import com.java.hotelbooking.repository.HotelRepository;
import com.java.hotelbooking.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public AdminStatsResponse stats() {
        Map<BookingStatus, Long> byStatus = new EnumMap<>(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) {
            byStatus.put(status, bookingRepository.countByStatus(status));
        }
        return new AdminStatsResponse(hotelRepository.count(), roomRepository.count(), byStatus);
    }
}
