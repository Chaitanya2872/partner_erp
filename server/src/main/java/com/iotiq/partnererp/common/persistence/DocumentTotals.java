package com.iotiq.partnererp.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * Result of the source totals() function, stored as a snapshot on each document
 * (BUSINESS_RULES §6.1). B2C documents embed it twice with column overrides.
 */
@Embeddable
public class DocumentTotals {

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "taxable_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(name = "gst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal gstAmount = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    public DocumentTotals() {
    }

    public DocumentTotals(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxableAmount,
                          BigDecimal gstAmount, BigDecimal grandTotal) {
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.taxableAmount = taxableAmount;
        this.gstAmount = gstAmount;
        this.grandTotal = grandTotal;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public BigDecimal getGstAmount() {
        return gstAmount;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }
}
