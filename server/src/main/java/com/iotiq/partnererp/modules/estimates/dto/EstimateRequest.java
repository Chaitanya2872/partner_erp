package com.iotiq.partnererp.modules.estimates.dto;

import com.iotiq.partnererp.common.enums.BillTo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Create, replace or preview an estimate. Only inputs are accepted; every total is computed on the server.
 *
 * <ul>
 *   <li>The partner comes from the lead (BR-E1). New estimates need a live lead that isn't Lost (BR-E5).</li>
 *   <li>Blank values default as in the source (BR-E2): date today, valid until = date + validity days, discount 0,
 *       CGST + SGST, client buys.</li>
 *   <li>{@code feePercent} null keeps the stored fee (or uses the default for a new estimate, a changed lead or a
 *       changed buyer). A fee other than the default needs {@code PARTNERS:APPROVE} and {@code feeOverrideReason}
 *       (D-13). Partner purchases carry no fee.</li>
 *   <li>A line with the {@code id} of one of this estimate's lines keeps its saved product snapshot (names, HSN,
 *       unit, rate, GST, B2C rate); only the quantity changes. A line without one copies the product as it is
 *       now (§6.2). Rate and GST of product lines always come from the product sheet; {@code unitPrice} and
 *       {@code gstPercent} are optional and ignored for them. Product lines without a product are dropped, as in the source.</li>
 *   <li>{@code kind} null or PRODUCT = a price-sheet line. INSTALLATION = a free-text priced service line:
 *       no product, {@code description} (required), {@code unit} (default "job"), quantity, rate and GST. A saved
 *       installation line is fully editable.</li>
 *   <li>{@code revisionReason}: why a sent, accepted or rejected estimate is being revised. Required (at least 10
 *       characters) on any update that changes it once it has left DRAFT; kept in its history.</li>
 *   <li>{@code version} is required on update.</li>
 * </ul>
 */
public record EstimateRequest(UUID leadId, LocalDate estimateDate, LocalDate validUntil, BigDecimal discountPercent,
                              Boolean igst, BillTo billTo, BigDecimal feePercent, String feeOverrideReason,
                              String notes, List<Line> lines, String revisionReason, Long version) {

    public record Line(UUID id, String kind, UUID productId, String description, String unit, BigDecimal quantity,
                       BigDecimal unitPrice, BigDecimal gstPercent) {
    }
}
