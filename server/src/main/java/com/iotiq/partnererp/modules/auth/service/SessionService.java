package com.iotiq.partnererp.modules.auth.service;

import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.RefreshToken;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.entity.UserStatus;
import com.iotiq.partnererp.modules.auth.repository.RefreshTokenRepository;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.security.JwtService;
import com.iotiq.partnererp.security.JwtService.AccessToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Session lifecycle: start (after a verified login), refresh-token rotation with reuse detection, logout
 * and password change. A session is a refresh-token family; each rotation revokes the presented token and
 * links it to its successor. Presenting an already-revoked token revokes the whole family.
 */
@Service
public class SessionService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;
    private final Clock clock;

    public SessionService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                          RefreshTokenService refreshTokenService, JwtService jwtService,
                          PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, AuditService auditService,
                          Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Starts a new session for a user whose credentials were verified. */
    @Transactional
    public IssuedSession start(UUID userId, ClientInfo client) {
        User user = userRepository.findForUpdate(userId).orElseThrow();
        Instant now = Instant.now(clock);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        IssuedSession session = issue(user, UUID.randomUUID(), client, now);
        auditService.recordAs(user.getId(), AuditActions.LOGIN_SUCCEEDED, AuditActions.ENTITY_USER, user.getId(),
                null, null);
        return session;
    }

    /** Rotates a refresh token. Reuse of a revoked token revokes its family (kept despite the rejection). */
    @Transactional(noRollbackFor = RefreshTokenRejectedException.class)
    public IssuedSession rotate(String rawToken, ClientInfo client) {
        Instant now = Instant.now(clock);
        RefreshToken token = refreshTokenRepository.findForRotation(RefreshTokenService.hash(rawToken))
                .orElseThrow(() -> new RefreshTokenRejectedException("Unknown refresh token"));
        User user = token.getUser();
        if (token.getRevokedAt() != null) {
            int revoked = refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            auditService.recordAs(user.getId(), AuditActions.REFRESH_TOKEN_REUSED, AuditActions.ENTITY_USER,
                    user.getId(), null, AuditService.values("familyId", token.getFamilyId(), "revokedTokens", revoked));
            throw new RefreshTokenRejectedException("Refresh token reuse detected");
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new RefreshTokenRejectedException("Refresh token expired");
        }
        if (user.getStatus() != UserStatus.ACTIVE || !user.getRole().isActive()) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            throw new RefreshTokenRejectedException("Account is not active");
        }
        token.setRevokedAt(now);
        IssuedSession session = issue(user, token.getFamilyId(), client, now);
        token.setReplacedBy(session.refreshTokenId());
        return session;
    }

    /** Ends the session the token belongs to. Unknown tokens are ignored (logout is idempotent). */
    @Transactional
    public void end(String rawToken) {
        refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(rawToken)).ifPresent(token -> {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now(clock));
            UUID userId = token.getUser().getId();
            auditService.recordAs(userId, AuditActions.LOGOUT, AuditActions.ENTITY_USER, userId, null, null);
        });
    }

    /**
     * Changes the password, revokes every session of the account (all devices) and starts a fresh one for
     * the caller.
     */
    @Transactional
    public IssuedSession changePassword(UUID userId, String currentPassword, String newPassword, ClientInfo client) {
        User user = userRepository.findForUpdate(userId).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ValidationException.of("currentPassword", "Current password is incorrect.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ValidationException.of("newPassword", "New password must be different from the current password.");
        }
        passwordPolicy.validate("newPassword", newPassword, user.getEmail());
        Instant now = Instant.now(clock);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(now);
        user.setMustChangePassword(false);
        user.incrementSessionVersion();
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
        IssuedSession session = issue(user, UUID.randomUUID(), client, now);
        auditService.recordAs(user.getId(), AuditActions.PASSWORD_CHANGED, AuditActions.ENTITY_USER, user.getId(),
                null, AuditService.values("otherSessionsRevoked", true));
        return session;
    }

    /** Revokes all sessions of an account and invalidates its access tokens (deactivation). */
    @Transactional
    public void revokeAll(User user) {
        user.incrementSessionVersion();
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now(clock));
    }

    private IssuedSession issue(User user, UUID familyId, ClientInfo client, Instant now) {
        RefreshTokenService.Created refresh = refreshTokenService.create(user, familyId, client, now);
        AccessToken access = jwtService.issue(user, now);
        return new IssuedSession(user, access, refresh.rawValue(), refresh.token().getId());
    }

    public record IssuedSession(User user, AccessToken accessToken, String refreshToken, UUID refreshTokenId) {

        @Override
        public String toString() {
            return "IssuedSession[user=" + user.getId() + "]";
        }
    }
}
