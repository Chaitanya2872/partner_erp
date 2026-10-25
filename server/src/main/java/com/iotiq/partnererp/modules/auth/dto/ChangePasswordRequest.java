package com.iotiq.partnererp.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(@NotBlank(message = "Current password is required.") @Size(max = 256) String currentPassword,
                                    @NotBlank(message = "New password is required.") @Size(max = 256) String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
