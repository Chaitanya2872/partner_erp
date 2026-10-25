package com.iotiq.partnererp.modules.b2c.entity;

import com.iotiq.partnererp.common.persistence.BaseEntity;
import com.iotiq.partnererp.common.persistence.DocumentTotals;
import com.iotiq.partnererp.modules.estimates.entity.Estimate;
import com.iotiq.partnererp.modules.invoices.entity.Invoice;
import com.iotiq.partnererp.modules.leads.entity.Lead;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal-only B2C twin of an estimate or invoice (source saveDoc, number "INT-" + parent number).
 * Never exposed to partner-facing APIs (ADR-06).
 */
@Entity
@Table(name = "b2c_documents")
@EntityListeners(AuditingEntityListener.class)
public class B2CDocument extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 10)
    private B2CDocumentType documentType;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estimate_id", unique = true)
    private Estimate estimate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", unique = true)
    private Invoice invoice;

    @Column(name = "internal_number", nullable = false, length = 40)
    private String internalNumber;

    @Column(name = "parent_number", nullable = false, length = 30)
    private String parentNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "discount_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "igst", nullable = false)
    private boolean igst;

    /** Partner-price totals of the parent (source twin.cpT). */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "subtotal", column = @Column(name = "partner_subtotal")),
            @AttributeOverride(name = "discountAmount", column = @Column(name = "partner_discount_amount")),
            @AttributeOverride(name = "taxableAmount", column = @Column(name = "partner_taxable_amount")),
            @AttributeOverride(name = "gstAmount", column = @Column(name = "partner_gst_amount")),
            @AttributeOverride(name = "grandTotal", column = @Column(name = "partner_grand_total"))
    })
    private DocumentTotals partnerTotals = new DocumentTotals();

    /** B2C-price totals (source twin.t). */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "subtotal", column = @Column(name = "b2c_subtotal")),
            @AttributeOverride(name = "discountAmount", column = @Column(name = "b2c_discount_amount")),
            @AttributeOverride(name = "taxableAmount", column = @Column(name = "b2c_taxable_amount")),
            @AttributeOverride(name = "gstAmount", column = @Column(name = "b2c_gst_amount")),
            @AttributeOverride(name = "grandTotal", column = @Column(name = "b2c_grand_total"))
    })
    private DocumentTotals b2cTotals = new DocumentTotals();

    /** B2C taxable minus partner taxable ("Discount vs B2C list"). */
    @Column(name = "uplift", nullable = false, precision = 14, scale = 2)
    private BigDecimal uplift = BigDecimal.ZERO;

    /** At least one line had no B2C price, so comparisons are incomplete (SRS FR-09). */
    @Column(name = "b2c_incomplete", nullable = false)
    private boolean b2cIncomplete;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<B2CDocumentItem> items = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void addItem(B2CDocumentItem item) {
        item.setDocument(this);
        items.add(item);
    }

    public B2CDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(B2CDocumentType documentType) {
        this.documentType = documentType;
    }

    public Estimate getEstimate() {
        return estimate;
    }

    public void setEstimate(Estimate estimate) {
        this.estimate = estimate;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public String getInternalNumber() {
        return internalNumber;
    }

    public void setInternalNumber(String internalNumber) {
        this.internalNumber = internalNumber;
    }

    public String getParentNumber() {
        return parentNumber;
    }

    public void setParentNumber(String parentNumber) {
        this.parentNumber = parentNumber;
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

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public void setDocumentDate(LocalDate documentDate) {
        this.documentDate = documentDate;
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

    public DocumentTotals getPartnerTotals() {
        return partnerTotals;
    }

    public void setPartnerTotals(DocumentTotals partnerTotals) {
        this.partnerTotals = partnerTotals;
    }

    public DocumentTotals getB2cTotals() {
        return b2cTotals;
    }

    public void setB2cTotals(DocumentTotals b2cTotals) {
        this.b2cTotals = b2cTotals;
    }

    public BigDecimal getUplift() {
        return uplift;
    }

    public void setUplift(BigDecimal uplift) {
        this.uplift = uplift;
    }

    public boolean isB2cIncomplete() {
        return b2cIncomplete;
    }

    public void setB2cIncomplete(boolean b2cIncomplete) {
        this.b2cIncomplete = b2cIncomplete;
    }

    public List<B2CDocumentItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
