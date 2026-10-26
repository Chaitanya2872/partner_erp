package com.iotiq.partnererp.modules.payouts.entity;

import com.iotiq.partnererp.common.persistence.AuditableEntity;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A partner payout: one batch of commission lines and adjustments for one partner (Phase 11).
 * Amounts are frozen on approval; finished payouts never change ({@code payouts_guard}).
 */
@Entity
@Table(name = "payouts")
public class Payout extends AuditableEntity {

    @Column(name = "payout_number", length = 20)
    private String payoutNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status;

    @Column(name = "legacy", nullable = false)
    private boolean legacy;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "fy_start", nullable = false)
    private LocalDate fyStart;

    @Column(name = "commission_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal commissionTotal;

    @Column(name = "tds_status", nullable = false, length = 30)
    private String tdsStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tds_configuration_id")
    private TdsConfiguration tdsConfiguration;

    @Column(name = "tds_section", length = 20)
    private String tdsSection;

    @Column(name = "tds_rate", precision = 6, scale = 3)
    private BigDecimal tdsRate;

    @Column(name = "tds_base", precision = 14, scale = 2)
    private BigDecimal tdsBase;

    @Column(name = "tds_amount", precision = 14, scale = 2)
    private BigDecimal tdsAmount;

    @Column(name = "tds_adjusted", nullable = false)
    private boolean tdsAdjusted;

    @Column(name = "net_amount", precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approval_note", length = 500)
    private String approvalNote;

    @Column(name = "processing_at")
    private Instant processingAt;

    @Column(name = "processing_by")
    private UUID processingBy;

    @Column(name = "bank_account_last4", length = 4)
    private String bankAccountLast4;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "idempotency_key", updatable = false)
    private UUID idempotencyKey;

    @Column(name = "request_hash", length = 64, updatable = false)
    private String requestHash;

    public String getPayoutNumber() {
        return payoutNumber;
    }

    public void setPayoutNumber(String payoutNumber) {
        this.payoutNumber = payoutNumber;
    }

    public Partner getPartner() {
        return partner;
    }

    public void setPartner(Partner partner) {
        this.partner = partner;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public void setStatus(PayoutStatus status) {
        this.status = status;
    }

    public boolean isLegacy() {
        return legacy;
    }

    public void setLegacy(boolean legacy) {
        this.legacy = legacy;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }

    public LocalDate getFyStart() {
        return fyStart;
    }

    public void setFyStart(LocalDate fyStart) {
        this.fyStart = fyStart;
    }

    public BigDecimal getCommissionTotal() {
        return commissionTotal;
    }

    public void setCommissionTotal(BigDecimal commissionTotal) {
        this.commissionTotal = commissionTotal;
    }

    public String getTdsStatus() {
        return tdsStatus;
    }

    public void setTdsStatus(String tdsStatus) {
        this.tdsStatus = tdsStatus;
    }

    public TdsConfiguration getTdsConfiguration() {
        return tdsConfiguration;
    }

    public void setTdsConfiguration(TdsConfiguration tdsConfiguration) {
        this.tdsConfiguration = tdsConfiguration;
    }

    public String getTdsSection() {
        return tdsSection;
    }

    public void setTdsSection(String tdsSection) {
        this.tdsSection = tdsSection;
    }

    public BigDecimal getTdsRate() {
        return tdsRate;
    }

    public void setTdsRate(BigDecimal tdsRate) {
        this.tdsRate = tdsRate;
    }

    public BigDecimal getTdsBase() {
        return tdsBase;
    }

    public void setTdsBase(BigDecimal tdsBase) {
        this.tdsBase = tdsBase;
    }

    public BigDecimal getTdsAmount() {
        return tdsAmount;
    }

    public void setTdsAmount(BigDecimal tdsAmount) {
        this.tdsAmount = tdsAmount;
    }

    public boolean isTdsAdjusted() {
        return tdsAdjusted;
    }

    public void setTdsAdjusted(boolean tdsAdjusted) {
        this.tdsAdjusted = tdsAdjusted;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(UUID submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getApprovalNote() {
        return approvalNote;
    }

    public void setApprovalNote(String approvalNote) {
        this.approvalNote = approvalNote;
    }

    public Instant getProcessingAt() {
        return processingAt;
    }

    public void setProcessingAt(Instant processingAt) {
        this.processingAt = processingAt;
    }

    public UUID getProcessingBy() {
        return processingBy;
    }

    public void setProcessingBy(UUID processingBy) {
        this.processingBy = processingBy;
    }

    public String getBankAccountLast4() {
        return bankAccountLast4;
    }

    public void setBankAccountLast4(String bankAccountLast4) {
        this.bankAccountLast4 = bankAccountLast4;
    }

    public LocalDate getPaidDate() {
        return paidDate;
    }

    public void setPaidDate(LocalDate paidDate) {
        this.paidDate = paidDate;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public UUID getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(UUID paidBy) {
        this.paidBy = paidBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public UUID getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(UUID closedBy) {
        this.closedBy = closedBy;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(UUID idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }
}
