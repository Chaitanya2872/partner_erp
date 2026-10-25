package com.iotiq.partnererp.common.enums;

/**
 * Derived invoice payment status (source invStatus). Never stored.
 */
public enum PaymentStatus {

    UNPAID("Unpaid"),
    PARTLY_PAID("Partly paid"),
    PARTLY_PAID_OVERDUE("Partly paid · overdue"),
    OVERDUE("Overdue"),
    PAID("Paid"),
    CANCELLED("Cancelled");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
