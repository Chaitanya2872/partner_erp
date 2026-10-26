package com.iotiq.partnererp.common.enums;

/**
 * Stored invoice lifecycle (Phase 9 decision 3). Only drafts can change; an issued invoice can only be cancelled.
 * The payment status is derived separately from recorded payments (see PaymentStatus, ADR-08).
 */
public enum InvoiceStatus {

    DRAFT("Draft"),
    ISSUED("Issued"),
    CANCELLED("Cancelled");

    private final String label;

    InvoiceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
