package com.iotiq.partnererp.modules.auth.service;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.modules.auth.dto.AuthResponse;
import com.iotiq.partnererp.modules.auth.dto.MeResponse;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.entity.UserStatus;
import com.iotiq.partnererp.modules.auth.mapper.AuthMapper;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.auth.service.SessionService.IssuedSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Login orchestration. Deliberately not transactional: a failed attempt is recorded in its own
 * transaction ({@link LoginAttemptService}) and the request then fails with a generic message, so the
 * response does not reveal whether the account exists.
 */
@Service
public class AuthService {

    /** Hash of a random value, compared for unknown accounts so response times are similar. */
    private final String timingHash;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final SessionService sessionService;
    private final PermissionService permissionService;
    private final AuthMapper authMapper;
    private final Clock clock;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       LoginAttemptService loginAttempts, SessionService sessionService,
                       PermissionService permissionService, AuthMapper authMapper, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
        this.sessionService = sessionService;
        this.permissionService = permissionService;
        this.authMapper = authMapper;
        this.clock = clock;
        this.timingHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResult login(String email, String password, ClientInfo client) {
        User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, timingHash);
            loginAttempts.recordUnknownAccount(email);
            throw invalidCredentials();
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now(clock))) {
            throw new BusinessException("ACCOUNT_LOCKED",
                    "Too many failed sign-in attempts. Try again in a few minutes.", HttpStatus.LOCKED);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttempts.recordWrongPassword(user.getId());
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE || !user.getRole().isActive()) {
            loginAttempts.recordInactiveAccount(user.getId(), user.getStatus().name());
            throw new BusinessException("ACCOUNT_DISABLED",
                    "This account is disabled. Contact your administrator.", HttpStatus.FORBIDDEN);
        }
        return toResult(sessionService.start(user.getId(), client));
    }

    public LoginResult refresh(String refreshToken, ClientInfo client) {
        return toResult(sessionService.rotate(refreshToken, client));
    }

    public void logout(String refreshToken) {
        sessionService.end(refreshToken);
    }

    public LoginResult changePassword(UUID userId, String currentPassword, String newPassword, ClientInfo client) {
        return toResult(sessionService.changePassword(userId, currentPassword, newPassword, client));
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User user = userRepository.findWithRoleById(userId).orElseThrow();
        return authMapper.toMe(user, permissionService.resolve(user));
    }

    private LoginResult toResult(IssuedSession session) {
        MeResponse me = me(session.user().getId());
        AuthResponse response = new AuthResponse(session.accessToken().value(), "Bearer",
                session.accessToken().expiresInSeconds(), session.accessToken().expiresAt(), me);
        return new LoginResult(response, session.refreshToken());
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "Invalid email or password.", HttpStatus.UNAUTHORIZED);
    }

    /** The response body plus the refresh token for the cookie. */
    public record LoginResult(AuthResponse response, String refreshToken) {

        @Override
        public String toString() {
            return "LoginResult[" + response + "]";
        }
    }
}
