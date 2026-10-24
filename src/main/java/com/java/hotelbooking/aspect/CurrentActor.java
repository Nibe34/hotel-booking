package com.java.hotelbooking.aspect;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
 * Name of the user on whose behalf the current call runs; "system" for calls without a user
 * (scheduled jobs, async tasks).
 */
final class CurrentActor {

    static final String SYSTEM = "system";

    private CurrentActor() {
    }

    static String name() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !StringUtils.hasText(authentication.getName())) {
            return SYSTEM;
        }
        return authentication.getName();
    }
}
