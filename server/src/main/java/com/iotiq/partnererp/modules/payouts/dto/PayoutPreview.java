package com.iotiq.partnererp.modules.payouts.dto;

import com.iotiq.partnererp.modules.leads.dto.PartnerRef;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
/**
 * What a payout for the partner would contain now.
 *
 * @param blockers    why it can't be created, or why it couldn't be approved yet
 * @param openPayout  the partner's draft or pending payout, which must be finished or recalculated instead
 */
public record PayoutPreview(PartnerRef partner, LocalDate asOfDate, String fyLabel, List<PayoutLineView> lines,
                            BigDecimal receiptsTotal, BigDecimal adjustmentsTotal, BigDecimal commissionTotal,
                            PayoutTds tds, BigDecimal netAmount, boolean canCreate, List<String> blockers,
                            PayoutSummary openPayout) {
}
