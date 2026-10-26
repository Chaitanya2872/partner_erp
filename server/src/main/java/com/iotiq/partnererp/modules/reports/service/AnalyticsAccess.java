package com.iotiq.partnererp.modules.reports.service;

import com.iotiq.partnererp.modules.auth.entity.DataScope;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.security.AuthenticatedUser;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which business data a dashboard or report may aggregate for a user (DECISIONS P12-02). Opening the dashboard needs
 * {@code DASHBOARD:READ} and reports {@code REPORTS:READ}, but neither grants data on its own: every figure also needs
 * read access to the module that owns the records, with the ALL data scope. SELF and TEAM scopes can't be evaluated
 * yet (D-30, see {@code DataScopeService}), so a scoped user gets no aggregate of that module instead of an
 * organisation-wide one. The check is made before any query runs.
 */
public record AnalyticsAccess(Set<ModuleCode> visible, Map<ModuleCode, String> restricted, boolean internalB2c,
                              boolean partnerSensitive) {

    /** Modules whose records feed dashboards and reports. */
    public static final Set<ModuleCode> DATA_MODULES = EnumSet.of(ModuleCode.PARTNERS, ModuleCode.LEADS,
            ModuleCode.PRODUCTS, ModuleCode.ESTIMATES, ModuleCode.INVOICES, ModuleCode.PAYMENTS, ModuleCode.PAYOUTS);

    public static AnalyticsAccess of(AuthenticatedUser user) {
        Set<ModuleCode> visible = EnumSet.noneOf(ModuleCode.class);
        Map<ModuleCode, String> restricted = new LinkedHashMap<>();
        for (ModuleCode module : DATA_MODULES) {
            DataScope scope = user.permissions().scope(module.name()).orElse(null);
            if (scope == null) {
                restricted.put(module, "NO_ACCESS");
            } else if (scope != DataScope.ALL) {
                restricted.put(module, "SCOPE_" + scope.name());
            } else {
                visible.add(module);
            }
        }
        return new AnalyticsAccess(visible, restricted, user.permissions().canPerform("PRODUCTS", "VIEW_INTERNAL_B2C"),
                user.permissions().canPerform("PARTNERS", "VIEW_PARTNER_SENSITIVE"));
    }

    /** True when every module is visible with the ALL scope. */
    public boolean sees(ModuleCode... modules) {
        return Arrays.stream(modules).allMatch(visible::contains);
    }
}
