package com.iotiq.partnererp.modules.invoices.dto;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.modules.invoices.tax.PlaceOfSupplyBasis;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Create or replace a draft invoice, or preview one. Only inputs are accepted; tax and totals are computed on the
 * server. Issued and cancelled invoices can't be replaced.
 *
 * <ul>
 *   <li>Direct invoices (no estimate) need a live lead that isn't Lost; the partner comes from the lead (BR-V1).</li>
 *   <li>A draft converted from an estimate keeps the estimate's commercial values: {@code leadId}, {@code billTo},
 *       {@code discountPercent}, {@code feePercent} and {@code lines} must be left out (or equal the stored values).
 *       Billing details, place of supply, due days and notes can change.</li>
 *   <li>The place of supply and its basis are entered explicitly; the billing state never implies them.</li>
 *   <li>A line GST rate other than the product's (or the estimate's) needs {@code INVOICES:APPROVE}.</li>
 *   <li>{@code version} is required on update.</li>
 * </ul>
 */
public record InvoiceRequest(UUID leadId, BillTo billTo, BigDecimal discountPercent, BigDecimal feePercent,
                             String feeOverrideReason, String notes, Integer dueDays, String billingName,
                             String billingAddress, String billingStateCode, String billingGstin,
                             String placeOfSupplyCode, PlaceOfSupplyBasis placeOfSupplyBasis, List<Line> lines,
                             Long version) {

    public record Line(UUID id, UUID productId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal gstPercent) {
    }
}
