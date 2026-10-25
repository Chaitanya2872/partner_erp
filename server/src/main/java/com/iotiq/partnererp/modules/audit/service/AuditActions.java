package com.iotiq.partnererp.modules.audit.service;

/**
 * Audit action and entity-type names used in {@code audit_logs}.
 */
public final class AuditActions {

    public static final String ENTITY_USER = "USER";
    public static final String ENTITY_ROLE = "ROLE";
    public static final String ENTITY_PARTNER = "PARTNER";

    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String LOGOUT = "LOGOUT";
    public static final String REFRESH_TOKEN_REUSED = "REFRESH_TOKEN_REUSED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String SUPER_ADMIN_BOOTSTRAPPED = "SUPER_ADMIN_BOOTSTRAPPED";

    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_ROLE_CHANGED = "USER_ROLE_CHANGED";
    public static final String USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
    public static final String PERMISSIONS_CHANGED = "PERMISSIONS_CHANGED";
    public static final String ROLE_UPDATED = "ROLE_UPDATED";

    public static final String PARTNER_CREATED = "PARTNER_CREATED";
    public static final String PARTNER_UPDATED = "PARTNER_UPDATED";
    public static final String PARTNER_STATUS_CHANGED = "PARTNER_STATUS_CHANGED";
    public static final String PARTNER_TIER_REVIEWED = "PARTNER_TIER_REVIEWED";

    private AuditActions() {
    }
}
