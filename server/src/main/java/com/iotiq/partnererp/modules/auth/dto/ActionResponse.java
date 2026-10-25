package com.iotiq.partnererp.modules.auth.dto;

import com.iotiq.partnererp.modules.auth.entity.PermissionKind;
import com.iotiq.partnererp.modules.auth.service.ModuleCatalog.ActionDef;

/**
 * An action permission. GENERIC actions (DELETE, EXPORT, APPROVE) are per-module flags; MODULE actions
 * belong to {@code moduleCode}. {@code superAdminOnly} actions cannot be granted.
 */
public record ActionResponse(String code, String displayName, String description, PermissionKind kind,
                             String moduleCode, boolean superAdminOnly) {

    public static ActionResponse of(ActionDef a) {
        return new ActionResponse(a.code(), a.displayName(), a.description(), a.kind(), a.moduleCode(),
                a.superAdminOnly());
    }
}
