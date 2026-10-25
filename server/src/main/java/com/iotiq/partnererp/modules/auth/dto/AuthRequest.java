package com.iotiq.partnererp.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login credentials. The password is never logged or audited.
 */
public record AuthRequest(@NotBlank(message = "Email is required.") @Size(max = 254) String email,
                          @NotBlank(message = "Password is required.") @Size(max = 256) String password) {

    @Override
    public String toString() {
        return "AuthRequest[email=" + email + "]";
    }
}
