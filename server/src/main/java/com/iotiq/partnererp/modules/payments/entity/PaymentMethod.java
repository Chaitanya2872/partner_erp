package com.iotiq.partnererp.modules.payments.entity;

/**
 * Payment modes (source recPayForm).
 */
public enum PaymentMethod {

    UPI("UPI"),
    NEFT_RTGS("NEFT / RTGS"),
    CHEQUE("Cheque"),
    CASH("Cash"),
    CARD("Card");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
