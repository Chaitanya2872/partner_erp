package com.iotiq.partnererp.modules.estimates.dto;

import java.time.Instant;

/**
 * Row of the estimate archive: the partner-safe summary, plus who archived it, when and why.
 */
public record EstimateArchiveEntry(EstimateSummary estimate, Instant deletedAt, String deletedByName,
                                   String deleteReason) {
}
