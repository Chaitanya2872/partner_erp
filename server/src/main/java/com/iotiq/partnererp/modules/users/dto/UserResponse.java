package com.iotiq.partnererp.modules.users.dto;

import com.iotiq.partnererp.modules.auth.dto.RoleRef;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * User account as shown in User Management. No password or token data.
 *
 * @param locked whether a temporary sign-in lockout is in force ({@code lockedUntil} in the future)
 */
public record UserResponse(UUID id, String fullName, String email, RoleRef role, UserStatus status,
                           boolean mustChangePassword, Instant lastLoginAt, Instant lockedUntil, boolean locked,
                           Instant createdAt, Instant updatedAt, long version) {

    public static UserResponse of(User user) {
        return of(user, Instant.now());
    }

    public static UserResponse of(User user, Instant now) {
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), RoleRef.of(user.getRole()),
                user.getStatus(), user.isMustChangePassword(), user.getLastLoginAt(), locked ? user.getLockedUntil() : null,
                locked, user.getCreatedAt(), user.getUpdatedAt(), user.getVersion());
    }
}
