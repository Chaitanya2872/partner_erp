package com.iotiq.partnererp.common.enums;

/**
 * Lead stages in source order (STAGES). Open = any stage except WON/LOST.
 */
public enum LeadStage {

    NEW("New"),
    REGISTERED("Registered"),
    PRESALES("Presales"),
    SITE_VISIT("Site visit"),
    ESTIMATE_SENT("Estimate sent"),
    NEGOTIATION("Negotiation"),
    WON("Won"),
    LOST("Lost");

    private final String label;

    LeadStage(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
