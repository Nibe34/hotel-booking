package com.java.hotelbooking.aspect;

import com.java.hotelbooking.domain.annotation.AuditBooking;
import com.java.hotelbooking.domain.dto.BookingResponse;
import com.java.hotelbooking.domain.model.BookingAuditEntry;
import com.java.hotelbooking.repository.BookingAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Only successful calls are audited: if the method throws (validation, conflict, access denied),
 * nothing changed and nothing is written.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingAuditAspect {

    private final BookingAuditRepository auditRepository;
    private final Clock clock;

    @AfterReturning(pointcut = "@annotation(auditBooking)", returning = "result")
    public void audit(JoinPoint joinPoint, AuditBooking auditBooking, Object result) {
        if (!(result instanceof BookingResponse booking)) {
            log.warn("@AuditBooking on {} returned {} instead of BookingResponse, nothing audited",
                    joinPoint.getSignature().toShortString(), result == null ? "null" : result.getClass().getName());
            return;
        }

        BookingAuditEntry entry = new BookingAuditEntry();
        entry.setBookingId(booking.id());
        entry.setAction(auditBooking.value());
        entry.setStatus(booking.status());
        entry.setActor(CurrentActor.name());
        entry.setCreatedAt(clock.instant());
        auditRepository.save(entry);

        log.info("Audit: booking {} {} -> {} by {} ({})", booking.id(), entry.getAction(), entry.getStatus(),
                entry.getActor(), joinPoint.getSignature().toShortString());
    }
}
