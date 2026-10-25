package com.iotiq.partnererp.common.persistence;

import com.iotiq.partnererp.modules.products.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

import java.math.BigDecimal;

/**
 * Line item snapshot shared by estimates and invoices (source blankLine / item selection).
 * Product names, HSN, unit, rates and GST are copied so product edits never change saved documents.
 */
@MappedSuperclass
public abstract class DocumentLine extends BaseEntity {

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Nullable: the product may later be removed; the snapshot below remains. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "b2c_name", length = 300)
    private String b2cName;

    @Column(name = "hsn", length = 20)
    private String hsn;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit = "nos";

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "b2c_unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal b2cUnitPrice = BigDecimal.ZERO;

    @Column(name = "gst_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal gstPercent;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

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

    public String getB2cName() {
        return b2cName;
    }

    public void setB2cName(String b2cName) {
        this.b2cName = b2cName;
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

    public BigDecimal getB2cUnitPrice() {
        return b2cUnitPrice;
    }

    public void setB2cUnitPrice(BigDecimal b2cUnitPrice) {
        this.b2cUnitPrice = b2cUnitPrice;
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
