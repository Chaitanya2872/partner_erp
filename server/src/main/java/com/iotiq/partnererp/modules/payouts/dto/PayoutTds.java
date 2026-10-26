package com.iotiq.partnererp.modules.payouts.dto;

import java.math.BigDecimal;
import java.util.UUID;
/**
 * The TDS of a payout or preview.
 *
 * @param message      why TDS isn't calculated, when it isn't
 * @param computedTds  TDS from the configuration; {@code tdsAmount} differs when it was adjusted by hand
 */
public record PayoutTds(String status, String message, UUID configurationId, String sectionCode,
                        BigDecimal ratePercent, BigDecimal fyThreshold, boolean panVerified,
                        BigDecimal priorFyCommission, BigDecimal priorFyTdsBase, BigDecimal tdsBase,
                        BigDecimal computedTds, BigDecimal tdsAmount, boolean adjusted) {
}
