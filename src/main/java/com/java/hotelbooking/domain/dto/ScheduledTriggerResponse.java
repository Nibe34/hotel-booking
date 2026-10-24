package com.java.hotelbooking.domain.dto;

import java.time.Instant;
import java.util.Map;

public record ScheduledTriggerResponse(
        String job,
        String trigger,
        String description,
        String state,
        Instant nextFireTime,
        Instant previousFireTime,
        Map<String, Object> triggerData
) {
}
