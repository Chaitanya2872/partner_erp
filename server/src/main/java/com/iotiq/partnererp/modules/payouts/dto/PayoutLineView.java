package com.iotiq.partnererp.modules.payouts.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
/**
 * One allocation of a payout (or a preview line, without an id).
 *
 * @param sourceReversed the line's receipt was reversed after it was allocated
 */
public record PayoutLineView(UUID id, String lineType, UUID paymentId, String paymentNumber, UUID invoiceId,
                             String invoiceNumber, LocalDate paymentDate, BigDecimal baseAmount,
                             BigDecimal commissionPercent, BigDecimal amount, UUID adjustmentId,
                             String adjustmentNumber, String adjustmentKind, String adjustmentReason,
                             boolean sourceReversed, Instant releasedAt, String releaseReason) {
}
