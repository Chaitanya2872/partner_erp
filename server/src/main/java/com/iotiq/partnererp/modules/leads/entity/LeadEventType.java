package com.iotiq.partnererp.modules.leads.entity;

/**
 * Kinds of lead history entries.
 */
public enum LeadEventType {

    CREATED("Created"),
    STAGE_CHANGED("Stage changed"),
    ACKNOWLEDGED("Acknowledged"),
    AUTO_STAGE("Stage changed automatically"),
    ARCHIVED("Archived"),
    RESTORED("Restored");

    private final String label;

    LeadEventType(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
