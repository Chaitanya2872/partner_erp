package com.iotiq.partnererp.modules.b2c.entity;

import com.iotiq.partnererp.common.persistence.BaseEntity;
import com.iotiq.partnererp.modules.products.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * B2C line of an internal twin (source twin.lines: name = B2C name, rate = B2C rate).
 */
@Entity
@Table(name = "b2c_document_items")
public class B2CDocumentItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "b2c_document_id", nullable = false)
    private B2CDocument document;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /** B2C name. */
    @Column(name = "description", length = 300)
    private String description;

    /** Partner copy name, shown on the internal view only. */
    @Column(name = "partner_name", length = 300)
    private String partnerName;

    @Column(name = "hsn", length = 20)
    private String hsn;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit = "nos";

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "gst_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal gstPercent;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    public B2CDocument getDocument() {
        return document;
    }

    void setDocument(B2CDocument document) {
        this.document = document;
    }

    public int getLineNo() {
        return lineNo;
    }

    public void setLineNo(int lineNo) {
        this.lineNo = lineNo;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public void setPartnerName(String partnerName) {
        this.partnerName = partnerName;
    }

    public String getHsn() {
        return hsn;
    }

    public void setHsn(String hsn) {
        this.hsn = hsn;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getGstPercent() {
        return gstPercent;
    }

    public void setGstPercent(BigDecimal gstPercent) {
        this.gstPercent = gstPercent;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }
}
