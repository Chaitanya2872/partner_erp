package com.iotiq.partnererp.modules.estimates.dto;

import com.iotiq.partnererp.common.persistence.LineKind;
import com.iotiq.partnererp.modules.estimates.entity.EstimateStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Internal B2C twin of an estimate (§6.6, FR-09). Only for {@code INTERNAL_B2C} readers; never sent to partners.
 *
 * @param uplift        B2C taxable − partner taxable ("Partner discount vs B2C list")
 * @param b2cIncomplete some lines have no B2C price, so the comparison is incomplete
 */
public record InternalEstimateDocument(UUID estimateId, String internalNumber, String parentNumber,
                                       LocalDate documentDate, EstimateStatus status, String statusLabel,
                                       BigDecimal discountPercent, boolean igst, List<Line> lines,
                                       TotalsResponse b2cTotals, TotalsResponse partnerTotals, BigDecimal uplift,
                                       boolean b2cIncomplete, int missingB2cPrices, Instant updatedAt) {

    public record Line(int lineNo, LineKind kind, String b2cName, String partnerName, String hsn, String unit, BigDecimal quantity,
                       BigDecimal rate, BigDecimal gstPercent, BigDecimal amount, boolean b2cPriceMissing) {
    }
}
