package com.iotiq.partnererp.modules.partners.dto;

import com.iotiq.partnererp.common.enums.PartnerStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Activate, deactivate or move a partner along Prospect → Onboarding → Active (BR-P3).
 */
public record PartnerStatusRequest(@NotNull(message = "Status is required.") PartnerStatus status,
                                   @NotNull(message = "Version is required.") Long version) {
}
