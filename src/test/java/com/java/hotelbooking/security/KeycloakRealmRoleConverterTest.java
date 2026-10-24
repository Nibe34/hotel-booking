package com.java.hotelbooking.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-id")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(60));
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void convert_realmRoles_areMappedToRolePrefixedAuthorities() {
        Jwt token = jwt(Map.of("realm_access", Map.of("roles", List.of("ADMIN", "USER"))));

        Collection<GrantedAuthority> authorities = converter.convert(token);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void convert_noRealmAccessClaim_returnsNoAuthorities() {
        Jwt token = jwt(Map.of("preferred_username", "bob"));

        assertThat(converter.convert(token)).isEmpty();
    }

    @Test
    void convert_realmAccessWithoutRoles_returnsNoAuthorities() {
        Jwt token = jwt(Map.of("realm_access", Map.of("other", "value")));

        assertThat(converter.convert(token)).isEmpty();
    }

    @Test
    void convert_rolesIsNotACollection_returnsNoAuthorities() {
        Jwt token = jwt(Map.of("realm_access", Map.of("roles", "ADMIN")));

        assertThat(converter.convert(token)).isEmpty();
    }

    @Test
    void convert_emptyRoles_returnsNoAuthorities() {
        Jwt token = jwt(Map.of("realm_access", Map.of("roles", List.of())));

        assertThat(converter.convert(token)).isEmpty();
    }
}
