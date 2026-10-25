package com.iotiq.partnererp.modules.partners.dto;

import com.iotiq.partnererp.modules.settings.entity.PartnerTier;

import java.math.BigDecimal;

/**
 * A tier as shown next to a partner: level, current name (editable in Settings) and fee rate.
 */
public record TierRef(int level, String name, BigDecimal commissionPercent) {

    public static TierRef of(PartnerTier tier) {
        return new TierRef(tier.getTierLevel(), tier.getName(), tier.getCommissionPercent());
    }
}
