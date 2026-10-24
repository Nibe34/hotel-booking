package com.java.hotelbooking.async;

import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.event.BookingChangedEvent;
import com.java.hotelbooking.exception.ServiceBusyException;
import com.java.hotelbooking.integration.Invoice;
import com.java.hotelbooking.integration.InvoiceRenderer;
import com.java.hotelbooking.service.BookingDocumentService;
import com.java.hotelbooking.service.BookingService;
import com.java.hotelbooking.service.notification.BookingNotificationListener;
import com.java.hotelbooking.service.notification.PendingEmailOutbox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/**
 * Executor with one thread and a queue of one: the third task cannot be accepted.
 * Each caller handles the rejection by an explicit decision instead of an accidental 500.
 */
@SpringJUnitConfig(AsyncTestContext.class)
@TestPropertySource(properties = {
        "hotel.async.core-pool-size=1",
        "hotel.async.max-pool-size=1",
        "hotel.async.queue-capacity=1"
})
class AsyncRejectionTest {

    @Autowired
    private ControllableSlowCalls slowCalls;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    private InvoiceRenderer invoiceRenderer;

    @Autowired
    private BookingDocumentService documentService;

    @Autowired
    private BookingNotificationListener notificationListener;

    @Autowired
    private PendingEmailOutbox outbox;

    @MockitoBean
    private BookingService bookingService;

    @AfterEach
    void releaseEverything() {
        slowCalls.releaseAll();
        await().atMost(Duration.ofSeconds(5)).until(() -> taskExecutor.getActiveCount() == 0
                && taskExecutor.getThreadPoolExecutor().getQueue().isEmpty());
        slowCalls.clear();
    }

    @Test
    void fullExecutor_rejectsTask_andEveryCallerHandlesItOnPurpose() throws Exception {
        when(bookingService.getById(1L)).thenReturn(TestData.booking(1L));
        slowCalls.block("invoice");
        CompletableFuture<Invoice> running = invoiceRenderer.render(TestData.booking(1L)); // takes the only thread
        CompletableFuture<Invoice> queued = invoiceRenderer.render(TestData.booking(2L));  // takes the only queue slot

        // 1. raw behaviour: the executor refuses the third task
        assertThatThrownBy(() -> invoiceRenderer.render(TestData.booking(3L)))
                .isInstanceOf(TaskRejectedException.class);

        // 2. request waiting for a result -> clear "try again later" (503 + Retry-After), not a random 500
        assertThatThrownBy(() -> documentService.invoice(1L, "USD"))
                .isInstanceOf(ServiceBusyException.class)
                .hasMessage("Too many documents are being prepared right now, try again in 5 seconds");

        // 3. fire-and-forget e-mail -> parked in the outbox, the booking flow is not disturbed
        assertThatCode(() -> notificationListener.onBookingChanged(
                new BookingChangedEvent(TestData.booking(1L), AuditAction.CREATE))).doesNotThrowAnyException();
        assertThat(outbox.size()).isEqualTo(1);

        // the load goes away ...
        slowCalls.release("invoice");
        running.get(2, TimeUnit.SECONDS);
        queued.get(2, TimeUnit.SECONDS);

        // ... and the outbox retry delivers the parked e-mail
        outbox.retryPending();
        assertThat(outbox.size()).isZero();
        assertThat(slowCalls.awaitFinished("mail")).isTrue();
    }
}
