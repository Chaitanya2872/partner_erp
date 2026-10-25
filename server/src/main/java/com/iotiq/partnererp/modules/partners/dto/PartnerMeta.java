package com.iotiq.partnererp.modules.partners.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Choices for partner forms and filters, with the original application's labels and the current tiers.
 */
public record PartnerMeta(List<Option> categories, List<Option> statuses, List<Option> agreementStatuses,
                          List<Tier> tiers, String defaultAgreementVersion) {

    public record Option(String code, String label) {
    }

    public record Tier(int level, String name, BigDecimal commissionPercent, BigDecimal minimumRevenue,
                       int payoutBusinessDays) {
    }
}
