package com.iotiq.partnererp.modules.partners.dto;

import com.iotiq.partnererp.modules.partners.entity.AgreementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Detail-page tiles (BR-P12, BR-P13): tier, paid revenue over the trailing 365 days, progress to the next
 * tier, referral fees and payout readiness.
 *
 * @param revenueSince   first day counted in {@code revenue12m} (today − 365 days, Asia/Kolkata)
 * @param nextTier       null at the top tier
 * @param payoutReady    PAN and bank account are both on file (BR-P7)
 */
public record PartnerPerformance(TierRef tier, TierRef calculatedTier, BigDecimal revenue12m, LocalDate revenueSince,
                                 TierRef nextTier, BigDecimal nextTierMinimum, Integer progressPercent,
                                 BigDecimal remainingToNextTier, BigDecimal feesUnpaid, BigDecimal feesPaid,
                                 long leadCount, AgreementStatus agreementStatus, boolean payoutReady) {
}
