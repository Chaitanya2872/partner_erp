package com.iotiq.partnererp.modules.users.dto;

import com.iotiq.partnererp.modules.auth.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Activate (ACTIVE) or deactivate (DISABLED) an account. Deactivation ends all of its sessions.
 */
public record UpdateUserStatusRequest(@NotNull(message = "Status is required.") UserStatus status,
                                      @NotNull(message = "Version is required.") Long version) {
}
