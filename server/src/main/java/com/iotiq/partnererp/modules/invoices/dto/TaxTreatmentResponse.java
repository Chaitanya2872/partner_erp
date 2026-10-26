package com.iotiq.partnererp.modules.invoices.dto;

import com.iotiq.partnererp.modules.invoices.tax.TaxType;

import java.time.Instant;
import java.util.List;

/**
 * How the invoice is taxed and whether that still needs a confirmation.
 *
 * @param taxType               null until the supplier state and the place of supply are known
 * @param stateTaxLabel         "SGST" or "UTGST" for intra-state supplies
 * @param missing               fields that must be filled in before issue
 * @param reasons               why an explicit confirmation is needed (empty when it isn't)
 * @param confirmed             a confirmation is recorded and still covers the current inputs
 */
public record TaxTreatmentResponse(TaxType taxType, String taxTypeLabel, boolean utgst, String stateTaxLabel,
                                   List<String> missing, List<Reason> reasons, boolean confirmationRequired,
                                   boolean confirmed, Instant confirmedAt, String confirmedByName,
                                   String confirmationNote) {

    public record Reason(String code, String message) {
    }
}
