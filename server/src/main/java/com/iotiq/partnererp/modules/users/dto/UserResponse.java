package com.iotiq.partnererp.modules.users.dto;

import com.iotiq.partnererp.modules.auth.dto.RoleRef;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * User account as shown in User Management. No password or token data.
 */
public record UserResponse(UUID id, String fullName, String email, RoleRef role, UserStatus status,
                           boolean mustChangePassword, Instant lastLoginAt, Instant lockedUntil, Instant createdAt,
                           Instant updatedAt, long version) {

    public static UserResponse of(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), RoleRef.of(user.getRole()),
                user.getStatus(), user.isMustChangePassword(), user.getLastLoginAt(), user.getLockedUntil(),
                user.getCreatedAt(), user.getUpdatedAt(), user.getVersion());
    }
}
