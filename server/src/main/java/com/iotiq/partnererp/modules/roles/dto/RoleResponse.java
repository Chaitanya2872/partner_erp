package com.iotiq.partnererp.modules.roles.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iotiq.partnererp.modules.auth.entity.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * A system role. {@code userCount} is included only for callers with Role Management access.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoleResponse(UUID id, String code, String displayName, String description, Long userCount,
                           Instant updatedAt, long version) {

    public static RoleResponse of(Role role, Long userCount) {
        return new RoleResponse(role.getId(), role.getCode(), role.getDisplayName(), role.getDescription(), userCount,
                role.getUpdatedAt(), role.getVersion());
    }
}
