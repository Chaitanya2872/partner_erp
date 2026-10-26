package com.iotiq.partnererp.modules.invoices.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One audited change to an invoice, newest first. Invoice audit entries hold partner-safe values only. */
public record InvoiceHistoryEntry(UUID id, Instant at, String actorName, String action, List<Change> changes) {

    public record Change(String field, JsonNode from, JsonNode to) {
    }
}
