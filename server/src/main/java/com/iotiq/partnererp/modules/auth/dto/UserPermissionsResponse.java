package com.iotiq.partnererp.modules.auth.dto;

import java.util.List;
import java.util.UUID;

/**
 * A user's permission matrix. For a Super Admin the matrix is the implicit full access and
 * {@code editable} is false.
 *
 * @param modules one entry per module, in catalogue order (read=false for no access)
 * @param actions granted module-specific action codes
 */
public record UserPermissionsResponse(UUID userId, RoleRef role, boolean superAdmin, boolean editable,
                                      List<ModulePermissionDto> modules, List<String> actions) {
}
