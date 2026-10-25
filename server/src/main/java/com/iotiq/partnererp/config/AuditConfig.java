package com.iotiq.partnererp.config;

import com.iotiq.partnererp.common.constants.AppConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Single clock for business dates and JPA audit timestamps, so tests can pin time.
 * The auditor (created_by/updated_by) is the signed-in user ({@code SecurityAuditorAware}).
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider", auditorAwareRef = "securityAuditorAware")
public class AuditConfig {

    @Bean
    public Clock clock() {
        return Clock.system(AppConstants.BUSINESS_ZONE);
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
