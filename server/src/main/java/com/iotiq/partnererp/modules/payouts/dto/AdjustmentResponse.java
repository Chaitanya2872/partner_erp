package com.iotiq.partnererp.modules.payouts.dto;

import com.iotiq.partnererp.modules.leads.dto.PartnerRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
/**
 * A partner adjustment.
 *
 * @param allocatedTo the live payout it is allocated to, if any
 */
public record AdjustmentResponse(UUID id, String adjustmentNumber, PartnerRef partner, String kind, String kindLabel,
                                 BigDecimal amount, String reason, String status, String statusLabel,
                                 UUID sourcePaymentId, String sourcePaymentNumber, Instant createdAt,
                                 String createdByName, Instant decidedAt, String decidedByName, String decisionNote,
                                 PayoutRef allocatedTo, boolean canDecide) {

    public record PayoutRef(UUID id, String payoutNumber, String status) {
    }
}
