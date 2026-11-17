package com.iotiq.partnererp.modules.estimates.dto;

import java.time.Instant;

/**
 * An archived estimate (partner copy) for the read-only archive view. It can't be edited; only restored.
 */
public record EstimateArchiveDetail(EstimateResponse estimate, Instant deletedAt, String deletedByName,
                                    String deleteReason) {
}
