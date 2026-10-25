package com.iotiq.partnererp.modules.products.entity;

import com.iotiq.partnererp.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Item & price sheet entry (source "items"). b2cName, b2cPrice and costPrice are internal-only
 * and must never reach partner-facing responses (ADR-06).
 */
@Entity
@Table(name = "products")
public class Product extends AuditableEntity {

    @Column(name = "sku", nullable = false, length = 50)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private ProductCategory category;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit = "nos";

    @Column(name = "hsn", length = 20)
    private String hsn;

    @Column(name = "b2c_name", nullable = false, length = 300)
    private String b2cName;

    @Column(name = "partner_name", nullable = false, length = 300)
    private String partnerName;

    /** Alternative partner-facing names (source aliases). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "aliases", nullable = false, columnDefinition = "text[]")
    private List<String> aliases = new ArrayList<>();

    /** Ex-GST B2C price; null means not yet set ("B2C price needed"). */
    @Column(name = "b2c_price", precision = 14, scale = 2)
    private BigDecimal b2cPrice;

    /** Ex-GST partner price. */
    @Column(name = "partner_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal partnerPrice;

    @Column(name = "gst_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal gstPercent;

    /** Optional landed cost. */
    @Column(name = "cost_price", precision = 14, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getHsn() {
        return hsn;
    }

    public void setHsn(String hsn) {
        this.hsn = hsn;
    }

    public String getB2cName() {
        return b2cName;
    }

    public void setB2cName(String b2cName) {
        this.b2cName = b2cName;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public void setPartnerName(String partnerName) {
        this.partnerName = partnerName;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases;
    }

    public BigDecimal getB2cPrice() {
        return b2cPrice;
    }

    public void setB2cPrice(BigDecimal b2cPrice) {
        this.b2cPrice = b2cPrice;
    }

    public BigDecimal getPartnerPrice() {
        return partnerPrice;
    }

    public void setPartnerPrice(BigDecimal partnerPrice) {
        this.partnerPrice = partnerPrice;
    }

    public BigDecimal getGstPercent() {
        return gstPercent;
    }

    public void setGstPercent(BigDecimal gstPercent) {
        this.gstPercent = gstPercent;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
