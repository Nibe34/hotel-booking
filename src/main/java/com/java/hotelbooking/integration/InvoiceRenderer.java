package com.java.hotelbooking.integration;

import com.java.hotelbooking.configuration.SimulationProperties;
import com.java.hotelbooking.domain.dto.BookingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Renders the invoice document of a booking. The rendering engine is simulated: it takes about a second
 * and produces a plain-text document instead of a real PDF.
 */
@Component
@RequiredArgsConstructor
public class InvoiceRenderer {

    private final SlowCallSimulator slowCall;
    private final SimulationProperties properties;

    @Async
    public CompletableFuture<Invoice> render(BookingResponse booking) {
        slowCall.simulate("invoice", properties.invoiceDelay());
        String number = "INV-%06d".formatted(booking.id());
        String content = """
                INVOICE %s
                Hotel: %s, room %s
                Guest: %s
                Stay: %s - %s, guests: %d
                Total: %s UAH
                """.formatted(number, booking.hotelName(), booking.roomNumber(), booking.guestUsername(),
                booking.checkIn(), booking.checkOut(), booking.guestsCount(), booking.totalPrice());
        return CompletableFuture.completedFuture(new Invoice(number, content.getBytes(StandardCharsets.UTF_8)));
    }
}
