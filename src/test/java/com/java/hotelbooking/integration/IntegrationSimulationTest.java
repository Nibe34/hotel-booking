package com.java.hotelbooking.integration;

import com.java.hotelbooking.configuration.SimulationProperties;
import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.enums.BookingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * The simulated external systems called directly (no proxy, so @Async does not apply and calls are synchronous).
 */
@ExtendWith(MockitoExtension.class)
class IntegrationSimulationTest {

    private static final SimulationProperties PROPERTIES = new SimulationProperties(
            Duration.ofMillis(10), Duration.ofMillis(20), Duration.ofMillis(30), List.of("ghost"));

    @Mock
    private SlowCallSimulator slowCall;

    private BookingResponse booking() {
        return new BookingResponse(42L, 1L, "101", 1L, "Grand", "bob", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 3), 2, new BigDecimal("4200.00"), BookingStatus.CONFIRMED, null);
    }

    @Test
    void mailGateway_reachableRecipient_takesConfiguredTimeAndSucceeds() {
        SimulatedMailGateway gateway = new SimulatedMailGateway(slowCall, PROPERTIES);

        assertThatCode(() -> gateway.send(new Email("bob", "s", "b"))).doesNotThrowAnyException();
        verify(slowCall).simulate("mail", Duration.ofMillis(10));
    }

    @Test
    void mailGateway_unreachableRecipient_throws() {
        SimulatedMailGateway gateway = new SimulatedMailGateway(slowCall, PROPERTIES);

        assertThatThrownBy(() -> gateway.send(new Email("ghost", "s", "b")))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessage("Mailbox of ghost is unreachable");
    }

    @Test
    void invoiceRenderer_rendersNumberedDocumentWithBookingDetails() {
        InvoiceRenderer renderer = new InvoiceRenderer(slowCall, PROPERTIES);

        Invoice invoice = renderer.render(booking()).join();

        assertThat(invoice.number()).isEqualTo("INV-000042");
        assertThat(new String(invoice.content(), StandardCharsets.UTF_8))
                .contains("INVOICE INV-000042", "Grand, room 101", "Guest: bob", "Total: 4200.00 UAH");
        verify(slowCall).simulate("invoice", Duration.ofMillis(20));
    }

    @ParameterizedTest
    @CsvSource({
            "UAH, 4200.00",
            "usd, 101.22",
            "EUR, 93.24",
            "PLN, 404.04"
    })
    void exchangeRateClient_knownCurrency_convertsAndRounds(String currency, String expected) {
        ExchangeRateClient client = new ExchangeRateClient(slowCall, PROPERTIES);

        BigDecimal converted = client.convertFromUah(new BigDecimal("4200.00"), currency).join();

        assertThat(converted).isEqualByComparingTo(expected);
        assertThat(converted.scale()).isEqualTo(2);
    }

    @Test
    void exchangeRateClient_unknownCurrency_fails() {
        ExchangeRateClient client = new ExchangeRateClient(slowCall, PROPERTIES);

        assertThatThrownBy(() -> client.convertFromUah(BigDecimal.TEN, "XYZ"))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessage("No exchange rate from UAH to XYZ");
    }

    @Test
    void sleepingSimulator_interrupted_restoresFlagAndFails() {
        SleepingSlowCallSimulator simulator = new SleepingSlowCallSimulator();
        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> simulator.simulate("mail", Duration.ofSeconds(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("mail was interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
