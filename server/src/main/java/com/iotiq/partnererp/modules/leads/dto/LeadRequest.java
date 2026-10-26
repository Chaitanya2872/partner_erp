package com.iotiq.partnererp.modules.leads.dto;

import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.modules.leads.entity.ProjectType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Create or replace a lead. The service validates and normalises every field (BR-L1, BR-L10).
 *
 * <ul>
 *   <li>{@code stage} defaults to New on create and is kept when null on update. A change follows the stage rules
 *       (D-04): reopening a Won or Lost lead needs {@code LEADS:APPROVE}. {@code stageReason} is recorded in the
 *       stage history.</li>
 *   <li>{@code partnerId} can't change once the registration is acknowledged (the fee rate is locked to it).</li>
 *   <li>{@code clientEmail} is optional and validated when given (D-16). The lead number is never accepted: the
 *       server allocates it.</li>
 *   <li>{@code version} is required on update (optimistic locking) and ignored on create.</li>
 * </ul>
 */
public record LeadRequest(String clientName, String clientPhone, String clientEmail, String city, UUID partnerId, ProjectType projectType,
                          Integer units, BigDecimal budget, LeadStage stage, String stageReason, String presalesOwner,
                          LocalDate expectedClose, String source, String notes, Long version) {
}
