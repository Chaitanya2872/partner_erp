package com.iotiq.partnererp.modules.settings.dto;

/**
 * Supplier GST registration (Settings write). {@code version} is the settings row version.
 *
 * @param einvoicingApplicable whether e-invoicing (IRN/QR) applies to the company; required
 */
public record GstRegistrationRequest(String legalName, String address, String stateCode, String gstin,
                                     Boolean einvoicingApplicable, Long version) {
}
