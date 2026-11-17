package com.iotiq.partnererp.modules.estimates.entity;

import com.iotiq.partnererp.common.persistence.SalesDocument;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Partner-price estimate (source "estimates"). Each one has an internal B2C twin.
 */
@Entity
@Table(name = "estimates")
public class Estimate extends SalesDocument {

    @Column(name = "estimate_number", nullable = false, length = 30, updatable = false)
    private String estimateNumber;

    @Column(name = "estimate_date", nullable = false)
    private LocalDate estimateDate;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EstimateStatus status = EstimateStatus.DRAFT;

    @Column(name = "sent_on")
    private LocalDate sentOn;

    @OneToMany(mappedBy = "estimate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<EstimateItem> items = new ArrayList<>();

    public void addItem(EstimateItem item) {
        item.setEstimate(this);
        items.add(item);
    }

    public String getEstimateNumber() {
        return estimateNumber;
    }

    public void setEstimateNumber(String estimateNumber) {
        this.estimateNumber = estimateNumber;
    }

    public LocalDate getEstimateDate() {
        return estimateDate;
    }

    public void setEstimateDate(LocalDate estimateDate) {
        this.estimateDate = estimateDate;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public EstimateStatus getStatus() {
        return status;
    }

    public void setStatus(EstimateStatus status) {
        this.status = status;
    }

    public LocalDate getSentOn() {
        return sentOn;
    }

    public void setSentOn(LocalDate sentOn) {
        this.sentOn = sentOn;
    }

    /** Soft delete (Phase 8): the row, its number and its twin stay; lists and detail no longer show it. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    /** Why the estimate was archived; set together with {@code deletedAt}. */
    @Column(name = "delete_reason", length = 500)
    private String deleteReason;

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeleteReason() {
        return deleteReason;
    }

    public void setDeleteReason(String deleteReason) {
        this.deleteReason = deleteReason;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(UUID deletedBy) {
        this.deletedBy = deletedBy;
    }

    public List<EstimateItem> getItems() {
        return items;
    }
}
