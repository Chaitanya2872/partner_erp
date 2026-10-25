package com.iotiq.partnererp.common.enums;

/**
 * Who buys on an estimate/invoice (source 'Who buys'). PARTNER means a bulk purchase with no referral fee.
 */
public enum BillTo {

    CLIENT("Client buys from IOTIQ"),
    PARTNER("Partner buys (bulk)");

    private final String label;

    BillTo(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
