package com.iotiq.partnererp.modules.estimates.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.iotiq.partnererp.common.persistence.LineKind;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Server-side totals for the editor (nothing is saved). {@code internal} (B2C rates and totals, the missing-B2C
 * count) is filled only for callers with {@code PRODUCTS:VIEW_INTERNAL_B2C} and is omitted from the JSON otherwise.
 *
 * @param defaultFeePercent 0 when the partner buys, else the lead's locked rate or the partner's tier rate (BR-E3)
 * @param feeOverride       the effective fee differs from the default
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EstimatePreview(List<Line> lines, TotalsResponse totals, BigDecimal defaultFeePercent,
                              BigDecimal feePercent, boolean feeOverride, Internal internal) {

    public record Line(int lineNo, UUID id, LineKind kind, UUID productId, String sku, String description, String hsn, String unit,
                       BigDecimal quantity, BigDecimal unitPrice, BigDecimal gstPercent, BigDecimal amount) {
    }

    public record Internal(List<InternalLine> lines, TotalsResponse totals, BigDecimal uplift, int missingB2cPrices) {
    }

    public record InternalLine(int lineNo, String b2cName, BigDecimal b2cRate, BigDecimal amount,
                               boolean b2cPriceMissing) {
    }
}
