package com.iotiq.partnererp.modules.b2c.entity;

/**
 * Parent kind of an internal B2C twin.
 */
public enum B2CDocumentType {

    ESTIMATE("Estimate"),
    INVOICE("Invoice");

    private final String label;

    B2CDocumentType(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
