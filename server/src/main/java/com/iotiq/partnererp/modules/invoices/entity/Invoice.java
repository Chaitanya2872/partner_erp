package com.iotiq.partnererp.modules.invoices.entity;

import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.common.persistence.SalesDocument;
import com.iotiq.partnererp.modules.estimates.entity.Estimate;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Partner-price invoice (source "invoices"). Only the lifecycle (OPEN/CANCELLED) is stored; the
 * payment status is derived from payments and the due date (ADR-08).
 */
@Entity
@Table(name = "invoices")
public class Invoice extends SalesDocument {

    @Column(name = "invoice_number", nullable = false, length = 30, updatable = false)
    private String invoiceNumber;

    /** Source estimate when converted (source estimateId). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estimate_id")
    private Estimate estimate;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.OPEN;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

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

    public List<InvoiceItem> getItems() {
        return items;
    }
}
