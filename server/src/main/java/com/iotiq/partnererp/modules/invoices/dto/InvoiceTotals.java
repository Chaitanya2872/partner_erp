package com.iotiq.partnererp.modules.invoices.dto;

import java.math.BigDecimal;

/** Invoice totals: sums of the line snapshots (BUSINESS_RULES §6.7). */
public record InvoiceTotals(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxableAmount,
                            BigDecimal cgstAmount, BigDecimal sgstAmount, BigDecimal igstAmount,
                            BigDecimal gstAmount, BigDecimal grandTotal) {
}
