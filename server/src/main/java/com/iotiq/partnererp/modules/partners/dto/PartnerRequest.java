package com.iotiq.partnererp.modules.partners.dto;

import com.iotiq.partnererp.common.enums.PartnerStatus;
import com.iotiq.partnererp.modules.partners.entity.AgreementStatus;
import com.iotiq.partnererp.modules.partners.entity.PartnerCategory;

import java.time.LocalDate;

/**
 * Create or replace a partner. The service validates and normalises every field (BR-P1…P5).
 *
 * <ul>
 *   <li>{@code tierLevel} and {@code strategicProgramme} change commission rates: setting them to anything other
 *       than the current value (or tier 1 / no programme on create) needs {@code PARTNERS:APPROVE}.</li>
 *   <li>{@code pan}, {@code gstin} and {@code bankAccount} are write-through for callers who cannot see them: {@code null}
 *       keeps the stored value, an empty string removes it, any other value replaces it.</li>
 *   <li>{@code version} is required on update (optimistic locking) and ignored on create.</li>
 * </ul>
 */
public record PartnerRequest(String name, PartnerCategory category, PartnerStatus status, String contactName,
                             String phone, String email, String city, Integer tierLevel, Boolean strategicProgramme,
                             AgreementStatus agreementStatus, String agreementVersion, LocalDate agreementDate,
                             String pan, String gstin, String licenceNo, String bankName, String bankAccount,
                             String ifsc, String notes, Long version) {

    /** Never prints PAN, GSTIN or bank account numbers. */
    @Override
    public String toString() {
        return "PartnerRequest[name=" + name + ", category=" + category + ", status=" + status + ", version=" + version + "]";
    }
}
