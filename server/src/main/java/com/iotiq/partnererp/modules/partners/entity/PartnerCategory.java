package com.iotiq.partnererp.modules.partners.entity;

/**
 * Partner categories (source CATS).
 */
public enum PartnerCategory {

    INTERIOR_DESIGNER("Interior designer"),
    ARCHITECT("Architect"),
    BUILDER("Builder"),
    DEVELOPER("Developer"),
    CONTRACTOR("Contractor (civil/electrical/MEP)"),
    ELECTRICAL_DEALER("Electrical dealer"),
    KITCHEN_FURNITURE_SHOWROOM("Kitchen / furniture showroom"),
    SOCIETY_REAL_ESTATE_APP("Society / real-estate app"),
    OTHER("Other");

    private final String label;

    PartnerCategory(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
