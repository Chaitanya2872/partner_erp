package com.iotiq.partnererp.modules.invoices.dto;

/**
 * Body of the lifecycle actions. {@code version} guards against acting on a stale copy.
 *
 * @param expectedGrandTotal issue: the total the user reviewed; refused when the server total differs
 * @param note               confirm-tax: why the treatment is right (required)
 * @param reason             cancel: why the invoice is cancelled (required)
 */
public record InvoiceActionRequest(Long version, java.math.BigDecimal expectedGrandTotal, String note, String reason) {
}
