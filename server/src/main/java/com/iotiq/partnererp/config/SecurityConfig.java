package com.iotiq.partnererp.config;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.security.JsonSecurityErrorHandler;
import com.iotiq.partnererp.security.PasswordChangeRequiredFilter;
import com.iotiq.partnererp.security.UserJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Stateless API secured by short-lived bearer JWTs (ADR-07). Deny by default: only health/info, the API
 * docs (dev only; disabled by configuration in prd) and login/refresh/logout are public. Endpoints declare
 * the permission they need with {@code @PreAuthorize("hasAuthority('<MODULE>:<LEVEL>')")}.
 *
 * <p>CSRF: there is no cookie-authenticated state-changing API except refresh/logout. Their cookie is
 * {@code SameSite=Strict}, scoped to {@code /api/v1/auth}, and both endpoints also require the
 * {@code X-Requested-With} header, which a cross-site form cannot send.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
            AppConstants.API_V1 + "/auth/login",
            AppConstants.API_V1 + "/auth/refresh",
            AppConstants.API_V1 + "/auth/logout"
    };

    /** BCrypt cost 12 (ADR-07). */
    public static final int BCRYPT_STRENGTH = 12;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errorHandler,
                                                   JwtDecoder jwtDecoder,
                                                   UserJwtAuthenticationConverter authenticationConverter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(jwtDecoder).jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .addFilterAfter(new PasswordChangeRequiredFilter(errorHandler), BearerTokenAuthenticationFilter.class)
                .headers(h -> h
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
