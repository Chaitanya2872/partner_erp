package com.iotiq.partnererp.modules.settings.dto;

import com.iotiq.partnererp.modules.invoices.dto.InvoiceMeta;

import java.util.List;

/**
 * Supplier GST registration. {@code complete} is true when invoices can be issued: state, GSTIN and address are set.
 * {@code states} lists the GST state codes the registration may use, so the settings screen needs no invoice access.
 */
public record GstRegistrationResponse(String legalName, String address, String stateCode, String stateName,
                                      String gstin, boolean complete, long version, List<InvoiceMeta.State> states) {
}
