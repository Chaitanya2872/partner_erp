package com.iotiq.partnererp.modules.leads.dto;

import com.iotiq.partnererp.common.enums.LeadStage;
import jakarta.validation.constraints.NotNull;

/**
 * Inline stage change (D-04), with an optional reason for the history.
 */
public record LeadStageRequest(@NotNull LeadStage stage, String reason, @NotNull Long version) {
}
