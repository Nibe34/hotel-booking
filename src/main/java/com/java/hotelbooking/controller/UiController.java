package com.java.hotelbooking.controller;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Bridge between the OIDC session and the JWT-protected API: the UI asks for the
 * access token of the logged-in user and sends it as a Bearer token to /api/**.
 * The token is refreshed automatically by the OAuth2AuthorizedClientManager.
 */
@RestController
public class UiController {

    @GetMapping("/ui/token")
    public Map<String, Object> token(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client,
                                     @AuthenticationPrincipal OidcUser user) {
        return Map.of(
                "username", user.getPreferredUsername(),
                "accessToken", client.getAccessToken().getTokenValue(),
                "expiresAt", client.getAccessToken().getExpiresAt()
        );
    }
}
