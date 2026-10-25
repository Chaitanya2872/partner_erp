package com.iotiq.partnererp.security;

import java.util.UUID;

/**
 * The signed-in user, rebuilt from the database for each request. Holds no password data.
 */
public record AuthenticatedUser(UUID id, String email, String fullName, UUID roleId, String roleCode,
                                String roleDisplayName, boolean mustChangePassword,
                                EffectivePermissions permissions) {

    public boolean isSuperAdmin() {
        return permissions.superAdmin();
    }
}
