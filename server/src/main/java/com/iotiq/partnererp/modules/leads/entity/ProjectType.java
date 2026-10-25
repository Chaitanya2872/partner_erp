package com.iotiq.partnererp.modules.leads.entity;

/**
 * Lead project types (source PTYPES).
 */
public enum ProjectType {

    VILLA("Villa"),
    APARTMENT("Apartment"),
    MULTI_UNIT_PROJECT("Multi-unit project"),
    OFFICE("Office"),
    RETROFIT_RENOVATION("Retrofit / renovation"),
    OTHER("Other");

    private final String label;

    ProjectType(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
