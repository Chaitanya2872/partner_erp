package com.iotiq.partnererp.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Authentication settings bound from {@code app.security.*} (ADR-07). The JWT secret has no default:
 * it comes from {@code JWT_SECRET} (git-ignored server/.env locally, the environment in production).
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(@Valid @NotNull Jwt jwt,
                                 @Valid @NotNull RefreshToken refreshToken,
                                 @Valid @NotNull Login login,
                                 @Valid @NotNull Password password) {

    /** HS256 needs a key of at least 256 bits. */
    public static final int MIN_SECRET_BYTES = 32;

    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTokenTtl) {

        public byte[] secretBytes() {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("app.security.jwt.secret (JWT_SECRET) must be at least "
                        + MIN_SECRET_BYTES + " bytes long");
            }
            return bytes;
        }
    }

    /**
     * Rotating refresh token in an HttpOnly cookie. {@code cookieSecure} is false only in the dev profile
     * (plain-http localhost); production keeps the default true.
     */
    public record RefreshToken(@NotNull Duration ttl, @NotBlank String cookieName, @NotBlank String cookiePath,
                               boolean cookieSecure, @NotBlank String cookieSameSite) {
    }

    /** Temporary lockout after {@code maxFailedAttempts} consecutive wrong passwords. */
    public record Login(@Min(1) int maxFailedAttempts, @NotNull Duration lockoutDuration) {
    }

    public record Password(@Min(8) int minLength, @Min(16) int maxLength) {
    }
}
