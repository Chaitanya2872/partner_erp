package com.iotiq.partnererp.modules.payouts.dto;

import java.time.LocalDate;
/**
 * Confirm that a payout under processing was paid outside the system (no bank integration). The reference is the
 * bank's UTR or transfer reference. {@code acknowledgeReversedSources} confirms paying a payout some of whose receipts
 * were reversed after approval; their commission becomes negative adjustments carried into the next payout.
 */
public record PayoutMarkPaidRequest(Long version, LocalDate paidDate, String reference,
                                    Boolean acknowledgeReversedSources) {
}
