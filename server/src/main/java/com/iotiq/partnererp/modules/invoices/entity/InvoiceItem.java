package com.iotiq.partnererp.modules.invoices.entity;

import com.iotiq.partnererp.common.persistence.DocumentLine;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoice_items")
public class InvoiceItem extends DocumentLine {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    public Invoice getInvoice() {
        return invoice;
    }

    void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }
}
