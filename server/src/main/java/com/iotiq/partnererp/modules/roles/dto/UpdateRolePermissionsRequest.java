package com.iotiq.partnererp.modules.roles.dto;

import com.iotiq.partnererp.modules.auth.dto.ModulePermissionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Full replacement of a role's default module permissions. Modules left out get no access by default. */
public record UpdateRolePermissionsRequest(@NotNull List<@Valid @NotNull ModulePermissionDto> modules,
                                           @NotNull(message = "Version is required.") Long version) {
}
