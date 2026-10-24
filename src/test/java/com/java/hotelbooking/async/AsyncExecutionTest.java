package com.java.hotelbooking.async;

import com.java.hotelbooking.configuration.AsyncConfig;
import com.java.hotelbooking.domain.dto.BookingInvoiceResponse;
import com.java.hotelbooking.domain.enums.AuditAction;
import com.java.hotelbooking.domain.event.BookingChangedEvent;
import com.java.hotelbooking.exception.ServiceBusyException;
import com.java.hotelbooking.integration.Email;
import com.java.hotelbooking.integration.Invoice;
import com.java.hotelbooking.integration.InvoiceRenderer;
import com.java.hotelbooking.service.BookingDocumentService;
import com.java.hotelbooking.service.BookingService;
import com.java.hotelbooking.service.notification.BookingNotificationListener;
import com.java.hotelbooking.service.notification.GuestNotifier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@SpringJUnitConfig(AsyncTestContext.class)
@TestPropertySource(properties = {
        "hotel.async.thread-name-prefix=hotel-async-",
        "hotel.async.core-pool-size=4",
        "hotel.async.max-pool-size=4",
        "hotel.async.queue-capacity=10",
        "hotel.async.exchange-rate-timeout=300ms",
        "hotel.async.documents-timeout=1s",
        "hotel.simulation.unreachable-recipients=ghost"
})
class AsyncExecutionTest {

    @Autowired
    private ControllableSlowCalls slowCalls;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    private AsyncConfig asyncConfig;

    @Autowired
    private InvoiceRenderer invoiceRenderer;

    @Autowired
    private GuestNotifier guestNotifier;

    @Autowired
    private BookingNotificationListener notificationListener;

    @Autowired
    private BookingDocumentService documentService;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        when(bookingService.getById(1L)).thenReturn(TestData.booking(1L));
    }

    @AfterEach
    void releaseEverything() {
        slowCalls.releaseAll();
        await().atMost(Duration.ofSeconds(5)).until(() -> taskExecutor.getActiveCount() == 0
                && taskExecutor.getThreadPoolExecutor().getQueue().isEmpty());
        slowCalls.clear();
    }

    // ---------- executor ----------

    @Test
    void asyncExecutor_isOurBoundedThreadPool() {
        assertThat(asyncConfig.getAsyncExecutor()).isSameAs(taskExecutor);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("hotel-async-");
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(10);
    }

    // ---------- the slow method no longer blocks the caller ----------

    @Test
    void render_returnsBeforeRenderingFinishes_andRunsOnOwnExecutor() throws Exception {
        slowCalls.block("invoice");

        CompletableFuture<Invoice> future = invoiceRenderer.render(TestData.booking(1L));

        assertThat(future).isNotDone();
        assertThat(slowCalls.hasFinished("invoice")).isFalse();

        slowCalls.release("invoice");
        Invoice invoice = future.get(2, TimeUnit.SECONDS);

        assertThat(invoice.number()).isEqualTo("INV-000001");
        assertThat(slowCalls.threadOf("invoice"))
                .startsWith("hotel-async-")
                .doesNotStartWith("task-")
                .doesNotContain("SimpleAsyncTaskExecutor")
                .isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    void bookingChanged_fireAndForget_listenerReturnsBeforeMailIsSent() throws Exception {
        slowCalls.block("mail");

        notificationListener.onBookingChanged(new BookingChangedEvent(TestData.booking(1L), AuditAction.CONFIRM));

        assertThat(slowCalls.hasFinished("mail")).isFalse();
        slowCalls.release("mail");
        assertThat(slowCalls.awaitFinished("mail")).isTrue();
        assertThat(slowCalls.threadOf("mail")).startsWith("hotel-async-");
    }

    // ---------- two independent calls really run at the same time ----------

    @Test
    void invoice_rendersAndConvertsConcurrently_andCombinesBothResults() {
        // both operations have to meet at a barrier - impossible if they ran one after another
        slowCalls.mustRunConcurrently("invoice", "exchange-rate");

        BookingInvoiceResponse result = documentService.invoice(1L, "USD");

        assertThat(result.invoiceNumber()).isEqualTo("INV-000001");
        assertThat(result.invoiceSizeBytes()).isPositive();
        assertThat(result.totalUah()).isEqualByComparingTo("100.00");
        assertThat(result.convertedTotal()).isEqualByComparingTo("2.41");
        assertThat(result.note()).isNull();
        assertThat(slowCalls.threadOf("invoice")).startsWith("hotel-async-");
        assertThat(slowCalls.threadOf("exchange-rate")).startsWith("hotel-async-")
                .isNotEqualTo(slowCalls.threadOf("invoice"));
    }

    // ---------- failures do not break the caller ----------

    @Test
    void invoice_conversionFails_returnsInvoiceWithFallbackNote() {
        BookingInvoiceResponse result = documentService.invoice(1L, "XYZ");

        assertThat(result.invoiceNumber()).isEqualTo("INV-000001");
        assertThat(result.convertedTotal()).isNull();
        assertThat(result.note()).isEqualTo("Price in XYZ is not available: No exchange rate from UAH to XYZ");
    }

    @Test
    void asyncVoidFailure_isRecordedByUncaughtExceptionHandler_notSwallowed() {
        assertThatCode(() -> guestNotifier.send(new Email("ghost", "Booking #1 confirmed", "...")))
                .doesNotThrowAnyException();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            Counter failures = meterRegistry.find("hotel.async.failures").tag("method", "GuestNotifier.send").counter();
            assertThat(failures).isNotNull();
            assertThat(failures.count()).isEqualTo(1.0);
        });
    }

    // ---------- waiting is always bounded ----------

    @Test
    void invoice_exchangeRateTooSlow_givesUpOnConversionButReturnsInvoice() {
        slowCalls.block("exchange-rate");

        BookingInvoiceResponse result = documentService.invoice(1L, "EUR");

        assertThat(result.invoiceNumber()).isEqualTo("INV-000001");
        assertThat(result.convertedTotal()).isNull();
        assertThat(result.note()).contains("did not answer in time");
    }

    @Test
    void invoice_renderingTooSlow_callerStopsWaitingWithServiceBusy() {
        slowCalls.block("invoice");

        assertThatThrownBy(() -> documentService.invoice(1L, "USD"))
                .isInstanceOf(ServiceBusyException.class)
                .hasMessageContaining("taking too long")
                .extracting("retryAfterSeconds").isEqualTo(5L);
    }
}
