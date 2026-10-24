package com.java.hotelbooking.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * A failure in an @Async void method has no caller to report to. Instead of letting it disappear it is
 * logged with the method and arguments and counted in the {@code hotel.async.failures} metric.
 */
@Slf4j
public class LoggingAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {

    static final String METRIC = "hotel.async.failures";

    private final MeterRegistry meterRegistry;

    public LoggingAsyncUncaughtExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        String name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        log.error("@Async method {} failed, arguments {}", name, Arrays.toString(params), ex);
        meterRegistry.counter(METRIC, "method", name).increment();
    }
}
