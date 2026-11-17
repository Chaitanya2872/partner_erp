package com.iotiq.partnererp.modules.leads.entity;

import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.common.persistence.AuditableEntity;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lead / deal registration (source "leads").
 */
@Entity
@Table(name = "leads")
public class Lead extends AuditableEntity {

    /** LEAD-YYYY-NNNNNN, allocated once on create and never changed (D-16). */
    @Column(name = "lead_number", nullable = false, length = 20, updatable = false)
    private String leadNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    @Column(name = "client_phone", length = 30)
    private String clientPhone;

    /** Last ten digits of the phone; computed by the database (source digits()). */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "client_phone_digits", length = 10, insertable = false, updatable = false)
    private String clientPhoneDigits;

    @Column(name = "client_email", length = 254)
    private String clientEmail;

    @Column(name = "city", length = 150)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 30)
    private ProjectType projectType;

    /** What the project is when {@link #projectType} is OTHER; null otherwise. */
    @Column(name = "project_type_other", length = 100)
    private String projectTypeOther;

    @Column(name = "units")
    private Integer units;

    @Column(name = "budget", precision = 14, scale = 2)
    private BigDecimal budget;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private LeadStage stage = LeadStage.NEW;

    @Column(name = "presales_owner", length = 150)
    private String presalesOwner;

    @Column(name = "expected_close")
    private LocalDate expectedClose;

    @Column(name = "source", length = 200)
    private String source;

    @Column(name = "notes")
    private String notes;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    /** Fee rate locked at acknowledgement (source lockedFeePct). */
    @Column(name = "locked_fee_percent", precision = 6, scale = 3)
    private BigDecimal lockedFeePercent;

    @Column(name = "protection_until")
    private LocalDate protectionUntil;

    /** Possible duplicate: same phone already protected for another partner (warning only, BR-L6). */
    @Column(name = "conflict", nullable = false)
    private boolean conflict;

    /** Set when the lead is deleted (D-26); deleted leads are hidden everywhere. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    /** Why the lead was archived; set together with {@code deletedAt}. */
    @Column(name = "delete_reason", length = 500)
    private String deleteReason;

    public String getLeadNumber() {
        return leadNumber;
    }

    public void setLeadNumber(String leadNumber) {
        this.leadNumber = leadNumber;
    }

    public Partner getPartner() {
        return partner;
    }

    public void setPartner(Partner partner) {
        this.partner = partner;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientPhone() {
        return clientPhone;
    }

    public void setClientPhone(String clientPhone) {
        this.clientPhone = clientPhone;
    }

    public String getClientPhoneDigits() {
        return clientPhoneDigits;
    }

    public String getClientEmail() {
        return clientEmail;
    }

    public void setClientEmail(String clientEmail) {
        this.clientEmail = clientEmail;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getProjectTypeOther() {
        return projectTypeOther;
    }

    public void setProjectTypeOther(String projectTypeOther) {
        this.projectTypeOther = projectTypeOther;
    }

    /** Label shown on screens and documents: "Other: Warehouse" for OTHER with a description, else the type's label. */
    public String getProjectTypeDisplay() {
        if (projectType == ProjectType.OTHER && projectTypeOther != null && !projectTypeOther.isBlank()) {
            return projectType.getLabel() + ": " + projectTypeOther;
        }
        return projectType.getLabel();
    }

    public ProjectType getProjectType() {
        return projectType;
    }

    public void setProjectType(ProjectType projectType) {
        this.projectType = projectType;
    }

    public Integer getUnits() {
        return units;
    }

    public void setUnits(Integer units) {
        this.units = units;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public LeadStage getStage() {
        return stage;
    }

    public void setStage(LeadStage stage) {
        this.stage = stage;
    }

    public String getPresalesOwner() {
        return presalesOwner;
    }

    public void setPresalesOwner(String presalesOwner) {
        this.presalesOwner = presalesOwner;
    }

    public LocalDate getExpectedClose() {
        return expectedClose;
    }

    public void setExpectedClose(LocalDate expectedClose) {
        this.expectedClose = expectedClose;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public UUID getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public void setAcknowledgedBy(UUID acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    public BigDecimal getLockedFeePercent() {
        return lockedFeePercent;
    }

    public void setLockedFeePercent(BigDecimal lockedFeePercent) {
        this.lockedFeePercent = lockedFeePercent;
    }

    public LocalDate getProtectionUntil() {
        return protectionUntil;
    }

    public void setProtectionUntil(LocalDate protectionUntil) {
        this.protectionUntil = protectionUntil;
    }

    public boolean isConflict() {
        return conflict;
    }

    public void setConflict(boolean conflict) {
        this.conflict = conflict;
    }

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
}
