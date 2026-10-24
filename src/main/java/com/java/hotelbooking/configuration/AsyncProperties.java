package com.java.hotelbooking.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("hotel.async")
public record AsyncProperties(
        @DefaultValue("hotel-async-") String threadNamePrefix,
        @DefaultValue("4") int corePoolSize,
        @DefaultValue("8") int maxPoolSize,
        @DefaultValue("50") int queueCapacity,
        @DefaultValue("3s") Duration documentsTimeout,
        @DefaultValue("1500ms") Duration exchangeRateTimeout,
        @DefaultValue("PT10S") Duration outboxRetryDelay
) {
}
