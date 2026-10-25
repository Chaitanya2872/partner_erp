package com.iotiq.partnererp.modules.auth.entity;

/**
 * User account status.
 */
public enum UserStatus {

    ACTIVE("Active"),
    DISABLED("Disabled"),
    LOCKED("Locked");

    private final String label;

    UserStatus(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
