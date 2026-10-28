package com.iotiq.partnererp.modules.roles.dto;

import com.iotiq.partnererp.modules.auth.dto.ModulePermissionDto;

import java.util.List;
import java.util.UUID;

/**
 * A role's default permissions: the matrix given to new accounts and to accounts moved to the role. Changing it never
 * changes existing accounts. Super Admin access is implicit and complete, so its matrix is not editable.
 *
 * @param modules one entry per module, in catalogue order (read=false for no access)
 * @param version the role's version; template changes increment it
 */
public record RolePermissionsResponse(UUID roleId, String code, String displayName, boolean superAdmin,
                                      boolean editable, List<ModulePermissionDto> modules, long userCount,
                                      long version) {
}
