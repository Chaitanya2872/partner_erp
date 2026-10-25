package com.iotiq.partnererp.modules.estimates.entity;

/**
 * Stored estimate statuses (source). 'Expired' is derived, never stored (D-29).
 */
public enum EstimateStatus {

    DRAFT("draft"),
    SENT("sent"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    INVOICED("invoiced");

    private final String label;

    EstimateStatus(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
