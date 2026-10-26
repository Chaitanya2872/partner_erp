package com.iotiq.partnererp.modules.invoices.dto;

import com.iotiq.partnererp.modules.leads.dto.PartnerRef;

import java.util.UUID;

/** Restricted lead picker row for direct invoices: the minimum a user needs to choose a lead. */
public record InvoiceLeadOption(UUID id, String leadNumber, String clientName, PartnerRef partner) {
}
