package com.java.hotelbooking.repository;

import com.java.hotelbooking.domain.model.BookingReminder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingReminderRepository extends JpaRepository<BookingReminder, Long> {
}
