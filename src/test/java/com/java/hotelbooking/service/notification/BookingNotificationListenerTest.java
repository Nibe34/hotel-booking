package com.java.hotelbooking.service.notification;

import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.domain.event.BookingChangedEvent;
import com.java.hotelbooking.integration.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingNotificationListenerTest {

    @Mock
    private GuestNotifier notifier;

    @Mock
    private PendingEmailOutbox outbox;

    @InjectMocks
    private BookingNotificationListener listener;

    private BookingChangedEvent event(AuditAction action, BookingStatus status) {
        return new BookingChangedEvent(new BookingResponse(7L, 1L, "101", 1L, "Grand", "bob",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), 2, new BigDecimal("200.00"), status, null),
                action);
    }

    @Test
    void onBookingChanged_handsMailToAsyncNotifier() {
        listener.onBookingChanged(event(AuditAction.CONFIRM, BookingStatus.CONFIRMED));

        verify(notifier).send(new Email("bob", "Booking #7 confirmed",
                "Hotel Grand, room 101, 2026-11-01 - 2026-11-03, total 200.00 UAH. Status: CONFIRMED."));
        verify(outbox, never()).add(any());
    }

    @Test
    void onBookingChanged_executorFull_parksMailInOutboxInsteadOfFailing() {
        doThrow(new TaskRejectedException("full")).when(notifier).send(any());

        assertThatCode(() -> listener.onBookingChanged(event(AuditAction.CREATE, BookingStatus.PENDING)))
                .doesNotThrowAnyException();

        verify(outbox).add(new Email("bob", "Booking #7 received, waiting for confirmation",
                "Hotel Grand, room 101, 2026-11-01 - 2026-11-03, total 200.00 UAH. Status: PENDING."));
    }

    @ParameterizedTest
    @CsvSource({
            "CREATE,  PENDING,   'Booking #7 received, waiting for confirmation'",
            "CONFIRM, CONFIRMED, Booking #7 confirmed",
            "CANCEL,  CANCELLED, Booking #7 cancelled"
    })
    void emailFor_subjectDependsOnAction(AuditAction action, BookingStatus status, String subject) {
        Email email = BookingNotificationListener.emailFor(event(action, status));

        assertThat(email.to()).isEqualTo("bob");
        assertThat(email.subject()).isEqualTo(subject);
        assertThat(email.body()).endsWith("Status: " + status + ".");
    }
}
