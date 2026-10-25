package com.iotiq.partnererp.modules.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Profile and role update. {@code version} is the value last read (optimistic locking).
 */
public record UpdateUserRequest(
        @NotBlank(message = "Full name is required.") @Size(max = 150) String fullName,
        @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.") @Size(max = 254) String email,
        @NotNull(message = "Role is required.") UUID roleId,
        @NotNull(message = "Version is required.") Long version) {
}
