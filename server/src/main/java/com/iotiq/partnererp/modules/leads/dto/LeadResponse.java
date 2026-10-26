package com.iotiq.partnererp.modules.leads.dto;

import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.modules.leads.entity.ProjectType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One lead with its SLA block.
 *
 * @param conflictPartnerName BR-L6: the partner currently protecting the same phone, if any. On a save that sets
 *                            {@code conflict} it is the name the warning toast shows.
 */
public record LeadResponse(UUID id, String leadNumber, String clientName, String clientPhone, String clientEmail,
                           String city, PartnerRef partner,
                           ProjectType projectType, String projectTypeLabel, Integer units, BigDecimal budget,
                           LeadStage stage, String stageLabel, String presalesOwner, LocalDate expectedClose,
                           String source, String notes, Instant acknowledgedAt, String acknowledgedByName,
                           BigDecimal lockedFeePercent, LocalDate protectionUntil, boolean protectionActive,
                           boolean conflict, String conflictPartnerName, SlaStatus sla, Instant createdAt,
                           Instant updatedAt, long version) {
}
