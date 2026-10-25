package com.iotiq.partnererp.modules.products.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One audited change to a product, newest first. Internal fields are left out for callers without
 * {@code PRODUCTS:VIEW_INTERNAL_B2C}.
 */
public record ProductHistoryEntry(UUID id, Instant at, String actorName, String action, List<Change> changes) {

    public record Change(String field, JsonNode from, JsonNode to) {
    }
}
