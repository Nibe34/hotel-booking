package com.java.hotelbooking.configuration;

import com.java.hotelbooking.integration.Email;
import com.java.hotelbooking.integration.MailDeliveryException;
import com.java.hotelbooking.service.notification.GuestNotifier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAsyncUncaughtExceptionHandlerTest {

    @Test
    void handleUncaughtException_countsFailurePerMethod() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LoggingAsyncUncaughtExceptionHandler handler = new LoggingAsyncUncaughtExceptionHandler(registry);
        Method send = GuestNotifier.class.getMethod("send", Email.class);

        handler.handleUncaughtException(new MailDeliveryException("unreachable"), send, new Email("ghost", "s", "b"));
        handler.handleUncaughtException(new MailDeliveryException("unreachable"), send, new Email("ghost", "s", "b"));

        assertThat(registry.get(LoggingAsyncUncaughtExceptionHandler.METRIC)
                .tag("method", "GuestNotifier.send").counter().count()).isEqualTo(2.0);
    }
}
