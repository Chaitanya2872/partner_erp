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

/**
 * Money received against an invoice (source "payments"). Refunds are stored as negative amounts.
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
}
