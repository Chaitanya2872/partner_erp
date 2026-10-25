package com.iotiq.partnererp.modules.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * New account. The temporary password must be changed at first sign-in; permissions start from the
 * role's template.
 */
public record CreateUserRequest(
        @NotBlank(message = "Full name is required.") @Size(max = 150) String fullName,
        @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.") @Size(max = 254) String email,
        @NotNull(message = "Role is required.") UUID roleId,
        @NotBlank(message = "Temporary password is required.") @Size(max = 256) String temporaryPassword) {

    @Override
    public String toString() {
        return "CreateUserRequest[email=" + email + ", roleId=" + roleId + "]";
    }
}
