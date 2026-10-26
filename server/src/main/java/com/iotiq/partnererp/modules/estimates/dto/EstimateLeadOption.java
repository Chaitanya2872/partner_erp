package com.iotiq.partnererp.modules.estimates.dto;

import com.iotiq.partnererp.modules.leads.dto.PartnerRef;

import java.util.UUID;

/**
 * A lead an estimate can be made for, from the restricted lead picker (Phase 8 decision 4). Only what is needed to
 * choose the lead: no phone, email, budget, notes, stage, SLA or protection details.
 */
public record EstimateLeadOption(UUID id, String leadNumber, String clientName, PartnerRef partner) {
}
