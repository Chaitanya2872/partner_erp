package com.iotiq.partnererp.modules.estimates.dto;

import com.iotiq.partnererp.common.enums.LeadStage;

import java.util.UUID;

/**
 * The client side of an estimate, from its lead. {@code deleted} is true when the lead was deleted afterwards.
 */
public record EstimateLeadRef(UUID id, String leadNumber, String clientName, String clientPhone, String city,
                              String projectTypeLabel, LeadStage stage, String stageLabel, boolean deleted) {
}
