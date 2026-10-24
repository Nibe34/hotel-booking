package com.java.hotelbooking.domain.annotation;

import com.java.hotelbooking.domain.validation.StayPeriodValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level rule for a {@link com.java.hotelbooking.domain.dto.StayPeriod}: check-out must be after check-in
 * and the stay must not be longer than {@link #maxNights()} nights. The violation is reported on the
 * {@code checkOut} property with a message that names the actual dates / number of nights.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StayPeriodValidator.class)
@Documented
public @interface ValidStayPeriod {

    String message() default "checkOut must be after checkIn and the stay must not exceed {maxNights} nights";

    int maxNights() default 30;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
