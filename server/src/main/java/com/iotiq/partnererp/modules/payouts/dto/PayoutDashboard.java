package com.iotiq.partnererp.modules.payouts.dto;

import java.math.BigDecimal;
import java.util.List;
/**
 * Payout totals.
 *
 * @param eligibleCommission  commission on eligible receipts not yet in a payout (all partners)
 * @param carriedAdjustments  approved adjustments not yet in a payout
 */
public record PayoutDashboard(String fyLabel, List<StatusTotal> byStatus, BigDecimal eligibleCommission,
                              long eligibleReceipts, BigDecimal carriedAdjustments, long pendingAdjustments,
                              BigDecimal paidFy, BigDecimal tdsFy, long partnersWithBalance, boolean tdsConfigured) {

    public record StatusTotal(String status, String label, long count, BigDecimal commission, BigDecimal tds,
                              BigDecimal net) {
    }
}
