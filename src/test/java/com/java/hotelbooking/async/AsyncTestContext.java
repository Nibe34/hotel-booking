package com.java.hotelbooking.async;

import com.java.hotelbooking.configuration.AsyncConfig;
import com.java.hotelbooking.integration.ExchangeRateClient;
import com.java.hotelbooking.integration.InvoiceRenderer;
import com.java.hotelbooking.integration.SimulatedMailGateway;
import com.java.hotelbooking.service.BookingDocumentService;
import com.java.hotelbooking.service.notification.BookingNotificationListener;
import com.java.hotelbooking.service.notification.GuestNotifier;
import com.java.hotelbooking.service.notification.PendingEmailOutbox;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Only the async part of the application: the real executor configuration and the beans that use it.
 * No database, no web layer, no security; BookingService is mocked in the tests.
 */
@Configuration
@Import({AsyncConfig.class, InvoiceRenderer.class, ExchangeRateClient.class, SimulatedMailGateway.class,
        GuestNotifier.class, PendingEmailOutbox.class, BookingNotificationListener.class,
        BookingDocumentService.class})
class AsyncTestContext {

    @Bean
    ControllableSlowCalls slowCalls() {
        return new ControllableSlowCalls();
    }

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
