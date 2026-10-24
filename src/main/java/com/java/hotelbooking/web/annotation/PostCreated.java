package com.java.hotelbooking.web.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composed annotation for "create" endpoints: the same as {@code @PostMapping(...)} +
 * {@code @ResponseStatus(HttpStatus.CREATED)} written together.
 * <p>
 * {@code @PostMapping} itself is limited to methods and cannot be used as a meta-annotation, so this one
 * is built the same way Spring builds {@code @PostMapping}: {@code @RequestMapping(method = POST)}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequestMapping(method = RequestMethod.POST)
@ResponseStatus(HttpStatus.CREATED)
public @interface PostCreated {

    /** Path of the endpoint, passed through to {@link RequestMapping#value()}. */
    @AliasFor(annotation = RequestMapping.class)
    String[] value() default {};
}
