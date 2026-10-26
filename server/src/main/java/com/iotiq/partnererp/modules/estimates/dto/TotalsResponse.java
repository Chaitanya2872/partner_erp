package com.iotiq.partnererp.modules.estimates.dto;

import com.iotiq.partnererp.common.persistence.DocumentTotals;
import com.iotiq.partnererp.common.util.DocumentCalculator;

import java.math.BigDecimal;

/**
 * Document totals (§6.1) with the CGST/SGST split used for display (D-21: SGST = GST − CGST).
 */
public record TotalsResponse(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxableAmount,
                             BigDecimal gstAmount, BigDecimal cgstAmount, BigDecimal sgstAmount,
                             BigDecimal grandTotal) {

    public static TotalsResponse of(DocumentCalculator.Totals t) {
        return new TotalsResponse(t.subtotal(), t.discountAmount(), t.taxableAmount(), t.gstAmount(), t.cgstAmount(),
                t.sgstAmount(), t.grandTotal());
    }

    public static TotalsResponse of(DocumentTotals t) {
        return of(new DocumentCalculator.Totals(t.getSubtotal(), t.getDiscountAmount(), t.getTaxableAmount(),
                t.getGstAmount(), t.getGrandTotal()));
    }
}
