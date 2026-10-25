package com.iotiq.partnererp.modules.auth.dto;

import com.iotiq.partnererp.modules.auth.entity.Role;

import java.util.UUID;

/**
 * Role as embedded in other responses: the immutable code plus the current display name.
 */
public record RoleRef(UUID id, String code, String displayName) {

    public static RoleRef of(Role role) {
        return new RoleRef(role.getId(), role.getCode(), role.getDisplayName());
    }
}
