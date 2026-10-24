package com.java.hotelbooking.service.notification;

import com.java.hotelbooking.integration.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PendingEmailOutboxTest {

    private static final Email FIRST = new Email("bob", "Booking #1 confirmed", "...");
    private static final Email SECOND = new Email("alice", "Booking #2 cancelled", "...");

    @Mock
    private GuestNotifier notifier;

    @InjectMocks
    private PendingEmailOutbox outbox;

    @Test
    void retryPending_executorHasRoom_resubmitsAllInOrder() {
        outbox.add(FIRST);
        outbox.add(SECOND);

        outbox.retryPending();

        var order = inOrder(notifier);
        order.verify(notifier).send(FIRST);
        order.verify(notifier).send(SECOND);
        assertThat(outbox.size()).isZero();
    }

    @Test
    void retryPending_stillRejected_keepsMailAndStops() {
        outbox.add(FIRST);
        outbox.add(SECOND);
        doThrow(new TaskRejectedException("full")).when(notifier).send(FIRST);

        outbox.retryPending();

        verify(notifier, times(1)).send(any());
        verify(notifier, never()).send(SECOND);
        assertThat(outbox.size()).isEqualTo(2);
    }

    @Test
    void retryPending_rejectedOnceThenAccepted_deliversOnNextRun() {
        outbox.add(FIRST);
        doThrow(new TaskRejectedException("full")).doNothing().when(notifier).send(FIRST);

        outbox.retryPending();
        outbox.retryPending();

        verify(notifier, times(2)).send(FIRST);
        assertThat(outbox.size()).isZero();
    }

    @Test
    void retryPending_emptyOutbox_doesNothing() {
        outbox.retryPending();

        verify(notifier, never()).send(any());
    }
}
