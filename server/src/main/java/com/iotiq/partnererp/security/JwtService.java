package com.iotiq.partnererp.security;

import com.iotiq.partnererp.config.SecurityProperties;
import com.iotiq.partnererp.modules.auth.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues short-lived access tokens. Claims: {@code sub} = user id, {@code sv} = session version (bumped to
 * revoke all tokens), {@code role} = role code for information only; permissions are never put in the
 * token so changes apply immediately.
 */
@Service
public class JwtService {

    public static final String SESSION_VERSION_CLAIM = "sv";

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issue(User user) {
        return issue(user, Instant.now(clock));
    }

    /** Issues a token as of {@code issuedAt} (tests use a past instant to obtain an expired token). */
    public AccessToken issue(User user, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(properties.jwt().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(SESSION_VERSION_CLAIM, user.getSessionVersion())
                .claim("role", user.getRole().getCode())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, expiresAt, properties.jwt().accessTokenTtl().toSeconds());
    }

    public record AccessToken(String value, Instant expiresAt, long expiresInSeconds) {
    }
}
