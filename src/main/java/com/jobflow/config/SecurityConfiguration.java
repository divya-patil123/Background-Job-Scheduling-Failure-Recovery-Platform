package com.jobflow.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration implements EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<JwtDecoder> decoderProvider,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            @Value("${jobflow.auth0.enabled:false}") boolean auth0Enabled,
            @Value("${jobflow.auth0.issuer-uri:}") String issuer,
            @Value("${jobflow.auth0.audience:}") String audience) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(
                            "/", "/index.html", "/assets/**", "/favicon.ico",
                            "/actuator/health", "/api/auth/config",
                            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dev-h2"))) {
                        authorize.requestMatchers("/api/**").permitAll();
                    } else if (auth0Enabled) {
                        authorize.requestMatchers(HttpMethod.GET, "/api/**")
                                .hasAnyRole("VIEWER", "OPERATOR", "ADMIN");
                        authorize.requestMatchers(HttpMethod.DELETE, "/api/jobs/**").hasRole("ADMIN");
                        authorize.requestMatchers(HttpMethod.POST, "/api/jobs/**")
                                .hasAnyRole("OPERATOR", "ADMIN");
                        authorize.requestMatchers(HttpMethod.PUT, "/api/jobs/**")
                                .hasAnyRole("OPERATOR", "ADMIN");
                        authorize.requestMatchers(HttpMethod.PATCH, "/api/**")
                                .hasAnyRole("OPERATOR", "ADMIN");
                        authorize.requestMatchers("/api/**").denyAll();
                    } else {
                        authorize.requestMatchers("/api/**").denyAll();
                    }
                    authorize.anyRequest().permitAll();
                });

        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dev-h2"))
                && auth0Enabled) {
            JwtDecoder decoder = decoderProvider.getIfAvailable();
            if (decoder == null) {
                throw new IllegalStateException("Auth0 JWT decoder is not configured");
            }
            http.oauth2ResourceServer(resourceServer -> resourceServer
                    .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(jwtAuthenticationConverter)));
        }
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new Auth0AuthoritiesConverter());
        return converter;
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "jobflow.auth0", name = "enabled", havingValue = "true")
    @org.springframework.context.annotation.Profile("!dev-h2")
    public JwtDecoder jwtDecoder(
            @Value("${jobflow.auth0.issuer-uri}") String issuer,
            @Value("${jobflow.auth0.audience}") String audience) {
        if (!StringUtils.hasText(issuer) || !StringUtils.hasText(audience)) {
            throw new IllegalStateException(
                    "AUTH0_ISSUER_URI and AUTH0_AUDIENCE are required when AUTH0_ENABLED=true");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "Required API audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }
}
