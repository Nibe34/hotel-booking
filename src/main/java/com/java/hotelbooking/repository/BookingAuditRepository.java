package com.java.hotelbooking.repository;

import com.java.hotelbooking.domain.model.BookingAuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingAuditRepository extends JpaRepository<BookingAuditEntry, Long> {

    List<BookingAuditEntry> findByBookingIdOrderByIdAsc(Long bookingId);

    List<BookingAuditEntry> findTop100ByOrderByIdDesc();
}
