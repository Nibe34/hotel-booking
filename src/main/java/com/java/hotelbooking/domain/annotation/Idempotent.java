package com.java.hotelbooking.domain.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a (non-idempotent) endpoint safe to retry: if the client repeats a request with the same
 * idempotency key header, the stored result of the first call is returned and the method is not executed again.
 * Requests without the header are executed as usual. Handled by {@link com.java.hotelbooking.aspect.IdempotencyAspect}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    String header() default "Idempotency-Key";

    /** How long a stored result can be replayed. */
    long ttlSeconds() default 600;
}
