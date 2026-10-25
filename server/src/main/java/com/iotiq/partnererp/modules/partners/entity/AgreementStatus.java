package com.iotiq.partnererp.modules.partners.entity;

/**
 * Partner agreement status (source slaStatus).
 */
public enum AgreementStatus {

    NOT_SENT("Not sent"),
    SENT("Sent"),
    SIGNED("Signed");

    private final String label;

    AgreementStatus(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
