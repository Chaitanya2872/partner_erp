package com.iotiq.partnererp.modules.invoices.dto;

import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.modules.estimates.dto.TotalsResponse;

import com.iotiq.partnererp.common.persistence.LineKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Internal B2C twin of an issued invoice (§6.6). Only for {@code INTERNAL_B2C} readers; never sent to partners.
 *
 * @param uplift B2C taxable − partner taxable ("Partner discount vs B2C list")
 */
public record InternalInvoiceDocument(UUID invoiceId, String internalNumber, String parentNumber,
                                      LocalDate documentDate, InvoiceStatus status, String statusLabel,
                                      BigDecimal discountPercent, boolean igst, List<Line> lines,
                                      TotalsResponse b2cTotals, TotalsResponse partnerTotals, BigDecimal uplift,
                                      boolean b2cIncomplete, int missingB2cPrices, Instant updatedAt) {

    public record Line(int lineNo, LineKind kind, String b2cName, String partnerName, String hsn, String unit, BigDecimal quantity,
                       BigDecimal rate, BigDecimal gstPercent, BigDecimal amount, boolean b2cPriceMissing) {
    }
}
