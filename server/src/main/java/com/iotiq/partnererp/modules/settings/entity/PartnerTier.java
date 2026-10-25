package com.iotiq.partnererp.modules.settings.entity;

import com.iotiq.partnererp.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One of the four fixed partner tiers (source DEFTIERS). Names, fee, threshold and payout days are
 * editable; tiers cannot be added or removed.
 */
@Entity
@Table(name = "partner_tiers")
public class PartnerTier extends AuditableEntity {

    @Column(name = "tier_level", nullable = false, updatable = false)
    private short tierLevel;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** Referral fee %. */
    @Column(name = "commission_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal commissionPercent;

    /** Minimum paid referred revenue, ex-GST, trailing 12 months. */
    @Column(name = "minimum_revenue", nullable = false, precision = 14, scale = 2)
    private BigDecimal minimumRevenue;

    @Column(name = "payout_business_days", nullable = false)
    private short payoutBusinessDays;

    public short getTierLevel() {
        return tierLevel;
    }

    public void setTierLevel(short tierLevel) {
        this.tierLevel = tierLevel;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getCommissionPercent() {
        return commissionPercent;
    }

    public void setCommissionPercent(BigDecimal commissionPercent) {
        this.commissionPercent = commissionPercent;
    }

    public BigDecimal getMinimumRevenue() {
        return minimumRevenue;
    }

    public void setMinimumRevenue(BigDecimal minimumRevenue) {
        this.minimumRevenue = minimumRevenue;
    }

    public short getPayoutBusinessDays() {
        return payoutBusinessDays;
    }

    public void setPayoutBusinessDays(short payoutBusinessDays) {
        this.payoutBusinessDays = payoutBusinessDays;
    }
}
