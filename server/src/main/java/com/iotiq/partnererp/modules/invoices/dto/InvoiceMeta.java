package com.iotiq.partnererp.modules.invoices.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Options and defaults for invoice forms and filters.
 *
 * @param canOverrideFee     a fee other than the default needs {@code PARTNERS:APPROVE} (D-13)
 * @param canApprove         {@code INVOICES:APPROVE}: confirm a tax treatment, change a line's GST rate
 * @param canCancel          {@code INVOICES:DELETE}: cancel an issued invoice or discard a draft
 * @param supplierComplete   the supplier GST registration is complete, so invoices can be issued
 */
public record InvoiceMeta(List<Option> statuses, List<Option> paymentStatuses, List<Option> billTo,
                          List<State> states, List<Option> placeOfSupplyBases, int dueDays, String terms,
                          BigDecimal defaultGstPercent, int maxLines, boolean canOverrideFee, boolean canApprove,
                          boolean canCancel, boolean supplierComplete, String supplierStateCode) {

    public record Option(String value, String label) {
    }

    public record State(String code, String name, boolean utgst) {
    }
}
