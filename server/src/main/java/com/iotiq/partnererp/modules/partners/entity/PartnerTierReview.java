package com.iotiq.partnererp.modules.partners.entity;

import com.iotiq.partnererp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * History of applied tier review outcomes and automatic upgrades on payment.
 */
@Entity
@Table(name = "partner_tier_reviews")
public class PartnerTierReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(name = "from_level", nullable = false)
    private short fromLevel;

    @Column(name = "to_level", nullable = false)
    private short toLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private TierReviewAction action;

    @Column(name = "revenue_12m", nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue12m;

    @Column(name = "programme", nullable = false)
    private boolean programme;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @PrePersist
    void onCreate() {
        if (reviewedAt == null) {
            reviewedAt = Instant.now();
        }
    }

    public Partner getPartner() {
        return partner;
    }

    public void setPartner(Partner partner) {
        this.partner = partner;
    }

    public short getFromLevel() {
        return fromLevel;
    }

    public void setFromLevel(short fromLevel) {
        this.fromLevel = fromLevel;
    }

    public short getToLevel() {
        return toLevel;
    }

    public void setToLevel(short toLevel) {
        this.toLevel = toLevel;
    }

    public TierReviewAction getAction() {
        return action;
    }

    public void setAction(TierReviewAction action) {
        this.action = action;
    }

    public BigDecimal getRevenue12m() {
        return revenue12m;
    }

    public void setRevenue12m(BigDecimal revenue12m) {
        this.revenue12m = revenue12m;
    }

    public boolean isProgramme() {
        return programme;
    }

    public void setProgramme(boolean programme) {
        this.programme = programme;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
