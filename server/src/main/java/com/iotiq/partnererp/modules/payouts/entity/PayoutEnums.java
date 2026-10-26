package com.iotiq.partnererp.modules.payouts.entity;

/**
 * Small value types of the payout module.
 */
public final class PayoutEnums {

    private PayoutEnums() {
    }

    /** COMMISSION: a receipt's commission; ADJUSTMENT: an approved partner adjustment carried into the payout. */
    public enum LineType { COMMISSION, ADJUSTMENT }

    /** Why a line left its payout; the receipt or adjustment is free for another payout afterwards. */
    public enum ReleaseReason { RECALCULATED, REJECTED, CANCELLED, FAILED }

    public enum AdjustmentKind {
        REVERSAL("Payment reversal"),
        MANUAL("Manual");

        private final String label;

        AdjustmentKind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum AdjustmentStatus {
        PENDING_APPROVAL("Pending approval"),
        APPROVED("Approved"),
        REJECTED("Rejected");

        private final String label;

        AdjustmentStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum TdsConfigStatus { DRAFT, CONFIRMED }

    /** What caused a TDS calculation to be recorded. */
    public enum TdsEvent { GENERATED, RECALCULATED, ADJUSTED, APPROVED, LEGACY }
}
