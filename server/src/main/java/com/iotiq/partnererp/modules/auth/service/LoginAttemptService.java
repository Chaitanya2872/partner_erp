package com.iotiq.partnererp.modules.auth.service;

import com.iotiq.partnererp.config.SecurityProperties;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Records failed logins in their own transactions (the login request itself fails), counts consecutive
 * wrong passwords and applies the temporary lockout.
 */
@Service
public class LoginAttemptService {

    private static final Pattern EMAIL_LIKE = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]{1,189}$");

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecurityProperties.Login rules;
    private final Clock clock;

    public LoginAttemptService(UserRepository userRepository, AuditService auditService,
                               SecurityProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.rules = properties.login();
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordWrongPassword(UUID userId) {
        User user = userRepository.findForUpdate(userId).orElseThrow();
        user.setFailedLoginCount(user.getFailedLoginCount() + 1);
        auditService.recordAs(null, AuditActions.LOGIN_FAILED, AuditActions.ENTITY_USER, userId, null,
                AuditService.values("reason", "WRONG_PASSWORD", "consecutiveFailures", user.getFailedLoginCount()));
        if (user.getFailedLoginCount() >= rules.maxFailedAttempts()) {
            Instant until = Instant.now(clock).plus(rules.lockoutDuration());
            user.setLockedUntil(until);
            user.setFailedLoginCount(0);
            auditService.recordAs(null, AuditActions.ACCOUNT_LOCKED, AuditActions.ENTITY_USER, userId, null,
                    AuditService.values("lockedUntil", until));
        }
    }

    /**
     * An unknown email. The attempted value is stored only if it looks like an email address, so a
     * password typed into the email field is never kept.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUnknownAccount(String attemptedEmail) {
        String email = attemptedEmail != null && EMAIL_LIKE.matcher(attemptedEmail.trim()).matches()
                ? attemptedEmail.trim().toLowerCase() : "[not an email address]";
        auditService.recordAs(null, AuditActions.LOGIN_FAILED, AuditActions.ENTITY_USER, null, null,
                AuditService.values("reason", "UNKNOWN_ACCOUNT", "email", email));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInactiveAccount(UUID userId, String status) {
        auditService.recordAs(null, AuditActions.LOGIN_FAILED, AuditActions.ENTITY_USER, userId, null,
                AuditService.values("reason", "ACCOUNT_" + status));
    }
}
