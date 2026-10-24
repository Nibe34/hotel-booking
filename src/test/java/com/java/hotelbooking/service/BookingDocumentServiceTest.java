package com.java.hotelbooking.service;

import com.java.hotelbooking.configuration.AsyncProperties;
import com.java.hotelbooking.domain.dto.BookingInvoiceResponse;
import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.BookingStatus;
import com.java.hotelbooking.exception.NotFoundException;
import com.java.hotelbooking.exception.ServiceBusyException;
import com.java.hotelbooking.integration.ExchangeRateClient;
import com.java.hotelbooking.integration.ExchangeRateUnavailableException;
import com.java.hotelbooking.integration.Invoice;
import com.java.hotelbooking.integration.InvoiceRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Combination and failure logic with mocked futures; the real threads are covered by async/AsyncExecutionTest.
 */
@ExtendWith(MockitoExtension.class)
class BookingDocumentServiceTest {

    private static final AsyncProperties PROPERTIES = new AsyncProperties("t-", 1, 1, 1,
            Duration.ofMillis(200), Duration.ofMillis(100), Duration.ofSeconds(10));
    private static final BookingResponse BOOKING = new BookingResponse(5L, 1L, "101", 1L, "Grand", "bob",
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), 2, new BigDecimal("1000.00"),
            BookingStatus.CONFIRMED, null);
    private static final Invoice INVOICE = new Invoice("INV-000005", new byte[]{1, 2, 3});

    @Mock
    private BookingService bookingService;

    @Mock
    private InvoiceRenderer invoiceRenderer;

    @Mock
    private ExchangeRateClient exchangeRateClient;

    private BookingDocumentService service;

    @BeforeEach
    void setUp() {
        service = new BookingDocumentService(bookingService, invoiceRenderer, exchangeRateClient, PROPERTIES);
        lenient().when(bookingService.getById(5L)).thenReturn(BOOKING);
    }

    @Test
    void invoice_bothCallsSucceed_combinesInvoiceAndConvertedPrice() {
        when(invoiceRenderer.render(BOOKING)).thenReturn(CompletableFuture.completedFuture(INVOICE));
        when(exchangeRateClient.convertFromUah(new BigDecimal("1000.00"), "USD"))
                .thenReturn(CompletableFuture.completedFuture(new BigDecimal("24.10")));

        BookingInvoiceResponse result = service.invoice(5L, "USD");

        assertThat(result).isEqualTo(new BookingInvoiceResponse(5L, "INV-000005", 3, new BigDecimal("1000.00"),
                "USD", new BigDecimal("24.10"), null));
    }

    @Test
    void invoice_conversionFails_exceptionallyFallsBackToNote() {
        when(invoiceRenderer.render(BOOKING)).thenReturn(CompletableFuture.completedFuture(INVOICE));
        when(exchangeRateClient.convertFromUah(any(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new ExchangeRateUnavailableException("bank is down")));

        BookingInvoiceResponse result = service.invoice(5L, "EUR");

        assertThat(result.invoiceNumber()).isEqualTo("INV-000005");
        assertThat(result.convertedTotal()).isNull();
        assertThat(result.note()).isEqualTo("Price in EUR is not available: bank is down");
    }

    @Test
    void invoice_conversionNeverAnswers_timesOutAndFallsBack() {
        when(invoiceRenderer.render(BOOKING)).thenReturn(CompletableFuture.completedFuture(INVOICE));
        when(exchangeRateClient.convertFromUah(any(), anyString())).thenReturn(new CompletableFuture<>());

        BookingInvoiceResponse result = service.invoice(5L, "EUR");

        assertThat(result.convertedTotal()).isNull();
        assertThat(result.note()).isEqualTo("Price in EUR is not available: the exchange rate service did not answer in time");
    }

    @Test
    void invoice_renderingNeverFinishes_boundedWaitEndsWithServiceBusy() {
        when(invoiceRenderer.render(BOOKING)).thenReturn(new CompletableFuture<>());
        when(exchangeRateClient.convertFromUah(any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(BigDecimal.ONE));

        assertThatThrownBy(() -> service.invoice(5L, "USD"))
                .isInstanceOf(ServiceBusyException.class)
                .hasMessage("Invoice for booking 5 is taking too long, try again in 5 seconds");
    }

    @Test
    void invoice_executorRejectsTask_endsWithServiceBusyNot500() {
        when(invoiceRenderer.render(BOOKING)).thenThrow(new TaskRejectedException("executor full"));

        assertThatThrownBy(() -> service.invoice(5L, "USD"))
                .isInstanceOf(ServiceBusyException.class)
                .hasMessageContaining("Too many documents")
                .extracting("retryAfterSeconds").isEqualTo(5L);
        verify(exchangeRateClient, never()).convertFromUah(any(), anyString());
    }

    @Test
    void invoice_renderingFails_propagatesOriginalException() {
        when(invoiceRenderer.render(BOOKING))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("template missing")));
        when(exchangeRateClient.convertFromUah(any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(BigDecimal.ONE));

        assertThatThrownBy(() -> service.invoice(5L, "USD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("template missing");
    }

    @Test
    void invoice_unknownBooking_failsBeforeStartingAsyncWork() {
        when(bookingService.getById(99L)).thenThrow(new NotFoundException("Booking", 99L));

        assertThatThrownBy(() -> service.invoice(99L, "USD")).isInstanceOf(NotFoundException.class);
        verify(invoiceRenderer, never()).render(any());
    }
}
