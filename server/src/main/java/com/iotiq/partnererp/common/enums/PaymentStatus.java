package com.iotiq.partnererp.common.enums;

/**
 * Derived invoice payment status (source invStatus). Never stored.
 */
public enum PaymentStatus {

    UNPAID("Unpaid"),
    PARTLY_PAID("Partially paid"),
    PARTLY_PAID_OVERDUE("Partially paid · overdue"),
    OVERDUE("Overdue"),
    PAID("Paid"),
    CANCELLED("Cancelled");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    /** Display text ("Partly paid" in the source; "Partially paid" per Phase 9 decision 3). */
    public String getLabel() {
        return label;
    }
}
