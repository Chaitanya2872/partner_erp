package com.iotiq.partnererp.modules.products.entity;

/**
 * Item categories (source ICATS). SERVICES has no seeded products.
 */
public enum ProductCategory {

    SMART_TOUCH_INTERFACES("Smart Touch Interfaces"),
    SMART_RELAY_SENSING("Smart Relay & Sensing"),
    SMART_GLIDE("Smart Glide"),
    LOCKS_ENTRY("Locks & Entry"),
    SERVICES("Services");

    private final String label;

    ProductCategory(String label) {
        this.label = label;
    }

    /** Display text used by the original application. */
    public String getLabel() {
        return label;
    }
}
