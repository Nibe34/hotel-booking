package com.java.hotelbooking.aspect;

import com.java.hotelbooking.domain.annotation.RateLimited;
import com.java.hotelbooking.exception.RateLimitExceededException;
import com.java.hotelbooking.service.AuditTrail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limiter: remembers the times of recent calls per user and method. Every attempt counts,
 * including ones that later fail in the method itself.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final Clock clock;
    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();

    @Before("@annotation(rateLimited)")
    public void checkLimit(JoinPoint joinPoint, RateLimited rateLimited) {
        String method = joinPoint.getSignature().toShortString();
        String actor = AuditTrail.currentActor();
        Instant now = clock.instant();
        Instant windowStart = now.minusSeconds(rateLimited.perSeconds());

        Deque<Instant> recent = calls.computeIfAbsent(actor + '|' + method, k -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(windowStart)) {
                recent.pollFirst();
            }
            if (recent.size() >= rateLimited.permits()) {
                long retryAfter = Math.max(1, Duration.between(windowStart, recent.peekFirst()).toSeconds());
                log.warn("Rate limit hit: {} called {} more than {} times in {} s", actor, method,
                        rateLimited.permits(), rateLimited.perSeconds());
                throw new RateLimitExceededException("Too many calls of " + method + ": at most "
                        + rateLimited.permits() + " per " + rateLimited.perSeconds() + " s, retry in "
                        + retryAfter + " s", retryAfter);
            }
            recent.addLast(now);
        }
    }
}
