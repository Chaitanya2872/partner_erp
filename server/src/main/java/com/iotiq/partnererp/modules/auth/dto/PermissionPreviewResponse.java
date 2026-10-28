package com.iotiq.partnererp.modules.auth.dto;

import com.iotiq.partnererp.modules.auth.entity.DataScope;

import java.util.List;
import java.util.UUID;

/**
 * What saving a proposed permission matrix would change (nothing is saved).
 *
 * @param granted         authorities ({@code MODULE:LEVEL} or {@code MODULE:ACTION}) the user would gain
 * @param revoked         authorities the user would lose
 * @param effective       every authority the user would hold
 * @param differsFromRole modules (and granted actions) that would differ from the role's default
 */
public record PermissionPreviewResponse(UUID userId, boolean changed, List<String> granted, List<String> revoked,
                                        List<ScopeChange> scopeChanges, List<String> effective,
                                        List<String> differsFromRole) {

    public record ScopeChange(String moduleCode, DataScope from, DataScope to) {
    }
}
