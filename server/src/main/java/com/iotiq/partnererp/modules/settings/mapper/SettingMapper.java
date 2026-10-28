package com.iotiq.partnererp.modules.settings.mapper;

import com.iotiq.partnererp.modules.invoices.tax.GstState;
import com.iotiq.partnererp.modules.settings.dto.SettingResponse;
import com.iotiq.partnererp.modules.settings.entity.CompanySettings;
import com.iotiq.partnererp.modules.settings.entity.PartnerTier;

public final class SettingMapper {

    private SettingMapper() {
    }

    public static SettingResponse.Company company(CompanySettings s, String updatedBy, boolean canEdit,
                                                  boolean canManageCommercial, SettingResponse.Limits limits) {
        GstState state = GstState.byCode(s.getStateCode()).orElse(null);
        return new SettingResponse.Company(s.getCompanyName(), s.getAddress(), s.getStateCode(),
                state == null ? null : state.getDisplayName(), s.getGstin(), s.getPhone(), s.getEmail(), s.getTerms(),
                s.getEstimateValidityDays(), s.getInvoiceDueDays(), s.getDefaultGstPercent(), s.getAcknowledgementHours(),
                s.getEstimateSlaDays(), s.getLeadProtectionDays(), s.getClawbackDays(), s.getUpdatedAt(), updatedBy,
                s.getVersion(), canEdit, canManageCommercial, limits);
    }

    public static SettingResponse.Tier tier(PartnerTier t, long partners, String updatedBy) {
        return new SettingResponse.Tier(t.getId().toString(), t.getTierLevel(), t.getName(), t.getCommissionPercent(),
                t.getMinimumRevenue(), t.getPayoutBusinessDays(), partners, t.getUpdatedAt(), updatedBy, t.getVersion());
    }
}
