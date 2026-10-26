package com.iotiq.partnererp.modules.estimates.dto;

/**
 * Body of the status actions. {@code version}, when sent, must match (optimistic locking).
 */
public record EstimateStatusRequest(Long version) {
}
