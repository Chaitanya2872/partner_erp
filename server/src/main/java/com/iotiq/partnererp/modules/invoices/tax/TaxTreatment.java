package com.iotiq.partnererp.modules.invoices.tax;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decides the tax type of an invoice from explicit inputs only (Phase 9 decision 1, DECISIONS.md §J).
 *
 * <ul>
 *   <li>The place of supply is entered with its basis; it is never derived from the billing state.</li>
 *   <li>Same state as the supplier's registration → intra-state (CGST + SGST, or UTGST in a union territory without a
 *       legislature); any other state → inter-state (IGST). IGST Act ss. 7–8.</li>
 *   <li>When the inputs leave room for doubt (no billing state, the place of supply differs from the billing state or
 *       the GSTIN's state, an "other" basis, or a different tax type from the source estimate) the treatment needs an
 *       explicit confirmation by a user with Invoices approve before the invoice can be issued.</li>
 * </ul>
 */
public final class TaxTreatment {

    private TaxTreatment() {
    }

    public record Inputs(GstState supplierState, GstState billingState, String billingGstin, GstState placeOfSupply,
                         PlaceOfSupplyBasis basis, Boolean estimateIgst) {
    }

    /** Why a treatment needs confirmation. */
    public record Reason(String code, String message) {
    }

    /**
     * @param taxType     null until the supplier state and place of supply are both known
     * @param missing     what must be filled in before the invoice can be issued
     * @param reasons     why the treatment needs an explicit confirmation (empty when it doesn't)
     * @param key         the inputs a confirmation covers
     */
    public record Result(TaxType taxType, boolean utgst, List<String> missing, List<Reason> reasons, String key) {

        public boolean confirmationRequired() {
            return !reasons.isEmpty();
        }
    }

    public static Result evaluate(Inputs in) {
        List<String> missing = new ArrayList<>();
        if (in.supplierState() == null) {
            missing.add("supplierState");
        }
        if (in.placeOfSupply() == null) {
            missing.add("placeOfSupplyCode");
        }
        if (in.basis() == null) {
            missing.add("placeOfSupplyBasis");
        }
        TaxType type = null;
        boolean utgst = false;
        if (in.supplierState() != null && in.placeOfSupply() != null) {
            type = in.supplierState() == in.placeOfSupply() ? TaxType.INTRA : TaxType.INTER;
            utgst = type == TaxType.INTRA && in.placeOfSupply().isUtgst();
        }

        List<Reason> reasons = new ArrayList<>();
        if (in.placeOfSupply() != null) {
            if (in.billingState() == null) {
                reasons.add(new Reason("BILLING_STATE_MISSING",
                        "The customer's billing state isn't recorded, so the place of supply can't be checked against it."));
            } else if (in.billingState() != in.placeOfSupply()) {
                reasons.add(new Reason("PLACE_OF_SUPPLY_DIFFERS",
                        "The place of supply (" + in.placeOfSupply().label() + ") differs from the billing state ("
                                + in.billingState().label() + ")."));
            }
            if (in.billingGstin() != null) {
                GstState registered = GstState.byCode(in.billingGstin().substring(0, 2)).orElse(null);
                if (registered != null && registered != in.placeOfSupply() && registered != in.billingState()) {
                    reasons.add(new Reason("GSTIN_STATE_DIFFERS", "The customer's GSTIN is registered in "
                            + registered.label() + ", not in the place of supply or billing state."));
                } else if (registered != null && in.billingState() != null && registered != in.billingState()) {
                    reasons.add(new Reason("GSTIN_STATE_DIFFERS", "The customer's GSTIN is registered in "
                            + registered.label() + ", not in the billing state (" + in.billingState().label() + ")."));
                }
            }
        }
        if (in.basis() == PlaceOfSupplyBasis.OTHER) {
            reasons.add(new Reason("OTHER_BASIS", "The place of supply rests on another basis."));
        }
        if (type != null && in.estimateIgst() != null && in.estimateIgst() != (type == TaxType.INTER)) {
            reasons.add(new Reason("ESTIMATE_TAX_TYPE_DIFFERS", "The estimate used "
                    + (in.estimateIgst() ? "IGST" : "CGST + SGST") + ", but the place of supply gives "
                    + (type == TaxType.INTER ? "IGST" : "CGST + SGST") + "."));
        }
        String key = String.join("|", code(in.supplierState()), code(in.billingState()),
                Objects.toString(in.billingGstin(), ""), code(in.placeOfSupply()),
                in.basis() == null ? "" : in.basis().name(), type == null ? "" : type.name(),
                Objects.toString(in.estimateIgst(), ""));
        return new Result(type, utgst, List.copyOf(missing), List.copyOf(reasons), key);
    }

    private static String code(GstState state) {
        return state == null ? "" : state.getCode();
    }
}
