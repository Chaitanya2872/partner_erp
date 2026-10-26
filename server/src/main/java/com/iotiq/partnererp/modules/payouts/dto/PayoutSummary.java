package com.iotiq.partnererp.modules.payouts.dto;

import com.iotiq.partnererp.modules.leads.dto.PartnerRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
/**
 * One payout in the list.
 *
 * @param blocked an active receipt of the payout was reversed: recalculate a draft or pending payout; an approved or
 *                processing one can't be paid without acknowledging it
 */
public record PayoutSummary(UUID id, String payoutNumber, PartnerRef partner, String status, String statusLabel,
                            boolean legacy, LocalDate asOfDate, String fyLabel, BigDecimal commissionTotal,
                            String tdsStatus, BigDecimal tdsAmount, BigDecimal netAmount, LocalDate paidDate,
                            boolean blocked, Instant createdAt) {
}
