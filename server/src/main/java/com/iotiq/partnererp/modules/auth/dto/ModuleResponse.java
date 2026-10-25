package com.iotiq.partnererp.modules.auth.dto;

import com.iotiq.partnererp.modules.auth.service.ModuleCatalog.ModuleDef;

import java.util.List;

/**
 * A permission-controlled module, the permission columns it supports and its module-specific actions.
 */
public record ModuleResponse(String code, String displayName, String description, int sortOrder,
                             boolean supportsWrite, boolean supportsDelete, boolean supportsExport,
                             boolean supportsApprove, boolean supportsScope, boolean superAdminOnly,
                             List<ActionResponse> actions) {

    public static ModuleResponse of(ModuleDef m, List<ActionResponse> actions) {
        return new ModuleResponse(m.code(), m.displayName(), m.description(), m.sortOrder(), m.supportsWrite(),
                m.supportsDelete(), m.supportsExport(), m.supportsApprove(), m.supportsScope(), m.superAdminOnly(),
                actions);
    }
}
