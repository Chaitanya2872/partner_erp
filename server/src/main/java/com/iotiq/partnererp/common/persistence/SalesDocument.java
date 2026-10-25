package com.iotiq.partnererp.common.persistence;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.modules.leads.entity.Lead;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

import java.math.BigDecimal;

/**
 * Header fields shared by estimates and invoices (source edSave document shape).
 */
@MappedSuperclass
public abstract class SalesDocument extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    /** Required when a document is created; becomes null only if the lead is later deleted (D-26 open). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    /** Document-level discount applied to every line. */
    @Column(name = "discount_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /** true = IGST (other state); false = CGST + SGST. */
    @Column(name = "igst", nullable = false)
    private boolean igst;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_to", nullable = false, length = 10)
    private BillTo billTo = BillTo.CLIENT;

    /** Referral fee % snapshot for this document. */
    @Column(name = "fee_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal feePercent = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    /** Partner-price totals (source cpT). */
    @Embedded
    private DocumentTotals totals = new DocumentTotals();

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

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(BigDecimal discountPercent) {
        this.discountPercent = discountPercent;
    }

    public boolean isIgst() {
        return igst;
    }

    public void setIgst(boolean igst) {
        this.igst = igst;
    }

    public BillTo getBillTo() {
        return billTo;
    }

    public void setBillTo(BillTo billTo) {
        this.billTo = billTo;
    }

    public BigDecimal getFeePercent() {
        return feePercent;
    }

    public void setFeePercent(BigDecimal feePercent) {
        this.feePercent = feePercent;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public DocumentTotals getTotals() {
        return totals;
    }

    public void setTotals(DocumentTotals totals) {
        this.totals = totals;
    }
}
