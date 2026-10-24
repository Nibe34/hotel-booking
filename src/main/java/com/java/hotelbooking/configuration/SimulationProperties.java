package com.java.hotelbooking.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Typical response times of the simulated external systems (mail server, document renderer, bank API).
 */
@ConfigurationProperties("hotel.simulation")
public record SimulationProperties(
        @DefaultValue("1s") Duration mailDelay,
        @DefaultValue("1s") Duration invoiceDelay,
        @DefaultValue("700ms") Duration exchangeRateDelay,
        @DefaultValue List<String> unreachableRecipients
) {
}
