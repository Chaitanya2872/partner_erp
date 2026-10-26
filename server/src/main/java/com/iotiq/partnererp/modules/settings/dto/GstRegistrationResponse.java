package com.iotiq.partnererp.modules.settings.dto;

import com.iotiq.partnererp.modules.invoices.dto.InvoiceMeta;

import java.util.List;

/**
 * Supplier GST registration. {@code complete} is true when the registration is filled in: state, GSTIN, address and
 * the e-invoicing declaration. Invoices are issued only when it is complete and e-invoicing does not apply.
 * {@code states} lists the GST state codes the registration may use, so the settings screen needs no invoice access.
 */
public record GstRegistrationResponse(String legalName, String address, String stateCode, String stateName,
                                      String gstin, Boolean einvoicingApplicable, boolean complete, long version,
                                      List<InvoiceMeta.State> states) {
}
