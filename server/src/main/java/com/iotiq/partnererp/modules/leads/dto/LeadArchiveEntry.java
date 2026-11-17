package com.iotiq.partnererp.modules.leads.dto;

import com.iotiq.partnererp.common.enums.LeadStage;

import java.time.Instant;
import java.util.UUID;

/**
 * Row of the lead archive: the lead as it was, plus who archived it, when and why.
 */
public record LeadArchiveEntry(UUID id, String leadNumber, String clientName, String clientPhone, String city,
                               String projectTypeLabel, LeadStage stage, String stageLabel, PartnerRef partner,
                               Instant createdAt, Instant deletedAt, String deletedByName, String deleteReason) {
}
