package com.iotiq.partnererp.modules.roles.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Partial update of a role. Only {@code displayName} and {@code description} can change; {@code id} and
 * {@code code} are accepted only to reject attempts to change them explicitly.
 */
public record UpdateRoleRequest(String displayName, String description, String code, UUID id,
                                @NotNull(message = "Version is required.") Long version) {
}
