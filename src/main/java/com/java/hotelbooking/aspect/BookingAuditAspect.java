package com.java.hotelbooking.aspect;

import com.java.hotelbooking.domain.annotation.AuditBooking;
import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.model.BookingAuditEntry;
import com.java.hotelbooking.service.AuditTrail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Only successful calls are audited: if the method throws (validation, conflict, access denied),
 * nothing changed and nothing is written.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingAuditAspect {

    private final AuditTrail auditTrail;

    @AfterReturning(pointcut = "@annotation(auditBooking)", returning = "result")
    public void audit(JoinPoint joinPoint, AuditBooking auditBooking, Object result) {
        if (!(result instanceof BookingResponse booking)) {
            log.warn("@AuditBooking on {} returned {} instead of BookingResponse, nothing audited",
                    joinPoint.getSignature().toShortString(), result == null ? "null" : result.getClass().getName());
            return;
        }

        BookingAuditEntry entry = auditTrail.record(booking, auditBooking.value());

        log.info("Audit: booking {} {} -> {} by {} ({})", booking.id(), entry.getAction(), entry.getStatus(),
                entry.getActor(), joinPoint.getSignature().toShortString());
    }
}
