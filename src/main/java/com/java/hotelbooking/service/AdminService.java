package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.dto.AdminStatsResponse;
import com.java.hotelbooking.domain.dto.BookingAuditResponse;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.mapper.BookingAuditMapper;
import com.java.hotelbooking.domain.model.BookingAuditEntry;
import com.java.hotelbooking.repository.BookingAuditRepository;
import com.java.hotelbooking.repository.BookingRepository;
import com.java.hotelbooking.repository.HotelRepository;
import com.java.hotelbooking.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final BookingAuditRepository auditRepository;
    private final BookingAuditMapper auditMapper;

    public AdminStatsResponse stats() {
        Map<BookingStatus, Long> byStatus = new EnumMap<>(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) {
            byStatus.put(status, bookingRepository.countByStatus(status));
        }
        return new AdminStatsResponse(hotelRepository.count(), roomRepository.count(), byStatus);
    }

    /** Audit trail of one booking (oldest first) or, without bookingId, the 100 latest entries. */
    public List<BookingAuditResponse> audit(Long bookingId) {
        List<BookingAuditEntry> entries = bookingId == null
                ? auditRepository.findTop100ByOrderByIdDesc()
                : auditRepository.findByBookingIdOrderByIdAsc(bookingId);
        return entries.stream().map(auditMapper::toResponse).toList();
    }
}
