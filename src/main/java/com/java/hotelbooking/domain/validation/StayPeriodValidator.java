package com.java.hotelbooking.domain.validation;

import com.java.hotelbooking.domain.annotation.ValidStayPeriod;
import com.java.hotelbooking.domain.dto.StayPeriod;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import java.time.temporal.ChronoUnit;

public class StayPeriodValidator implements ConstraintValidator<ValidStayPeriod, StayPeriod> {

    private int maxNights;

    @Override
    public void initialize(ValidStayPeriod annotation) {
        this.maxNights = annotation.maxNights();
    }

    @Override
    public boolean isValid(StayPeriod stay, ConstraintValidatorContext context) {
        // missing dates are reported by @NotNull on the fields
        if (stay == null || stay.checkIn() == null || stay.checkOut() == null) {
            return true;
        }

        long nights = ChronoUnit.DAYS.between(stay.checkIn(), stay.checkOut());
        if (nights <= 0) {
            return violation(context, stay, nights, "checkOut ({checkOut}) must be after checkIn ({checkIn})");
        }
        if (nights > maxNights) {
            return violation(context, stay, nights,
                    "stay of {nights} nights ({checkIn} - {checkOut}) exceeds the maximum of {maxNights} nights");
        }
        return true;
    }

    // Values go in as message parameters, not concatenated into the template, so they are never
    // interpreted as message expressions.
    private boolean violation(ConstraintValidatorContext context, StayPeriod stay, long nights, String template) {
        HibernateConstraintValidatorContext hibernateContext = context.unwrap(HibernateConstraintValidatorContext.class);
        hibernateContext.disableDefaultConstraintViolation();
        hibernateContext
                .addMessageParameter("checkIn", stay.checkIn())
                .addMessageParameter("checkOut", stay.checkOut())
                .addMessageParameter("nights", nights)
                .buildConstraintViolationWithTemplate(template)
                .addPropertyNode("checkOut")
                .addConstraintViolation();
        return false;
    }
}
