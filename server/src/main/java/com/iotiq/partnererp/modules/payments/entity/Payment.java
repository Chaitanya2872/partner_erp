package com.iotiq.partnererp.modules.payments.entity;

import com.iotiq.partnererp.common.persistence.CreatedAuditEntity;
import com.iotiq.partnererp.modules.invoices.entity.Invoice;
import com.iotiq.partnererp.modules.leads.entity.Lead;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * One entry of the payment ledger (source "payments"): money received against an issued invoice, or the reversal of
 * such a receipt. Entries are append-only (V17 trigger; no code path updates them): a reversal is a new negative entry pointing at the receipt,
 * so the original stays as it was recorded. Refunds (negative amounts) exist only for rows carried over from before
 * Phase 10.
 */
@Entity
@Table(name = "payments")
public class Payment extends CreatedAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /** Amount including GST; negative for a refund. */
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Ex-GST realised amount: amount x invoice taxable / invoice total (source net). */
    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "is_refund", nullable = false)
    private boolean refund;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    /** PAY-YYYY-NNNNNN; null only for rows recorded before Phase 10. */
    @Column(name = "payment_number", length = 20)
    private String paymentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private PaymentEntryType entryType = PaymentEntryType.RECEIPT;

    /** For a reversal: the receipt it reverses. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_payment_id")
    private Payment reversedPayment;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @Column(name = "notes", length = 500)
    private String notes;

    /** Client-generated key of the recording request; a resubmission returns the same entry. */
    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    /** SHA-256 of the recorded inputs, to tell a resubmission from a reuse of the key for different inputs. */
    @Column(name = "request_hash", length = 64)
    private String requestHash;

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public Partner getPartner() {
        return partner;
    }

    public void setPartner(Partner partner) {
        this.partner = partner;
    }

    public Lead getLead() {
        return lead;
    }

    public void setLead(Lead lead) {
        this.lead = lead;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public boolean isRefund() {
        return refund;
    }

    public void setRefund(boolean refund) {
        this.refund = refund;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getPaymentNumber() {
        return paymentNumber;
    }

    public void setPaymentNumber(String paymentNumber) {
        this.paymentNumber = paymentNumber;
    }

    public PaymentEntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(PaymentEntryType entryType) {
        this.entryType = entryType;
    }

    public Payment getReversedPayment() {
        return reversedPayment;
    }

    public void setReversedPayment(Payment reversedPayment) {
        this.reversedPayment = reversedPayment;
    }

    public String getReversalReason() {
        return reversalReason;
    }

    public void setReversalReason(String reversalReason) {
        this.reversalReason = reversalReason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
