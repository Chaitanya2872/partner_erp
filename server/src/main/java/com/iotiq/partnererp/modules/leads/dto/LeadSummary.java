package com.iotiq.partnererp.modules.leads.dto;

import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.modules.leads.entity.ProjectType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lead list row (BR-L9): client and project, partner, stage, budget, registration and SLA flags.
 *
 * @param protectionActive BR-L7: {@code protectionUntil >= today}
 */
public record LeadSummary(UUID id, String clientName, String clientPhone, String city, ProjectType projectType,
                          String projectTypeLabel, Integer units, BigDecimal budget, LeadStage stage, String stageLabel,
                          PartnerRef partner, Instant acknowledgedAt, BigDecimal lockedFeePercent,
                          LocalDate protectionUntil, boolean protectionActive, boolean conflict, SlaStatus sla,
                          Instant createdAt, long version) {
}
