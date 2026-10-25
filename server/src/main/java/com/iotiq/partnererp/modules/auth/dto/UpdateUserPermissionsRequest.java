package com.iotiq.partnererp.modules.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Full replacement of a user's permissions. Modules left out get no access; actions left out are revoked.
 */
public record UpdateUserPermissionsRequest(@NotNull List<@Valid @NotNull ModulePermissionDto> modules,
                                           @NotNull List<@NotNull String> actions) {
}
