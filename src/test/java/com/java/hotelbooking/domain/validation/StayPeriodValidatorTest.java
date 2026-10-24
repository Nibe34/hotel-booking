package com.java.hotelbooking.domain.validation;

import com.java.hotelbooking.domain.annotation.ValidStayPeriod;
import com.java.hotelbooking.domain.dto.BookingCreateRequest;
import com.java.hotelbooking.domain.dto.StayPeriod;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses the real Bean Validation engine: StayPeriodValidator is found through
 * {@code @Constraint(validatedBy = ...)} on {@link ValidStayPeriod}, nobody creates it by hand.
 */
class StayPeriodValidatorTest {

    // far enough in the future for @FutureOrPresent on checkIn
    private static final LocalDate CHECK_IN = LocalDate.of(2099, 7, 1);

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<ConstraintViolation<BookingCreateRequest>> validate(LocalDate checkIn, LocalDate checkOut) {
        return validator.validate(new BookingCreateRequest(1L, checkIn, checkOut, 2));
    }

    @Test
    void bookingCreateRequest_isAnnotatedWithValidStayPeriod() {
        ValidStayPeriod annotation = BookingCreateRequest.class.getAnnotation(ValidStayPeriod.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.maxNights()).isEqualTo(30);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 14, 30})
    void validStay_producesNoViolations(int nights) {
        assertThat(validate(CHECK_IN, CHECK_IN.plusDays(nights))).isEmpty();
    }

    @Test
    void checkOutBeforeCheckIn_violationNamesBothDatesOnCheckOutField() {
        Set<ConstraintViolation<BookingCreateRequest>> violations = validate(CHECK_IN, CHECK_IN.minusDays(2));

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath()).hasToString("checkOut");
            assertThat(v.getMessage()).isEqualTo("checkOut (2099-06-29) must be after checkIn (2099-07-01)");
        });
    }

    @Test
    void checkOutSameDayAsCheckIn_isRejected() {
        Set<ConstraintViolation<BookingCreateRequest>> violations = validate(CHECK_IN, CHECK_IN);

        assertThat(violations).singleElement()
                .extracting(ConstraintViolation::getMessage)
                .isEqualTo("checkOut (2099-07-01) must be after checkIn (2099-07-01)");
    }

    @Test
    void stayLongerThanMax_violationNamesActualNightsAndLimit() {
        Set<ConstraintViolation<BookingCreateRequest>> violations = validate(CHECK_IN, CHECK_IN.plusDays(31));

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath()).hasToString("checkOut");
            assertThat(v.getMessage())
                    .isEqualTo("stay of 31 nights (2099-07-01 - 2099-08-01) exceeds the maximum of 30 nights");
            assertThat(v.getConstraintDescriptor().getAnnotation()).isInstanceOf(ValidStayPeriod.class);
        });
    }

    @Test
    void missingDates_areLeftToNotNull() {
        Set<ConstraintViolation<BookingCreateRequest>> violations = validate(CHECK_IN, null);

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath()).hasToString("checkOut");
            assertThat(v.getConstraintDescriptor().getAnnotation()).isInstanceOf(NotNull.class);
        });
    }

    @Test
    void customMaxNights_isTakenFromAnnotationAttribute() {
        Set<ConstraintViolation<ShortStay>> ok = validator.validate(new ShortStay(CHECK_IN, CHECK_IN.plusDays(3)));
        Set<ConstraintViolation<ShortStay>> tooLong =
                validator.validate(new ShortStay(CHECK_IN, CHECK_IN.plusDays(4)));

        assertThat(ok).isEmpty();
        assertThat(tooLong).singleElement()
                .extracting(ConstraintViolation::getMessage)
                .isEqualTo("stay of 4 nights (2099-07-01 - 2099-07-05) exceeds the maximum of 3 nights");
    }

    @ValidStayPeriod(maxNights = 3)
    private record ShortStay(LocalDate checkIn, LocalDate checkOut)
            implements StayPeriod {
    }
}
