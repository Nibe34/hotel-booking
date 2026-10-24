package com.java.hotelbooking.web;

import com.java.hotelbooking.web.annotation.CurrentUsername;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUsernameArgumentResolverTest {

    private final CurrentUsernameArgumentResolver resolver = new CurrentUsernameArgumentResolver();

    @SuppressWarnings("unused")
    private static class SampleController {
        void handle(@CurrentUsername String username, String plain, @CurrentUsername Long wrongType) {
        }
    }

    private MethodParameter parameter(int index) throws NoSuchMethodException {
        Method method = SampleController.class.getDeclaredMethod("handle", String.class, String.class, Long.class);
        return new MethodParameter(method, index);
    }

    @Test
    void supportsParameter_annotatedString_returnsTrue() throws Exception {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
    }

    @Test
    void supportsParameter_withoutAnnotation_returnsFalse() throws Exception {
        assertThat(resolver.supportsParameter(parameter(1))).isFalse();
    }

    @Test
    void supportsParameter_annotatedNonString_returnsFalse() throws Exception {
        assertThat(resolver.supportsParameter(parameter(2))).isFalse();
    }

    @Test
    void resolveArgument_authenticatedRequest_returnsPrincipalName() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(new UsernamePasswordAuthenticationToken("bob", "n/a"));

        Object value = resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null);

        assertThat(value).isInstanceOf(String.class).isEqualTo("bob");
    }

    @Test
    void resolveArgument_noPrincipal_throwsAuthenticationException() throws Exception {
        ServletWebRequest anonymous = new ServletWebRequest(new MockHttpServletRequest());
        MethodParameter parameter = parameter(0);

        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, anonymous, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("No authenticated user");
    }
}
