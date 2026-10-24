package com.java.hotelbooking.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the username of the authenticated caller (Keycloak {@code preferred_username}) into a
 * {@code String} controller parameter. Resolved by {@link com.java.hotelbooking.web.CurrentUsernameArgumentResolver}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUsername {
}
