package com.iotiq.partnererp.modules.invoices.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Server tax and totals for the editor; nothing is saved. {@code internal} is present only for callers with
 * {@code PRODUCTS:VIEW_INTERNAL_B2C}.
 */
public record InvoicePreview(List<InvoiceResponse.Line> lines, InvoiceTotals totals, TaxTreatmentResponse tax,
                             EstimateComparison comparison, BigDecimal defaultFeePercent, BigDecimal feePercent,
                             boolean feeOverride, Internal internal) {

    public record Internal(List<InternalLine> lines, BigDecimal b2cTaxable, BigDecimal b2cGrandTotal,
                           BigDecimal uplift, int missingB2cPrices) {
    }

    public record InternalLine(int lineNo, UUID productId, String b2cName, BigDecimal b2cRate, BigDecimal b2cAmount,
                               boolean b2cPriceMissing) {
    }
}
