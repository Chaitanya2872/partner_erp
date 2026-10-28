package com.iotiq.partnererp.modules.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An administrator's password reset: a temporary password the user must change at the next sign-in. There is no
 * email delivery in this version; the administrator passes the password on securely, as for new accounts.
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Temporary password is required.") @Size(max = 256) String temporaryPassword,
        @NotNull(message = "Version is required.") Long version) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[version=" + version + "]";
    }
}
