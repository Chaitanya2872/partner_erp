package com.iotiq.partnererp.modules.payouts.entity;

import com.iotiq.partnererp.common.persistence.CreatedAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One recorded TDS calculation of a payout (append-only history).
 */
@Entity
@Table(name = "payout_tds_calculations")
public class PayoutTdsCalculation extends CreatedAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_id", nullable = false)
    private Payout payout;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 20)
    private PayoutEnums.TdsEvent event;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tds_configuration_id")
    private TdsConfiguration tdsConfiguration;

    @Column(name = "section_code", length = 20)
    private String sectionCode;

    @Column(name = "rate_percent", precision = 6, scale = 3)
    private BigDecimal ratePercent;

    @Column(name = "fy_threshold", precision = 14, scale = 2)
    private BigDecimal fyThreshold;

    @Column(name = "fy_start", nullable = false)
    private LocalDate fyStart;

    @Column(name = "pan_verified", nullable = false)
    private boolean panVerified;

    @Column(name = "prior_fy_commission", nullable = false, precision = 14, scale = 2)
    private BigDecimal priorFyCommission;

    @Column(name = "prior_fy_tds_base", nullable = false, precision = 14, scale = 2)
    private BigDecimal priorFyTdsBase;

    @Column(name = "commission_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal commissionTotal;

    @Column(name = "tds_base", precision = 14, scale = 2)
    private BigDecimal tdsBase;

    @Column(name = "computed_tds", precision = 14, scale = 2)
    private BigDecimal computedTds;

    @Column(name = "tds_amount", precision = 14, scale = 2)
    private BigDecimal tdsAmount;

    @Column(name = "adjustment_reason", length = 500)
    private String adjustmentReason;

    public Payout getPayout() {
        return payout;
    }

    public void setPayout(Payout payout) {
        this.payout = payout;
    }

    public PayoutEnums.TdsEvent getEvent() {
        return event;
    }

    public void setEvent(PayoutEnums.TdsEvent event) {
        this.event = event;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public TdsConfiguration getTdsConfiguration() {
        return tdsConfiguration;
    }

    public void setTdsConfiguration(TdsConfiguration tdsConfiguration) {
        this.tdsConfiguration = tdsConfiguration;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public void setSectionCode(String sectionCode) {
        this.sectionCode = sectionCode;
    }

    public BigDecimal getRatePercent() {
        return ratePercent;
    }

    public void setRatePercent(BigDecimal ratePercent) {
        this.ratePercent = ratePercent;
    }

    public BigDecimal getFyThreshold() {
        return fyThreshold;
    }

    public void setFyThreshold(BigDecimal fyThreshold) {
        this.fyThreshold = fyThreshold;
    }

    public LocalDate getFyStart() {
        return fyStart;
    }

    public void setFyStart(LocalDate fyStart) {
        this.fyStart = fyStart;
    }

    public boolean isPanVerified() {
        return panVerified;
    }

    public void setPanVerified(boolean panVerified) {
        this.panVerified = panVerified;
    }

    public BigDecimal getPriorFyCommission() {
        return priorFyCommission;
    }

    public void setPriorFyCommission(BigDecimal priorFyCommission) {
        this.priorFyCommission = priorFyCommission;
    }

    public BigDecimal getPriorFyTdsBase() {
        return priorFyTdsBase;
    }

    public void setPriorFyTdsBase(BigDecimal priorFyTdsBase) {
        this.priorFyTdsBase = priorFyTdsBase;
    }

    public BigDecimal getCommissionTotal() {
        return commissionTotal;
    }

    public void setCommissionTotal(BigDecimal commissionTotal) {
        this.commissionTotal = commissionTotal;
    }

    public BigDecimal getTdsBase() {
        return tdsBase;
    }

    public void setTdsBase(BigDecimal tdsBase) {
        this.tdsBase = tdsBase;
    }

    public BigDecimal getComputedTds() {
        return computedTds;
    }

    public void setComputedTds(BigDecimal computedTds) {
        this.computedTds = computedTds;
    }

    public BigDecimal getTdsAmount() {
        return tdsAmount;
    }

    public void setTdsAmount(BigDecimal tdsAmount) {
        this.tdsAmount = tdsAmount;
    }

    public String getAdjustmentReason() {
        return adjustmentReason;
    }

    public void setAdjustmentReason(String adjustmentReason) {
        this.adjustmentReason = adjustmentReason;
    }
}
