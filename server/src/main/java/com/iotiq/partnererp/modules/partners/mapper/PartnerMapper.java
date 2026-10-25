package com.iotiq.partnererp.modules.partners.mapper;

import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.partners.dto.PartnerResponse;
import com.iotiq.partnererp.modules.partners.dto.PartnerSummary;
import com.iotiq.partnererp.modules.partners.dto.TierRef;
import com.iotiq.partnererp.modules.partners.entity.Partner;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Entity → DTO mapping. PAN, GSTIN and bank account numbers leave this class unmasked only when the caller may
 * see them; audit snapshots always carry the masked form.
 */
public final class PartnerMapper {

    private static final String MASK = "••";
    private static final int VISIBLE_TAIL = 4;
    /** GSTIN = state (2) + PAN (10) + entity no. + "Z" + check character; only the 3 non-PAN trailing ones show. */
    private static final int GSTIN_VISIBLE_TAIL = 3;

    private PartnerMapper() {
    }

    public static PartnerSummary toSummary(Partner p, TierRef calculatedTier, BigDecimal revenue12m, long leadCount) {
        return new PartnerSummary(p.getId(), p.getName(), p.getCategory(), p.getCategory().getLabel(), p.getCity(),
                p.getContactName(), p.getPhone(), p.getStatus(), p.getStatus().getLabel(), TierRef.of(p.getTier()),
                calculatedTier, revenue12m, p.getAgreementStatus(), p.getAgreementStatus().getLabel(), leadCount,
                p.isStrategicProgramme(), p.isDowngradeGrace());
    }

    public static PartnerResponse toResponse(Partner p, TierRef calculatedTier, BigDecimal revenue12m,
                                             boolean sensitiveVisible) {
        return new PartnerResponse(p.getId(), p.getName(), p.getCategory(), p.getCategory().getLabel(), p.getStatus(),
                p.getStatus().getLabel(), p.getContactName(), p.getPhone(), p.getEmail(), p.getCity(),
                TierRef.of(p.getTier()), calculatedTier, revenue12m, p.isStrategicProgramme(), p.isDowngradeGrace(),
                p.getLastTierReviewOn(), p.getAgreementStatus(), p.getAgreementStatus().getLabel(),
                p.getAgreementVersion(), p.getAgreementDate(),
                sensitiveVisible ? p.getPan() : mask(p.getPan()), p.getPan() != null,
                sensitiveVisible ? p.getGstin() : maskGstin(p.getGstin()), p.getGstin() != null, p.getLicenceNo(),
                p.getBankName(), sensitiveVisible ? p.getBankAccount() : mask(p.getBankAccount()),
                p.getBankAccount() != null, p.getIfsc(), p.getNotes(), sensitiveVisible, p.getCreatedAt(),
                p.getUpdatedAt(), p.getVersion());
    }

    /** BR-P9: {@code ••} plus the last 4 characters; null stays null. */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return MASK + value.substring(Math.max(0, value.length() - VISIBLE_TAIL));
    }

    /**
     * A GSTIN carries the PAN in characters 3–12, so the BR-P9 "last 4" mask would leak a PAN character beyond
     * what {@link #mask} shows for the PAN itself. Only the entity number, "Z" and check character remain.
     */
    public static String maskGstin(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return MASK + value.substring(Math.max(0, value.length() - GSTIN_VISIBLE_TAIL));
    }

    /** Snapshot for the audit log. PAN, GSTIN and bank account are masked; nothing secret is stored. */
    public static Map<String, Object> auditSnapshot(Partner p) {
        return AuditService.values(
                "name", p.getName(),
                "category", p.getCategory(),
                "status", p.getStatus(),
                "contactName", p.getContactName(),
                "phone", p.getPhone(),
                "email", p.getEmail(),
                "city", p.getCity(),
                "tierLevel", (int) p.getTier().getTierLevel(),
                "strategicProgramme", p.isStrategicProgramme(),
                "downgradeGrace", p.isDowngradeGrace(),
                "agreementStatus", p.getAgreementStatus(),
                "agreementVersion", p.getAgreementVersion(),
                "agreementDate", p.getAgreementDate(),
                "pan", mask(p.getPan()),
                "gstin", maskGstin(p.getGstin()),
                "licenceNo", p.getLicenceNo(),
                "bankName", p.getBankName(),
                "bankAccount", mask(p.getBankAccount()),
                "ifsc", p.getIfsc(),
                "notes", p.getNotes());
    }
}
