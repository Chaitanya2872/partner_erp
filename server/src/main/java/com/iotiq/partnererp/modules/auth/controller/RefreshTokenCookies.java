package com.iotiq.partnererp.modules.auth.controller;

import com.iotiq.partnererp.config.SecurityProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The refresh-token cookie: HttpOnly, SameSite=Strict, scoped to the auth endpoints, Secure outside dev.
 */
@Component
public class RefreshTokenCookies {

    private final SecurityProperties.RefreshToken settings;

    public RefreshTokenCookies(SecurityProperties properties) {
        this.settings = properties.refreshToken();
    }

    public String name() {
        return settings.cookieName();
    }

    public ResponseCookie issue(String rawToken) {
        return base(rawToken).maxAge(settings.ttl()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(settings.cookieName(), value)
                .httpOnly(true)
                .secure(settings.cookieSecure())
                .sameSite(settings.cookieSameSite())
                .path(settings.cookiePath());
    }
}
