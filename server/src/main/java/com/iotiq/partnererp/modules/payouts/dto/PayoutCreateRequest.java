package com.iotiq.partnererp.modules.payouts.dto;

import java.time.LocalDate;
import java.util.UUID;
/**
 * Generate a payout for one partner from the eligible receipts and carried adjustments up to {@code asOfDate}
 * (defaults to today). The idempotency key makes a resubmitted form return the payout already created.
 */
public record PayoutCreateRequest(UUID partnerId, LocalDate asOfDate, String notes, UUID idempotencyKey) {
}
