package com.iotiq.partnererp.modules.partners.entity;

/**
 * Outcome of a tier review (source reviewCp) or an automatic upgrade on payment.
 */
public enum TierReviewAction {

    UPGRADE("Upgrade"),
    DOWNGRADE("Step down"),
    GRACE("Below threshold: grace quarter starts"),
    RECOVERED("Back above threshold: grace cleared"),
    AUTO_UPGRADE("Upgraded on payment");

    private final String label;

    TierReviewAction(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
