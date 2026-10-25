package com.iotiq.partnererp.modules.leads.dto;

import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.modules.leads.entity.LeadEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * One stage-history entry, oldest first.
 */
public record LeadHistoryEntry(UUID id, LeadEventType event, String eventLabel, LeadStage fromStage,
                               String fromStageLabel, LeadStage toStage, String toStageLabel, String reason,
                               UUID actorId, String actorName, Instant occurredAt) {
}
