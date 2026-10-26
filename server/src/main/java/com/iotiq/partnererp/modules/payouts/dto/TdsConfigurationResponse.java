package com.iotiq.partnererp.modules.payouts.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
/**
 * TDS settings for payouts.
 *
 * @param configured true when a confirmed version is in force today
 * @param inForce    the confirmed version in force today, if any
 * @param draft      the draft being prepared, if any
 * @param history    confirmed versions, latest effective date first
 */
public record TdsConfigurationResponse(boolean configured, Version inForce, Version draft, List<Version> history,
                                       boolean canEdit, boolean canConfirm) {

    public record Version(UUID id, String status, String sectionCode, String description, BigDecimal ratePercent,
                          BigDecimal rateWithoutPanPercent, BigDecimal fyThreshold, LocalDate effectiveFrom,
                          String editedByName, Instant updatedAt, Instant confirmedAt, String confirmedByName,
                          String confirmationNote, boolean complete, long version) {
    }
}
