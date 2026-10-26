package com.iotiq.partnererp.modules.invoices.dto;

/** Supplier or customer as printed on the invoice. */
public record InvoiceParty(String name, String address, String stateCode, String stateName, String gstin) {
}
