package com.iotiq.partnererp.modules.invoices.tax;

/** Intra-state (CGST + SGST/UTGST) or inter-state (IGST) supply. */
public enum TaxType {

    INTRA("CGST + SGST/UTGST (intra-state)"),
    INTER("IGST (inter-state)");

    private final String label;

    TaxType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
