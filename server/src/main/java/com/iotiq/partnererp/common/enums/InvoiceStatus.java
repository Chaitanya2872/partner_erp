package com.iotiq.partnererp.common.enums;

/**
 * Stored invoice lifecycle. Payment status is derived separately (see PaymentStatus, ADR-08).
 */
public enum InvoiceStatus {

    OPEN("Open"),
    CANCELLED("Cancelled");

    private final String label;

    InvoiceStatus(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
