package com.iotiq.partnererp.modules.partners.dto;

/**
 * Record or withdraw a PAN check (Phase 11). {@code note} (how it was checked) is required when verifying.
 */
public record PanVerificationRequest(Boolean verified, String note, Long version) {
}
