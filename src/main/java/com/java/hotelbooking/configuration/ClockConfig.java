package com.java.hotelbooking.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Business rules that depend on "today" take the date from this clock,
 * so tests can pin it with Clock.fixed(...) instead of relying on the real time.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
