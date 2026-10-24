package com.java.hotelbooking.domain.annotation;

import com.java.hotelbooking.domain.enums.AuditAction;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Writes a row to the booking audit trail after the annotated method returns a
 * {@link com.java.hotelbooking.domain.dto.BookingResponse}: which action, resulting status, who did it.
 * Handled by {@link com.java.hotelbooking.aspect.BookingAuditAspect}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditBooking {

    AuditAction value();
}
