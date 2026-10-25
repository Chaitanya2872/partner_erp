package com.iotiq.partnererp.modules.payouts.entity;

/**
 * Stored payout status. Display states Due/Clawback are derived (source poStatus).
 */
public enum PayoutStatus {

    ACCRUED("Accrued"),
    PAID("Paid");

    private final String label;

    PayoutStatus(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
