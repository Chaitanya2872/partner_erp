package com.iotiq.partnererp.modules.estimates.dto;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.modules.estimates.entity.EstimateStatus;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Partner copy of an estimate (ADR-06). It never carries B2C names, B2C prices, the twin number or the uplift;
 * those are only in {@link InternalEstimateDocument}.
 *
 * @param expired derived (D-29): sent and valid until before today
 */
public record EstimateResponse(UUID id, String estimateNumber, LocalDate estimateDate, LocalDate validUntil,
                               EstimateStatus status, String statusLabel, boolean expired, LocalDate sentOn,
                               EstimateLeadRef lead, PartnerRef partner, BillTo billTo, String billToLabel,
                               BigDecimal discountPercent, boolean igst, BigDecimal feePercent, String notes,
                               List<Line> lines, TotalsResponse totals, Instant createdAt, String createdByName,
                               Instant updatedAt, long version) {

    /**
     * @param amount        quantity × partner rate, before the document discount (source "Amount")
     * @param productActive false when the product was deactivated after the line was saved
     */
    public record Line(UUID id, int lineNo, UUID productId, String sku, boolean productActive, String description,
                       String hsn, String unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal gstPercent,
                       BigDecimal amount) {
    }
}
