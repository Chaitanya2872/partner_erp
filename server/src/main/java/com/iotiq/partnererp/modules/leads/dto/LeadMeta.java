package com.iotiq.partnererp.modules.leads.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Choices for lead forms and filters: stages, project types, referring partners (with their current fee rate,
 * which acknowledging locks) and the SLA and protection settings.
 */
public record LeadMeta(List<Option> stages, List<Option> projectTypes, List<PartnerOption> partners,
                       int ackTargetHours, int estimateTargetDays, int protectionDays) {

    public record Option(String code, String label) {
    }

    public record PartnerOption(UUID id, String name, String statusLabel, boolean active, String tierName,
                                BigDecimal feePercent) {
    }
}
