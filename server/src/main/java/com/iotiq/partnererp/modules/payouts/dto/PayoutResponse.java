package com.iotiq.partnererp.modules.payouts.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
/**
 * A payout with its lines, TDS calculation history and workflow. Bank account and PAN are masked unless the caller
 * may see partner sensitive data.
 *
 * @param reversedSources   payment numbers of active receipts reversed since they were allocated
 * @param blockers          why the next step can't be taken yet
 * @param actions           what the caller may do now
 */
public record PayoutResponse(UUID id, String payoutNumber, String status, String statusLabel, boolean legacy,
                             PartnerInfo partner, LocalDate asOfDate, LocalDate fyStart, String fyLabel,
                             BigDecimal receiptsTotal, BigDecimal adjustmentsTotal, BigDecimal commissionTotal,
                             PayoutTds tds, BigDecimal netAmount, String notes, List<PayoutLineView> lines,
                             List<PayoutLineView> releasedLines, List<Calculation> calculations,
                             Workflow workflow, List<String> reversedSources, List<String> blockers, Actions actions,
                             long version) {

    public record PartnerInfo(UUID id, String name, String partnerCode, boolean panOnFile, String pan,
                              boolean panVerified, String bankName, String bankAccount, String ifsc,
                              boolean bankOnFile, boolean sensitiveVisible) {
    }

    public record Workflow(Instant createdAt, String createdByName, Instant submittedAt, String submittedByName,
                           Instant approvedAt, String approvedByName, String approvalNote, Instant processingAt,
                           String processingByName, String bankAccountLast4, LocalDate paidDate,
                           String paymentReference, Instant paidAt, String paidByName, Instant closedAt,
                           String closedByName, String statusReason) {
    }

    public record Calculation(UUID id, String event, String status, String sectionCode, BigDecimal ratePercent,
                              BigDecimal fyThreshold, String fyLabel, boolean panVerified,
                              BigDecimal priorFyCommission, BigDecimal priorFyTdsBase, BigDecimal commissionTotal,
                              BigDecimal tdsBase, BigDecimal computedTds, BigDecimal tdsAmount,
                              String adjustmentReason, Instant createdAt, String createdByName) {
    }

    public record Actions(boolean recalculate, boolean adjustTds, boolean submit, boolean approve, boolean reject,
                          boolean cancel, boolean startProcessing, boolean markPaid, boolean fail) {
    }
}
