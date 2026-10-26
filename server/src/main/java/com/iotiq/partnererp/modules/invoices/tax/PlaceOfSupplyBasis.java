package com.iotiq.partnererp.modules.invoices.tax;

/**
 * Why the place of supply is the chosen state. Recorded with every invoice so the choice can be reviewed; the
 * system never derives the place of supply from the billing state (Phase 9 decision 1).
 */
public enum PlaceOfSupplyBasis {

    RECIPIENT_LOCATION("Recipient's location (goods not moved, or services)"),
    DELIVERY_LOCATION("Where the goods are delivered (movement ends)"),
    INSTALLATION_SITE("Where the goods are installed or assembled"),
    OTHER("Other basis (explain in the confirmation note)");

    private final String label;

    PlaceOfSupplyBasis(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
