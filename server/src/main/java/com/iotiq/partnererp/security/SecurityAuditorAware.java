package com.iotiq.partnererp.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Fills created_by/updated_by from the signed-in user. Empty for system work (bootstrap, migrations).
 */
@Component("securityAuditorAware")
public class SecurityAuditorAware implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        return SecurityUtils.currentUserId();
    }
}
