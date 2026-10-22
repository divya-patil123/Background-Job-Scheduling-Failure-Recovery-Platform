package com.jobflow.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public class Auth0AuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String PERMISSIONS_CLAIM = "permissions";
    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Collection<GrantedAuthority> scopes = scopeConverter.convert(jwt);
        if (scopes != null) {
            authorities.addAll(scopes);
        }

        Object permissionsClaim = jwt.getClaims().get(PERMISSIONS_CLAIM);
        if (permissionsClaim instanceof Collection<?> permissions) {
            for (Object permission : permissions) {
                if (permission instanceof String value) {
                    String role = switch (value) {
                        case "jobflow:read" -> "ROLE_VIEWER";
                        case "jobflow:operate" -> "ROLE_OPERATOR";
                        case "jobflow:admin" -> "ROLE_ADMIN";
                        default -> null;
                    };
                    if (role != null) {
                        authorities.add(new SimpleGrantedAuthority(role));
                    }
                }
            }
        }
        return List.copyOf(authorities);
    }
}
