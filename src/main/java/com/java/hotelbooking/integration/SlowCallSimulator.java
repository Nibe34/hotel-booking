package com.java.hotelbooking.integration;

import java.time.Duration;

/**
 * Stands in for the time an external system takes to answer. Tests replace it to control timing
 * (block, release, meet at a barrier) instead of sleeping.
 */
public interface SlowCallSimulator {

    void simulate(String operation, Duration typicalDuration);
}
