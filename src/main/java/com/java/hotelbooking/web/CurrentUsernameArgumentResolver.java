package com.java.hotelbooking.web;

import com.java.hotelbooking.web.annotation.CurrentUsername;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.security.Principal;

/**
 * Takes the username from the request principal. Spring Security exposes the current Authentication
 * as the principal, and for JWTs its name is preferred_username (see SecurityConfig).
 */
public class CurrentUsernameArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUsername.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public String resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Principal principal = webRequest.getUserPrincipal();
        if (principal == null || !StringUtils.hasText(principal.getName())) {
            throw new AuthenticationCredentialsNotFoundException(
                    "No authenticated user for @CurrentUsername parameter of " + parameter.getMethod());
        }
        return principal.getName();
    }
}
