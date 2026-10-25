package com.iotiq.partnererp.modules.partners.dto;

import com.iotiq.partnererp.common.enums.PartnerStatus;
import com.iotiq.partnererp.modules.partners.entity.AgreementStatus;
import com.iotiq.partnererp.modules.partners.entity.PartnerCategory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Partner list row (BR-P11): assigned tier plus the calculated tier when they differ, paid revenue (12 m) and
 * lead count. No PAN or bank details.
 */
public record PartnerSummary(UUID id, String name, PartnerCategory category, String categoryLabel, String city,
                             String contactName, String phone, PartnerStatus status, String statusLabel, TierRef tier,
                             TierRef calculatedTier, BigDecimal revenue12m, AgreementStatus agreementStatus,
                             String agreementStatusLabel, long leadCount, boolean strategicProgramme,
                             boolean downgradeGrace) {
}
