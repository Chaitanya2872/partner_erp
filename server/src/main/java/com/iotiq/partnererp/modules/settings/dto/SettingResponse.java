package com.iotiq.partnererp.modules.settings.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Company Settings as shown in Settings and Administration (Phase 13). No secrets: the security section reports the
 * configured policy values only.
 */
public final class SettingResponse {

    private SettingResponse() {
    }

    /**
     * The company profile and defaults. The registration fields (legal name, address, state, GSTIN) are changed through
     * the GST registration endpoint; they are repeated here so the profile reads as one record.
     *
     * @param canEdit           Settings write
     * @param canManageCommercial Settings write plus MANAGE_SETTINGS (lead protection, tiers)
     */
    public record Company(String legalName, String address, String stateCode, String stateName, String gstin,
                          String phone, String email, String terms, int estimateValidityDays, int invoiceDueDays,
                          BigDecimal defaultGstPercent, int acknowledgementHours, int estimateSlaDays,
                          int leadProtectionDays, int clawbackDays, Instant updatedAt, String updatedBy, long version,
                          boolean canEdit, boolean canManageCommercial, Limits limits) {
    }

    /** Validation bounds, so the form can say what is accepted. */
    public record Limits(int maxTerms, int maxValidityDays, int maxDueDays, int maxAcknowledgementHours,
                         int maxSlaDays, int maxProtectionDays) {
    }

    /** @param partners partners on the tier, excluding Inactive ones */
    public record Tier(String id, int level, String name, BigDecimal commissionPercent, BigDecimal minimumRevenue,
                       int payoutBusinessDays, long partners, Instant updatedAt, String updatedBy, long version) {
    }

    /** @param canEdit Settings write plus MANAGE_SETTINGS */
    public record Tiers(List<Tier> tiers, boolean canEdit) {
    }

    /**
     * One document number series. Formats are fixed in code and not editable.
     *
     * @param lastValue the last number allocated this calendar year (0 when none)
     */
    public record NumberSeries(String prefix, String document, String format, String example, String allocatedWhen,
                               int year, int lastValue, int capacity) {
    }

    public record Security(PasswordPolicy password, Lockout lockout, Sessions sessions, long lockedAccounts,
                           long pendingPasswordChanges) {
    }

    public record PasswordPolicy(int minLength, int maxLength, List<String> rules) {
    }

    public record Lockout(int maxFailedAttempts, long lockoutMinutes, String unlock) {
    }

    /** Lifetimes are read-only: they come from the deployment configuration (P3-09), not from this screen. */
    public record Sessions(long accessTokenMinutes, long refreshTokenDays, List<String> rules) {
    }

    /** A financial or security control and where it is enforced. Read-only: no setting can relax it. */
    public record Control(String area, String control, String permission, String enforcedBy, String reference) {
    }
}
