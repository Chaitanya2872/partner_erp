package com.iotiq.partnererp.modules.settings.dto;

/** Supplier GST registration (Settings write). {@code version} is the settings row version. */
public record GstRegistrationRequest(String legalName, String address, String stateCode, String gstin, Long version) {
}
