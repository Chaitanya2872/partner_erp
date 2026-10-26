package com.iotiq.partnererp.modules.payouts.dto;
/**
 * A payout transition. {@code version} is the payout version the caller saw. {@code reason} is required to reject,
 * cancel or fail a payout; {@code note} is an optional approval or processing note.
 */
public record PayoutActionRequest(Long version, String reason, String note) {
}
