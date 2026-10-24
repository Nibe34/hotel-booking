package com.java.hotelbooking.aspect;

import com.java.hotelbooking.domain.annotation.Idempotent;
import com.java.hotelbooking.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Results are kept in memory, so replay works within one application instance; several instances would
 * need a shared store (DB / Redis). Keys are scoped by user and method, so one user can never receive
 * another user's stored response by guessing the key.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyAspect {

    static final int MAX_KEY_LENGTH = 128;

    private final Clock clock;
    private final Map<String, StoredResult> results = new ConcurrentHashMap<>();

    private record StoredResult(Object value, Instant expiresAt) {
    }

    @Around("@annotation(idempotent)")
    public Object replayOrProceed(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String key = currentRequestHeader(idempotent.header());
        if (!StringUtils.hasText(key)) {
            return joinPoint.proceed();
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException(idempotent.header() + " must be at most " + MAX_KEY_LENGTH + " characters");
        }

        String cacheKey = CurrentActor.name() + '|' + joinPoint.getSignature().toLongString() + '|' + key;
        Instant now = clock.instant();
        StoredResult stored = results.get(cacheKey);
        if (stored != null && stored.expiresAt().isAfter(now)) {
            log.info("Idempotent replay of {} for key '{}'", joinPoint.getSignature().toShortString(), key);
            return stored.value();
        }

        // a call that throws is not stored, so the client may retry it with the same key
        Object result = joinPoint.proceed();
        results.values().removeIf(r -> !r.expiresAt().isAfter(now));
        results.put(cacheKey, new StoredResult(result, now.plusSeconds(idempotent.ttlSeconds())));
        return result;
    }

    private String currentRequestHeader(String name) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(name);
        }
        return null;
    }
}
