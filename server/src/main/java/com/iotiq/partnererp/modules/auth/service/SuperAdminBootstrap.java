package com.iotiq.partnererp.modules.auth.service;

import com.iotiq.partnererp.config.BootstrapProperties;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.Role;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.repository.RoleRepository;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first Super Admin from {@code BOOTSTRAP_SUPERADMIN_*} when — and only when — no Super Admin
 * account exists. The account must change its password at first sign-in. Credentials are never logged;
 * once the account exists the variables are ignored and should be removed from the environment.
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;

    public SuperAdminBootstrap(BootstrapProperties properties, UserRepository userRepository,
                               RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                               PasswordPolicy passwordPolicy, AuditService auditService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRoleCode(RoleCode.SUPER_ADMIN.name())) {
            return;
        }
        if (!properties.isConfigured()) {
            log.warn("No Super Admin account exists. Set BOOTSTRAP_SUPERADMIN_EMAIL and BOOTSTRAP_SUPERADMIN_PASSWORD "
                    + "(see server/README.md) and restart to create it.");
            return;
        }
        String email = properties.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 254) {
            throw new IllegalStateException("BOOTSTRAP_SUPERADMIN_EMAIL is not a valid email address");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            // Never promote an existing account silently.
            throw new IllegalStateException("BOOTSTRAP_SUPERADMIN_EMAIL belongs to an existing non-Super-Admin account");
        }
        try {
            passwordPolicy.validate("password", properties.password(), email);
        } catch (RuntimeException e) {
            throw new IllegalStateException("BOOTSTRAP_SUPERADMIN_PASSWORD does not meet the password policy ("
                    + passwordPolicy.describe() + ")");
        }
        Role role = roleRepository.findByCode(RoleCode.SUPER_ADMIN.name()).orElseThrow();
        User user = new User();
        user.setFullName(properties.fullName() == null || properties.fullName().isBlank()
                ? "Super Admin" : properties.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        user.setRole(role);
        user.setMustChangePassword(true);
        userRepository.save(user);
        auditService.recordAs(null, AuditActions.SUPER_ADMIN_BOOTSTRAPPED, AuditActions.ENTITY_USER, user.getId(),
                null, AuditService.values("email", email, "role", role.getCode()));
        log.info("Created the bootstrap Super Admin account ({}). It must change its password at first sign-in; "
                + "remove the BOOTSTRAP_SUPERADMIN_* variables now.", mask(email));
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }
}
