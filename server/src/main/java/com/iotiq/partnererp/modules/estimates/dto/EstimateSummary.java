package com.iotiq.partnererp.modules.estimates.dto;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.modules.estimates.entity.EstimateStatus;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Estimate list row (source vEstimates: number, date, client, partner, status, total incl. GST). Partner-safe.
 */
public record EstimateSummary(UUID id, String estimateNumber, LocalDate estimateDate, LocalDate validUntil,
                              EstimateStatus status, String statusLabel, boolean expired, UUID leadId,
                              String leadNumber, String clientName, PartnerRef partner, BillTo billTo,
                              BigDecimal taxableAmount, BigDecimal grandTotal, Instant createdAt, long version) {
}
