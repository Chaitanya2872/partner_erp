package com.iotiq.partnererp.modules.payments.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One audited event of a payment, newest first. References are recorded masked. */
public record PaymentHistoryEntry(UUID id, Instant at, String actorName, String action, List<Change> changes) {

    public record Change(String field, JsonNode from, JsonNode to) {
    }
}
