package com.iotiq.partnererp.modules.payments.entity;

/**
 * Kind of payment ledger entry (V17). Receipts are positive; reversals mirror a receipt with a negative amount;
 * refunds are negative entries carried over from before Phase 10 and can't be recorded any more (P10-03).
 */
public enum PaymentEntryType {

    RECEIPT("Receipt"),
    REVERSAL("Reversal"),
    REFUND("Refund");

    private final String label;

    PaymentEntryType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
