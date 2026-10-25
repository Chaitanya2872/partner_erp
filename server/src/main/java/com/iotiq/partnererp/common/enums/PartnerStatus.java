package com.iotiq.partnererp.common.enums;

/**
 * Partner lifecycle status (source cpForm).
 */
public enum PartnerStatus {

    PROSPECT("Prospect"),
    ONBOARDING("Onboarding"),
    ACTIVE("Active"),
    INACTIVE("Inactive");

    private final String label;

    PartnerStatus(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
