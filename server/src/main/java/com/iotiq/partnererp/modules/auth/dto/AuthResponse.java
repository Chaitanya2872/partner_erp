package com.iotiq.partnererp.modules.auth.dto;

import java.time.Instant;

/**
 * Result of login, refresh and password change. The refresh token is only ever sent as an HttpOnly cookie.
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt, MeResponse user) {

    @Override
    public String toString() {
        return "AuthResponse[user=" + (user == null ? null : user.id()) + "]";
    }
}
