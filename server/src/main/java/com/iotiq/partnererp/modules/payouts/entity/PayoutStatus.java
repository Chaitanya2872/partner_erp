package com.iotiq.partnererp.modules.payouts.entity;

import java.util.Map;
import java.util.Set;

/**
 * Payout lifecycle (Phase 11, DECISIONS.md §L). Transitions are enforced here and by the {@code payouts_guard} trigger.
 */
public enum PayoutStatus {

    DRAFT("Draft"),
    PENDING_APPROVAL("Pending approval"),
    APPROVED("Approved"),
    PROCESSING("Processing"),
    PAID("Paid"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled"),
    FAILED("Failed");

    private static final Map<PayoutStatus, Set<PayoutStatus>> NEXT = Map.of(
            DRAFT, Set.of(PENDING_APPROVAL, CANCELLED),
            PENDING_APPROVAL, Set.of(APPROVED, REJECTED, CANCELLED),
            APPROVED, Set.of(PROCESSING, CANCELLED),
            PROCESSING, Set.of(PAID, FAILED));

    private final String label;

    PayoutStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean canMoveTo(PayoutStatus next) {
        return NEXT.getOrDefault(this, Set.of()).contains(next);
    }

    /** Draft or pending approval: lines can still be recalculated. */
    public boolean isOpen() {
        return this == DRAFT || this == PENDING_APPROVAL;
    }

    /** Paid, rejected, cancelled or failed: never changes again. */
    public boolean isFinal() {
        return this == PAID || this == REJECTED || this == CANCELLED || this == FAILED;
    }

    /** Holds its lines: every status except rejected, cancelled and failed. */
    public boolean isLive() {
        return this != REJECTED && this != CANCELLED && this != FAILED;
    }
}
