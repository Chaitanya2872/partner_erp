package com.iotiq.partnererp.modules.payouts.entity;

import com.iotiq.partnererp.common.persistence.CreatedAuditEntity;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import com.iotiq.partnererp.modules.payments.entity.Payment;
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
import java.util.UUID;

/**
 * A partner adjustment carried into the next payout: a negative REVERSAL (a receipt reversed after its commission
 * was paid) or a MANUAL adjustment approved by a second person.
 */
@Entity
@Table(name = "partner_adjustments")
public class PartnerAdjustment extends CreatedAuditEntity {

    @Column(name = "adjustment_number", nullable = false, length = 20)
    private String adjustmentNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private PayoutEnums.AdjustmentKind kind;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutEnums.AdjustmentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_payment_id")
    private Payment sourcePayment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_line_id")
    private PayoutLine sourceLine;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "idempotency_key", updatable = false)
    private UUID idempotencyKey;

    public String getAdjustmentNumber() {
        return adjustmentNumber;
    }

    public void setAdjustmentNumber(String adjustmentNumber) {
        this.adjustmentNumber = adjustmentNumber;
    }

    public Partner getPartner() {
        return partner;
    }

    public void setPartner(Partner partner) {
        this.partner = partner;
    }

    public PayoutEnums.AdjustmentKind getKind() {
        return kind;
    }

    public void setKind(PayoutEnums.AdjustmentKind kind) {
        this.kind = kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public PayoutEnums.AdjustmentStatus getStatus() {
        return status;
    }

    public void setStatus(PayoutEnums.AdjustmentStatus status) {
        this.status = status;
    }

    public Payment getSourcePayment() {
        return sourcePayment;
    }

    public void setSourcePayment(Payment sourcePayment) {
        this.sourcePayment = sourcePayment;
    }

    public PayoutLine getSourceLine() {
        return sourceLine;
    }

    public void setSourceLine(PayoutLine sourceLine) {
        this.sourceLine = sourceLine;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(UUID decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(UUID idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
