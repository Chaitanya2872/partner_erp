package com.iotiq.partnererp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First Super Admin account, created only when no Super Admin exists (see server/README.md). Values come
 * from {@code BOOTSTRAP_SUPERADMIN_*} environment variables and are never logged.
 */
@ConfigurationProperties(prefix = "app.bootstrap.super-admin")
public record BootstrapProperties(String email, String password, String fullName) {

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
