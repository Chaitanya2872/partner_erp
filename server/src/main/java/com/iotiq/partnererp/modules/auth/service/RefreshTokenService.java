package com.iotiq.partnererp.modules.auth.service;

import com.iotiq.partnererp.config.SecurityProperties;
import com.iotiq.partnererp.modules.auth.entity.RefreshToken;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens: 256 random bits, sent to the client once, stored only as a SHA-256 hash.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Creates and stores a token in {@code familyId}; returns the raw value for the cookie. */
    public Created create(User user, UUID familyId, ClientInfo client, Instant now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setFamilyId(familyId);
        token.setExpiresAt(now.plus(properties.refreshToken().ttl()));
        token.setUserAgent(client.userAgent());
        token.setIpAddress(client.ipAddress());
        repository.save(token);
        return new Created(raw, token);
    }

    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record Created(String rawValue, RefreshToken token) {

        @Override
        public String toString() {
            return "Created[token=" + token.getId() + "]";
        }
    }
}
