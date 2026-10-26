package com.iotiq.partnererp.modules.invoices.dto;

/**
 * Body of the lifecycle actions. {@code version} guards against acting on a stale copy.
 *
 * @param expectedGrandTotal issue: the total the user reviewed; refused when the server total differs
 * @param note               confirm-tax: why the treatment is right (required)
 * @param reason             cancel: why the invoice is cancelled (required)
 * @param regularSupplyConfirmed issue: the user confirms this is a regular domestic taxable supply, not one of the
 *                           unsupported kinds (export, SEZ, reverse charge, composition, cess, exempt); required
 */
public record InvoiceActionRequest(Long version, java.math.BigDecimal expectedGrandTotal, String note, String reason,
                                   Boolean regularSupplyConfirmed) {
}
