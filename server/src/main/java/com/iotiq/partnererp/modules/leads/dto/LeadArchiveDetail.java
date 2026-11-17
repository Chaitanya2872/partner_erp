package com.iotiq.partnererp.modules.leads.dto;

import java.time.Instant;

/**
 * An archived lead for the read-only archive view. It can't be edited; only restored.
 */
public record LeadArchiveDetail(LeadResponse lead, Instant deletedAt, String deletedByName, String deleteReason) {
}
