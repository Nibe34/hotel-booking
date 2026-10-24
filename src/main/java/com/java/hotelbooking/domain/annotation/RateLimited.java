package com.java.hotelbooking.domain.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows each user at most {@link #permits()} calls of the annotated method within a sliding window of
 * {@link #perSeconds()} seconds; further calls fail with 429 Too Many Requests.
 * Handled by {@link com.java.hotelbooking.aspect.RateLimitAspect}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

    int permits();

    long perSeconds() default 60;
}
