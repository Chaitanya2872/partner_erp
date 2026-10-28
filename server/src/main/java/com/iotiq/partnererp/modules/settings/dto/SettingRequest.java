package com.iotiq.partnererp.modules.settings.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Requests that change Company Settings (Phase 13). Each carries the version the user saw; numbers arrive as
 * entered and are range-checked by the service, so a blank or invalid value is an error rather than zero.
 */
public final class SettingRequest {

    private SettingRequest() {
    }

    /** Contact details, default document terms, document defaults and the lead SLA targets (Settings write). */
    public record Company(String phone, String email, String terms, Integer estimateValidityDays,
                          Integer invoiceDueDays, BigDecimal defaultGstPercent, Integer acknowledgementHours,
                          Integer estimateSlaDays, Long version) {
    }

    /** Commercial policy (Settings write + MANAGE_SETTINGS): lead protection. */
    public record Commercial(Integer leadProtectionDays, Long version) {
    }

    /** All four partner tiers at once, because thresholds are validated against each other. */
    public record Tiers(List<Tier> tiers) {
    }

    public record Tier(Integer level, String name, BigDecimal commissionPercent, BigDecimal minimumRevenue,
                       Integer payoutBusinessDays, Long version) {
    }
}
