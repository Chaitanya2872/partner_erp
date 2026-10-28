package com.iotiq.partnererp.modules.users.dto;

import jakarta.validation.constraints.NotNull;

/** A command on an account that only needs the version the administrator saw (unlock). */
public record UserVersionRequest(@NotNull(message = "Version is required.") Long version) {
}
