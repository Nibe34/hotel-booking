package com.java.hotelbooking.service.notification;

import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.event.BookingChangedEvent;
import com.java.hotelbooking.domain.event.CheckInReminderEvent;
import com.java.hotelbooking.integration.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs after the booking transaction has committed (so a guest is never told about a change that was rolled
 * back) and only hands the mail over to the async executor. If the executor is full the mail goes to the
 * outbox instead - the booking request itself must not fail because of a notification.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingNotificationListener {

    private final GuestNotifier notifier;
    private final PendingEmailOutbox outbox;

    @TransactionalEventListener(fallbackExecution = true)
    public void onBookingChanged(BookingChangedEvent event) {
        dispatch(emailFor(event));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onCheckInReminder(CheckInReminderEvent event) {
        dispatch(emailFor(event));
    }

    private void dispatch(Email email) {
        try {
            notifier.send(email);
        } catch (TaskRejectedException e) {
            log.warn("Async executor is full, e-mail '{}' to {} is queued for a later retry", email.subject(), email.to());
            outbox.add(email);
        }
    }

    static Email emailFor(BookingChangedEvent event) {
        BookingResponse booking = event.booking();
        String what = switch (event.action()) {
            case CREATE -> "received, waiting for confirmation";
            case CONFIRM -> "confirmed";
            case CANCEL -> "cancelled";
        };
        String subject = "Booking #" + booking.id() + " " + what;
        String body = "Hotel %s, room %s, %s - %s, total %s UAH. Status: %s.".formatted(booking.hotelName(),
                booking.roomNumber(), booking.checkIn(), booking.checkOut(), booking.totalPrice(), booking.status());
        return new Email(booking.guestUsername(), subject, body);
    }

    static Email emailFor(CheckInReminderEvent event) {
        BookingResponse booking = event.booking();
        String when = switch (event.daysBefore()) {
            case 0 -> "today";
            case 1 -> "tomorrow";
            default -> "in " + event.daysBefore() + " days";
        };
        String subject = "Reminder: your check-in is " + when;
        String body = "Booking #%d: hotel %s, room %s, check-in %s, check-out %s.".formatted(booking.id(),
                booking.hotelName(), booking.roomNumber(), booking.checkIn(), booking.checkOut());
        return new Email(booking.guestUsername(), subject, body);
    }
}
