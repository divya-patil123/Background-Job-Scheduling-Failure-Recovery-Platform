package com.jobflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class Auth0AuthoritiesConverterTest {

    private final Auth0AuthoritiesConverter converter = new Auth0AuthoritiesConverter();

    @Test
    void mapsKnownAuth0PermissionsToApplicationRoles() {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("permissions", List.of("jobflow:read", "jobflow:operate", "jobflow:admin"))
                .build();

        assertThat(converter.convert(token))
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_VIEWER", "ROLE_OPERATOR", "ROLE_ADMIN");
    }

    @Test
    void ignoresUnknownPermissions() {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("permissions", List.of("jobflow:unknown"))
                .build();

        assertThat(converter.convert(token)).isEmpty();
    }
}
