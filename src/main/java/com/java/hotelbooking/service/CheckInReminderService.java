package com.java.hotelbooking.service;

import com.java.hotelbooking.domain.event.CheckInReminderEvent;
import com.java.hotelbooking.domain.mapper.BookingMapper;
import com.java.hotelbooking.domain.model.Booking;
import com.java.hotelbooking.domain.model.BookingReminder;
import com.java.hotelbooking.repository.BookingReminderRepository;
import com.java.hotelbooking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Check-in reminders, run by CheckInReminderJob. Every sent reminder is recorded in booking_reminders
 * (unique per booking and daysBefore), so running the job again never sends the same reminder twice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckInReminderService {

    private final BookingRepository bookingRepository;
    private final BookingReminderRepository reminderRepository;
    private final BookingMapper bookingMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Sends a reminder to guests of CONFIRMED bookings whose check-in is exactly {@code daysBefore} days from today.
     * The e-mails go out after the transaction commits (BookingNotificationListener).
     *
     * @return how many reminders were sent
     */
    @Transactional
    public int sendReminders(int daysBefore, int batchSize) {
        if (daysBefore < 0) {
            throw new IllegalArgumentException("daysBefore must not be negative, was " + daysBefore);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
        }

        LocalDate checkIn = LocalDate.now(clock).plusDays(daysBefore);
        List<Booking> due = bookingRepository.findDueForReminder(checkIn, daysBefore, Limit.of(batchSize));
        for (Booking booking : due) {
            BookingReminder reminder = new BookingReminder();
            reminder.setBookingId(booking.getId());
            reminder.setDaysBefore(daysBefore);
            reminder.setSentAt(clock.instant());
            reminderRepository.save(reminder);
            eventPublisher.publishEvent(new CheckInReminderEvent(bookingMapper.toResponse(booking), daysBefore));
        }
        if (!due.isEmpty()) {
            log.info("Sent {} check-in reminder(s) for {} (daysBefore={})", due.size(), checkIn, daysBefore);
        }
        return due.size();
    }
}
