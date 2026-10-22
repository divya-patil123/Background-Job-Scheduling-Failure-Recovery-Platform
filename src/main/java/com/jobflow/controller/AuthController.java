package com.jobflow.controller;

import com.jobflow.dto.AuthConfigResponse;
import com.jobflow.dto.CurrentUserResponse;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final String issuerUri;
    private final String audience;
    private final String clientId;
    private final boolean auth0Enabled;
    private final Environment environment;

    public AuthController(@Value("${jobflow.auth0.issuer-uri:}") String issuerUri,
                          @Value("${jobflow.auth0.audience:}") String audience,
                          @Value("${jobflow.auth0.client-id:}") String clientId,
                          @Value("${jobflow.auth0.enabled:false}") boolean auth0Enabled,
                          Environment environment) {
        this.issuerUri = issuerUri;
        this.audience = audience;
        this.clientId = clientId;
        this.auth0Enabled = auth0Enabled;
        this.environment = environment;
    }

    @GetMapping("/config")
    public AuthConfigResponse config() {
        boolean enabled = auth0Enabled && StringUtils.hasText(issuerUri)
                && StringUtils.hasText(audience)
                && StringUtils.hasText(clientId);
        String domain = null;
        if (enabled) {
            domain = URI.create(issuerUri).getHost();
        }
        boolean developmentMode = environment.acceptsProfiles(Profiles.of("dev-h2"));
        return new AuthConfigResponse(enabled, developmentMode, domain, enabled ? clientId : null,
                enabled ? audience : null);
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return new CurrentUserResponse(authentication.getName(), authentication.getName(),
                    null, Set.of());
        }
        var jwt = jwtAuthentication.getToken();
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toUnmodifiableSet());
        String displayName = jwt.getClaimAsString("name");
        if (!StringUtils.hasText(displayName)) {
            displayName = jwt.getSubject();
        }
        return new CurrentUserResponse(jwt.getSubject(), displayName, jwt.getClaimAsString("email"), roles);
    }
}
