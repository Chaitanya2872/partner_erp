package com.iotiq.partnererp.modules.payouts.entity;

import com.iotiq.partnererp.common.persistence.AuditableEntity;
import com.iotiq.partnererp.modules.invoices.entity.Invoice;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import com.iotiq.partnererp.modules.payments.entity.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Referral fee accrued on one payment (source "payouts", id 'po_'+paymentId). A negative fee is a
 * refund clawback / set-off.
 */
@Entity
@Table(name = "partner_payouts")
public class PartnerPayout extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    /** Ex-GST base (the payment's net amount). */
    @Column(name = "base_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "commission_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal commissionPercent;

    /** Fee; negative for a clawback. */
    @Column(name = "gross_commission", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossCommission;

    @Column(name = "tds_amount", precision = 14, scale = 2)
    private BigDecimal tdsAmount;

    @Column(name = "net_payout", precision = 14, scale = 2)
    private BigDecimal netPayout;

    @Column(name = "accrued_on", nullable = false)
    private LocalDate accruedOn;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "tier_payout_days")
    private Short tierPayoutDays;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "utr", length = 100)
    private String utr;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status = PayoutStatus.ACCRUED;

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public Partner getPartner() {
        return partner;
    }

    public void setPartner(Partner partner) {
        this.partner = partner;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public BigDecimal getCommissionPercent() {
        return commissionPercent;
    }

    public void setCommissionPercent(BigDecimal commissionPercent) {
        this.commissionPercent = commissionPercent;
    }

    public BigDecimal getGrossCommission() {
        return grossCommission;
    }

    public void setGrossCommission(BigDecimal grossCommission) {
        this.grossCommission = grossCommission;
    }

    public BigDecimal getTdsAmount() {
        return tdsAmount;
    }

    public void setTdsAmount(BigDecimal tdsAmount) {
        this.tdsAmount = tdsAmount;
    }

    public BigDecimal getNetPayout() {
        return netPayout;
    }

    public void setNetPayout(BigDecimal netPayout) {
        this.netPayout = netPayout;
    }

    public LocalDate getAccruedOn() {
        return accruedOn;
    }

    public void setAccruedOn(LocalDate accruedOn) {
        this.accruedOn = accruedOn;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Short getTierPayoutDays() {
        return tierPayoutDays;
    }

    public void setTierPayoutDays(Short tierPayoutDays) {
        this.tierPayoutDays = tierPayoutDays;
    }

    public LocalDate getPaidDate() {
        return paidDate;
    }

    public void setPaidDate(LocalDate paidDate) {
        this.paidDate = paidDate;
    }

    public String getUtr() {
        return utr;
    }

    public void setUtr(String utr) {
        this.utr = utr;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public void setStatus(PayoutStatus status) {
        this.status = status;
    }
}
