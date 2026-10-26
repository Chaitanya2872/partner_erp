package com.iotiq.partnererp.modules.invoices.entity;

import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.common.persistence.SalesDocument;
import com.iotiq.partnererp.modules.estimates.entity.Estimate;
import com.iotiq.partnererp.modules.invoices.tax.PlaceOfSupplyBasis;
import com.iotiq.partnererp.modules.invoices.tax.TaxType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Partner-price invoice (source "invoices"), Phase 9 lifecycle DRAFT → ISSUED → CANCELLED.
 *
 * <p>A draft has no number and can change. Issuing assigns {@code INV-YYYY-NNNNNN} and freezes the supplier, the
 * customer billing details, the place of supply, the tax treatment and every line's tax snapshot; the database
 * refuses later changes as well (V16 triggers). The payment status is derived from payments, never stored (ADR-08).
 */
@Entity
@Table(name = "invoices")
public class Invoice extends SalesDocument {

    /** Null while the invoice is a draft. */
    @Column(name = "invoice_number", length = 30)
    private String invoiceNumber;

    /** Source estimate when converted (source estimateId). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estimate_id")
    private Estimate estimate;

    /** The issue date; null while the invoice is a draft. */
    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "issued_by")
    private UUID issuedBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    /** Payment terms in days; the due date is the issue date plus these (smallint column). */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "due_days")
    private Integer dueDays;

    @Column(name = "source_estimate_status", length = 20)
    private String sourceEstimateStatus;

    @Column(name = "supplier_name", length = 200)
    private String supplierName;

    @Column(name = "supplier_address")
    private String supplierAddress;

    @Column(name = "supplier_gstin", length = 15)
    private String supplierGstin;

    @Column(name = "supplier_state_code", length = 2)
    private String supplierStateCode;

    @Column(name = "billing_name", length = 200)
    private String billingName;

    @Column(name = "billing_address")
    private String billingAddress;

    @Column(name = "billing_state_code", length = 2)
    private String billingStateCode;

    @Column(name = "billing_gstin", length = 15)
    private String billingGstin;

    @Column(name = "place_of_supply_code", length = 2)
    private String placeOfSupplyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_of_supply_basis", length = 30)
    private PlaceOfSupplyBasis placeOfSupplyBasis;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type", length = 5)
    private TaxType taxType;

    @Column(name = "utgst", nullable = false)
    private boolean utgst;

    @Column(name = "tax_confirmed_at")
    private Instant taxConfirmedAt;

    @Column(name = "tax_confirmed_by")
    private UUID taxConfirmedBy;

    @Column(name = "tax_confirmation_note", length = 500)
    private String taxConfirmationNote;

    @Column(name = "tax_confirmation_key", length = 300)
    private String taxConfirmationKey;

    @Column(name = "cgst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(name = "sgst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(name = "igst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<InvoiceItem> items = new ArrayList<>();

    public void addItem(InvoiceItem item) {
        item.setInvoice(this);
        items.add(item);
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public Estimate getEstimate() {
        return estimate;
    }

    public void setEstimate(Estimate estimate) {
        this.estimate = estimate;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(UUID cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public UUID getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(UUID issuedBy) {
        this.issuedBy = issuedBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(UUID deletedBy) {
        this.deletedBy = deletedBy;
    }

    public Integer getDueDays() {
        return dueDays;
    }

    public void setDueDays(Integer dueDays) {
        this.dueDays = dueDays;
    }

    public String getSourceEstimateStatus() {
        return sourceEstimateStatus;
    }

    public void setSourceEstimateStatus(String sourceEstimateStatus) {
        this.sourceEstimateStatus = sourceEstimateStatus;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getSupplierAddress() {
        return supplierAddress;
    }

    public void setSupplierAddress(String supplierAddress) {
        this.supplierAddress = supplierAddress;
    }

    public String getSupplierGstin() {
        return supplierGstin;
    }

    public void setSupplierGstin(String supplierGstin) {
        this.supplierGstin = supplierGstin;
    }

    public String getSupplierStateCode() {
        return supplierStateCode;
    }

    public void setSupplierStateCode(String supplierStateCode) {
        this.supplierStateCode = supplierStateCode;
    }

    public String getBillingName() {
        return billingName;
    }

    public void setBillingName(String billingName) {
        this.billingName = billingName;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(String billingAddress) {
        this.billingAddress = billingAddress;
    }

    public String getBillingStateCode() {
        return billingStateCode;
    }

    public void setBillingStateCode(String billingStateCode) {
        this.billingStateCode = billingStateCode;
    }

    public String getBillingGstin() {
        return billingGstin;
    }

    public void setBillingGstin(String billingGstin) {
        this.billingGstin = billingGstin;
    }

    public String getPlaceOfSupplyCode() {
        return placeOfSupplyCode;
    }

    public void setPlaceOfSupplyCode(String placeOfSupplyCode) {
        this.placeOfSupplyCode = placeOfSupplyCode;
    }

    public PlaceOfSupplyBasis getPlaceOfSupplyBasis() {
        return placeOfSupplyBasis;
    }

    public void setPlaceOfSupplyBasis(PlaceOfSupplyBasis placeOfSupplyBasis) {
        this.placeOfSupplyBasis = placeOfSupplyBasis;
    }

    public TaxType getTaxType() {
        return taxType;
    }

    public void setTaxType(TaxType taxType) {
        this.taxType = taxType;
    }

    public boolean isUtgst() {
        return utgst;
    }

    public void setUtgst(boolean utgst) {
        this.utgst = utgst;
    }

    public Instant getTaxConfirmedAt() {
        return taxConfirmedAt;
    }

    public void setTaxConfirmedAt(Instant taxConfirmedAt) {
        this.taxConfirmedAt = taxConfirmedAt;
    }

    public UUID getTaxConfirmedBy() {
        return taxConfirmedBy;
    }

    public void setTaxConfirmedBy(UUID taxConfirmedBy) {
        this.taxConfirmedBy = taxConfirmedBy;
    }

    public String getTaxConfirmationNote() {
        return taxConfirmationNote;
    }

    public void setTaxConfirmationNote(String taxConfirmationNote) {
        this.taxConfirmationNote = taxConfirmationNote;
    }

    public String getTaxConfirmationKey() {
        return taxConfirmationKey;
    }

    public void setTaxConfirmationKey(String taxConfirmationKey) {
        this.taxConfirmationKey = taxConfirmationKey;
    }

    public BigDecimal getCgstAmount() {
        return cgstAmount;
    }

    public void setCgstAmount(BigDecimal cgstAmount) {
        this.cgstAmount = cgstAmount;
    }

    public BigDecimal getSgstAmount() {
        return sgstAmount;
    }

    public void setSgstAmount(BigDecimal sgstAmount) {
        this.sgstAmount = sgstAmount;
    }

    public BigDecimal getIgstAmount() {
        return igstAmount;
    }

    public void setIgstAmount(BigDecimal igstAmount) {
        this.igstAmount = igstAmount;
    }

    public List<InvoiceItem> getItems() {
        return items;
    }
}
