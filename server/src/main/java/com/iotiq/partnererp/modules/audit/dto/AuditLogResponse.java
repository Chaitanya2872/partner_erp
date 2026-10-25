package com.iotiq.partnererp.modules.audit.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * One audit entry. {@code actorName}/{@code actorEmail} are null for system events and failed logins.
 */
public record AuditLogResponse(UUID id, Instant createdAt, UUID userId, String actorName, String actorEmail,
                               String action, String entityType, UUID entityId, JsonNode oldValues,
                               JsonNode newValues, String ipAddress, String requestId) {
}
