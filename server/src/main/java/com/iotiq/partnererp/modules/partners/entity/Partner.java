package com.iotiq.partnererp.modules.partners.entity;

import com.iotiq.partnererp.common.enums.PartnerStatus;
import com.iotiq.partnererp.common.persistence.AuditableEntity;
import com.iotiq.partnererp.modules.settings.entity.PartnerTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Channel partner (source "cps"). PAN and bank fields are sensitive: masked in DTOs, redacted in logs.
 */
@Entity
@Table(name = "partners")
public class Partner extends AuditableEntity {

    @Column(name = "partner_code", length = 20)
    private String partnerCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private PartnerCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PartnerStatus status = PartnerStatus.PROSPECT;

    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "city", length = 100)
    private String city;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tier_id", nullable = false)
    private PartnerTier tier;

    /** Signed builder/developer/contractor programme: forces Strategic (source "programme"). */
    @Column(name = "strategic_programme", nullable = false)
    private boolean strategicProgramme;

    /** Below-threshold grace flag set by a tier review (source "grace"). */
    @Column(name = "downgrade_grace", nullable = false)
    private boolean downgradeGrace;

    @Column(name = "last_tier_review_on")
    private LocalDate lastTierReviewOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_status", nullable = false, length = 20)
    private AgreementStatus agreementStatus = AgreementStatus.NOT_SENT;

    @Column(name = "agreement_version", length = 20)
    private String agreementVersion = "v2.1";

    @Column(name = "agreement_date")
    private LocalDate agreementDate;

    @Column(name = "pan", length = 10)
    private String pan;

    /** Set when someone checked the PAN (Phase 11); cleared by the database whenever the PAN changes. */
    @Column(name = "pan_verified_at")
    private Instant panVerifiedAt;

    @Column(name = "pan_verified_by")
    private UUID panVerifiedBy;

    @Column(name = "pan_verification_note", length = 500)
    private String panVerificationNote;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "licence_no", length = 100)
    private String licenceNo;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "bank_account", length = 34)
    private String bankAccount;

    @Column(name = "ifsc", length = 11)
    private String ifsc;

    @Column(name = "notes")
    private String notes;

    public String getPartnerCode() {
        return partnerCode;
    }

    public void setPartnerCode(String partnerCode) {
        this.partnerCode = partnerCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PartnerCategory getCategory() {
        return category;
    }

    public void setCategory(PartnerCategory category) {
        this.category = category;
    }

    public PartnerStatus getStatus() {
        return status;
    }

    public void setStatus(PartnerStatus status) {
        this.status = status;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public PartnerTier getTier() {
        return tier;
    }

    public void setTier(PartnerTier tier) {
        this.tier = tier;
    }

    public boolean isStrategicProgramme() {
        return strategicProgramme;
    }

    public void setStrategicProgramme(boolean strategicProgramme) {
        this.strategicProgramme = strategicProgramme;
    }

    public boolean isDowngradeGrace() {
        return downgradeGrace;
    }

    public void setDowngradeGrace(boolean downgradeGrace) {
        this.downgradeGrace = downgradeGrace;
    }

    public LocalDate getLastTierReviewOn() {
        return lastTierReviewOn;
    }

    public void setLastTierReviewOn(LocalDate lastTierReviewOn) {
        this.lastTierReviewOn = lastTierReviewOn;
    }

    public AgreementStatus getAgreementStatus() {
        return agreementStatus;
    }

    public void setAgreementStatus(AgreementStatus agreementStatus) {
        this.agreementStatus = agreementStatus;
    }

    public String getAgreementVersion() {
        return agreementVersion;
    }

    public void setAgreementVersion(String agreementVersion) {
        this.agreementVersion = agreementVersion;
    }

    public LocalDate getAgreementDate() {
        return agreementDate;
    }

    public void setAgreementDate(LocalDate agreementDate) {
        this.agreementDate = agreementDate;
    }

    public String getPan() {
        return pan;
    }

    public Instant getPanVerifiedAt() {
        return panVerifiedAt;
    }

    public UUID getPanVerifiedBy() {
        return panVerifiedBy;
    }

    public String getPanVerificationNote() {
        return panVerificationNote;
    }

    /** Records (or, with nulls, clears) the PAN check. */
    public void setPanVerification(Instant at, UUID by, String note) {
        this.panVerifiedAt = at;
        this.panVerifiedBy = by;
        this.panVerificationNote = note;
    }

    public boolean isPanVerified() {
        return pan != null && panVerifiedAt != null;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public String getLicenceNo() {
        return licenceNo;
    }

    public void setLicenceNo(String licenceNo) {
        this.licenceNo = licenceNo;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
    }

    public String getIfsc() {
        return ifsc;
    }

    public void setIfsc(String ifsc) {
        this.ifsc = ifsc;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        // Deliberately excludes PAN and bank details.
        return "Partner{id=" + getId() + ", name='" + name + "'}";
    }
}
