package com.iotiq.partnererp.modules.payouts.dto;

import java.math.BigDecimal;
import java.util.UUID;
/** A manual partner adjustment (positive or negative) with its reason; a second person approves it. */
public record AdjustmentRequest(UUID partnerId, BigDecimal amount, String reason, UUID idempotencyKey) {
}
