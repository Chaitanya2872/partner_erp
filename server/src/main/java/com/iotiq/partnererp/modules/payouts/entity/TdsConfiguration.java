package com.iotiq.partnererp.modules.payouts.entity;

import com.iotiq.partnererp.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One version of the TDS rules for partner payouts, entered and confirmed by Finance. Nothing is seeded.
 * Confirmed versions never change.
 */
@Entity
@Table(name = "tds_configurations")
public class TdsConfiguration extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutEnums.TdsConfigStatus status;

    @Column(name = "section_code", length = 20)
    private String sectionCode;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "rate_percent", precision = 6, scale = 3)
    private BigDecimal ratePercent;

    @Column(name = "rate_without_pan_percent", precision = 6, scale = 3)
    private BigDecimal rateWithoutPanPercent;

    @Column(name = "fy_threshold", precision = 14, scale = 2)
    private BigDecimal fyThreshold;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmation_note", length = 500)
    private String confirmationNote;

    @Column(name = "edited_by")
    private UUID editedBy;

    public PayoutEnums.TdsConfigStatus getStatus() {
        return status;
    }

    public void setStatus(PayoutEnums.TdsConfigStatus status) {
        this.status = status;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public void setSectionCode(String sectionCode) {
        this.sectionCode = sectionCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getRatePercent() {
        return ratePercent;
    }

    public void setRatePercent(BigDecimal ratePercent) {
        this.ratePercent = ratePercent;
    }

    public BigDecimal getRateWithoutPanPercent() {
        return rateWithoutPanPercent;
    }

    public void setRateWithoutPanPercent(BigDecimal rateWithoutPanPercent) {
        this.rateWithoutPanPercent = rateWithoutPanPercent;
    }

    public BigDecimal getFyThreshold() {
        return fyThreshold;
    }

    public void setFyThreshold(BigDecimal fyThreshold) {
        this.fyThreshold = fyThreshold;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public UUID getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(UUID confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public String getConfirmationNote() {
        return confirmationNote;
    }

    public void setConfirmationNote(String confirmationNote) {
        this.confirmationNote = confirmationNote;
    }

    public UUID getEditedBy() {
        return editedBy;
    }

    public void setEditedBy(UUID editedBy) {
        this.editedBy = editedBy;
    }
}
