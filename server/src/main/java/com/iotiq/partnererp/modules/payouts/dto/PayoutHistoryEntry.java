package com.iotiq.partnererp.modules.payouts.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
/** One audited event of a payout, newest first. */
public record PayoutHistoryEntry(UUID id, Instant at, String actorName, String action, List<Change> changes) {

    public record Change(String field, JsonNode from, JsonNode to) {
    }
}
