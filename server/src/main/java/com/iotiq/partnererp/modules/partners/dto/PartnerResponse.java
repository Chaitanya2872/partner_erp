package com.iotiq.partnererp.modules.partners.dto;

import com.iotiq.partnererp.common.enums.PartnerStatus;
import com.iotiq.partnererp.modules.partners.entity.AgreementStatus;
import com.iotiq.partnererp.modules.partners.entity.PartnerCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Partner detail. {@code pan}, {@code gstin} and {@code bankAccount} are the full values only when
 * {@code sensitiveVisible} ({@code PARTNERS:VIEW_PARTNER_SENSITIVE}); otherwise PAN and bank account are masked to
 * the last 4 characters (BR-P9) and GSTIN to its last 3 (which are not part of the embedded PAN). The
 * {@code *OnFile} flags say whether a value exists. {@code panVerified}: someone recorded checking the PAN (Phase 11);
 * it is cleared whenever the PAN changes.
 */
public record PartnerResponse(UUID id, String name, PartnerCategory category, String categoryLabel,
                              PartnerStatus status, String statusLabel, String contactName, String phone,
                              String email, String city, TierRef tier, TierRef calculatedTier, BigDecimal revenue12m,
                              boolean strategicProgramme, boolean downgradeGrace, LocalDate lastTierReviewOn,
                              AgreementStatus agreementStatus, String agreementStatusLabel, String agreementVersion,
                              LocalDate agreementDate, String pan, boolean panOnFile, boolean panVerified,
                              Instant panVerifiedAt, String gstin, boolean gstinOnFile,
                              String licenceNo,
                              String bankName, String bankAccount, boolean bankAccountOnFile, String ifsc,
                              String notes, boolean sensitiveVisible, Instant createdAt, Instant updatedAt,
                              long version) {
}
