package com.iotiq.partnererp.modules.settings.entity;

import com.iotiq.partnererp.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Single-row company and business-rule configuration (source settings "main" merged with DEF).
 */
@Entity
@Table(name = "company_settings")
public class CompanySettings extends AuditableEntity {

    /** Always true; a unique constraint keeps the table to one row. */
    @Column(name = "singleton", nullable = false, insertable = false, updatable = false)
    private boolean singleton = true;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "address")
    private String address;

    @Column(name = "gstin", length = 15)
    private String gstin;

    /** GST state code of the supplier's registration (Phase 9). */
    @Column(name = "state_code", length = 2)
    private String stateCode;

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "terms")
    private String terms;

    @Column(name = "lead_protection_days", nullable = false)
    private int leadProtectionDays;

    @Column(name = "clawback_days", nullable = false)
    private int clawbackDays;

    @Column(name = "tds_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal tdsPercent;

    @Column(name = "tds_threshold", nullable = false, precision = 14, scale = 2)
    private BigDecimal tdsThreshold;

    @Column(name = "acknowledgement_hours", nullable = false)
    private int acknowledgementHours;

    @Column(name = "estimate_sla_days", nullable = false)
    private int estimateSlaDays;

    @Column(name = "estimate_validity_days", nullable = false)
    private int estimateValidityDays;

    @Column(name = "invoice_due_days", nullable = false)
    private int invoiceDueDays;

    @Column(name = "default_gst_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal defaultGstPercent;

    public boolean isSingleton() {
        return singleton;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
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

    public String getTerms() {
        return terms;
    }

    public void setTerms(String terms) {
        this.terms = terms;
    }

    public int getLeadProtectionDays() {
        return leadProtectionDays;
    }

    public void setLeadProtectionDays(int leadProtectionDays) {
        this.leadProtectionDays = leadProtectionDays;
    }

    public int getClawbackDays() {
        return clawbackDays;
    }

    public void setClawbackDays(int clawbackDays) {
        this.clawbackDays = clawbackDays;
    }

    public BigDecimal getTdsPercent() {
        return tdsPercent;
    }

    public void setTdsPercent(BigDecimal tdsPercent) {
        this.tdsPercent = tdsPercent;
    }

    public BigDecimal getTdsThreshold() {
        return tdsThreshold;
    }

    public void setTdsThreshold(BigDecimal tdsThreshold) {
        this.tdsThreshold = tdsThreshold;
    }

    public int getAcknowledgementHours() {
        return acknowledgementHours;
    }

    public void setAcknowledgementHours(int acknowledgementHours) {
        this.acknowledgementHours = acknowledgementHours;
    }

    public int getEstimateSlaDays() {
        return estimateSlaDays;
    }

    public void setEstimateSlaDays(int estimateSlaDays) {
        this.estimateSlaDays = estimateSlaDays;
    }

    public int getEstimateValidityDays() {
        return estimateValidityDays;
    }

    public void setEstimateValidityDays(int estimateValidityDays) {
        this.estimateValidityDays = estimateValidityDays;
    }

    public int getInvoiceDueDays() {
        return invoiceDueDays;
    }

    public void setInvoiceDueDays(int invoiceDueDays) {
        this.invoiceDueDays = invoiceDueDays;
    }

    public BigDecimal getDefaultGstPercent() {
        return defaultGstPercent;
    }

    public void setDefaultGstPercent(BigDecimal defaultGstPercent) {
        this.defaultGstPercent = defaultGstPercent;
    }
}
