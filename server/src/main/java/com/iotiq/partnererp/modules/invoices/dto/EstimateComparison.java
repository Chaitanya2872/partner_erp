package com.iotiq.partnererp.modules.invoices.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The source estimate's agreed values beside the invoice's, shown before issue (Phase 9 decision 2). Differences
 * come from per-line tax rounding or from a different tax type; the commercial values are copied unchanged.
 */
public record EstimateComparison(UUID estimateId, String estimateNumber, boolean estimateIgst,
                                 BigDecimal estimateTaxable, BigDecimal estimateGst, BigDecimal estimateGrandTotal,
                                 BigDecimal taxableDifference, BigDecimal gstDifference,
                                 BigDecimal grandTotalDifference, boolean taxTypeChanged) {
}
