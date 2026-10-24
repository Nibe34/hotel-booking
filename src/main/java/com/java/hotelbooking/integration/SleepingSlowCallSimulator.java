package com.java.hotelbooking.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class SleepingSlowCallSimulator implements SlowCallSimulator {

    @Override
    public void simulate(String operation, Duration typicalDuration) {
        log.debug("{}: waiting {} ms for the external system", operation, typicalDuration.toMillis());
        try {
            Thread.sleep(typicalDuration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operation + " was interrupted", e);
        }
    }
}
