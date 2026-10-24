package com.java.hotelbooking.service;

import com.java.hotelbooking.configuration.AsyncProperties;
import com.java.hotelbooking.domain.dto.BookingInvoiceResponse;
import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.exception.ServiceBusyException;
import com.java.hotelbooking.integration.ExchangeRateClient;
import com.java.hotelbooking.integration.Invoice;
import com.java.hotelbooking.integration.InvoiceRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingDocumentService {

    static final long RETRY_AFTER_SECONDS = 5;

    private final BookingService bookingService;
    private final InvoiceRenderer invoiceRenderer;
    private final ExchangeRateClient exchangeRateClient;
    private final AsyncProperties properties;

    private record ConvertedPrice(BigDecimal amount, String note) {
    }

    /**
     * Starts the invoice rendering and the currency conversion at the same time and combines both results.
     * The conversion is optional: if it fails or is too slow the invoice is returned without it. The invoice is
     * required: waiting for it is limited by {@code hotel.async.documents-timeout}, after that the client gets 503.
     */
    public BookingInvoiceResponse invoice(Long bookingId, String currency) {
        BookingResponse booking = bookingService.getById(bookingId); // also checks owner / ADMIN

        CompletableFuture<Invoice> invoiceFuture;
        CompletableFuture<ConvertedPrice> priceFuture;
        try {
            invoiceFuture = invoiceRenderer.render(booking);
            priceFuture = exchangeRateClient.convertFromUah(booking.totalPrice(), currency)
                    .orTimeout(properties.exchangeRateTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .thenApply(amount -> new ConvertedPrice(amount, null))
                    .exceptionally(ex -> {
                        String reason = describe(ex);
                        log.warn("Price of booking {} not converted to {}: {}", bookingId, currency, reason);
                        return new ConvertedPrice(null, "Price in " + currency + " is not available: " + reason);
                    });
        } catch (TaskRejectedException e) {
            log.warn("Async executor is full, invoice for booking {} rejected", bookingId);
            throw new ServiceBusyException("Too many documents are being prepared right now, try again in "
                    + RETRY_AFTER_SECONDS + " seconds", RETRY_AFTER_SECONDS);
        }

        CompletableFuture<BookingInvoiceResponse> combined = invoiceFuture.thenCombine(priceFuture,
                (invoice, price) -> new BookingInvoiceResponse(booking.id(), invoice.number(), invoice.content().length,
                        booking.totalPrice(), currency, price.amount(), price.note()));
        try {
            return combined.get(properties.documentsTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            combined.cancel(true);
            throw new ServiceBusyException("Invoice for booking " + bookingId + " is taking too long, try again in "
                    + RETRY_AFTER_SECONDS + " seconds", RETRY_AFTER_SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceBusyException("Interrupted while preparing the invoice", RETRY_AFTER_SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException("Invoice rendering failed", e.getCause());
        }
    }

    private static String describe(Throwable ex) {
        Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
        return cause instanceof TimeoutException
                ? "the exchange rate service did not answer in time"
                : cause.getMessage();
    }
}
