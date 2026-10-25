package com.iotiq.partnererp.modules.leads.service;

import com.iotiq.partnererp.common.enums.LeadStage;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Lead SLA clocks and protection status (BUSINESS_RULES §4.1, BR-L7), reproducing the source's {@code leadSla}
 * including its rounding: hours are rounded half up, so the acknowledgement breach starts at 48.5 h.
 */
public final class LeadSla {

    private static final long HOUR_MS = 3_600_000L;

    private LeadSla() {
    }

    /** The configured targets (company settings). */
    public record Targets(int ackHours, int estimateDays) {
    }

    /**
     * @param ackHours       hours from creation to acknowledgement (or to now while open and unacknowledged); null
     *                       for an unacknowledged Won/Lost lead
     * @param estimateDays   days from the acknowledgement date to the first estimate (or to today while open and
     *                       without an estimate); null before acknowledgement or for a closed lead without estimates
     * @param hasEstimate    whether the lead has any estimate, in any status
     */
    public record Result(Long ackHours, boolean ackBreach, Long estimateDays, boolean estimateBreach,
                         boolean hasEstimate) {

        public boolean breached() {
            return ackBreach || estimateBreach;
        }
    }

    public static boolean isOpen(LeadStage stage) {
        return stage != LeadStage.WON && stage != LeadStage.LOST;
    }

    /**
     * @param earliestEstimate date of the earliest-dated estimate for the lead (any status), or null
     */
    public static Result evaluate(Instant createdAt, Instant acknowledgedAt, LeadStage stage,
                                  LocalDate earliestEstimate, Instant now, ZoneId zone, Targets targets) {
        Long ackHours = null;
        boolean ackBreach = false;
        if (acknowledgedAt != null) {
            ackHours = roundedHours(createdAt, acknowledgedAt);
        } else if (isOpen(stage)) {
            ackHours = roundedHours(createdAt, now);
        }
        if (ackHours != null) {
            ackBreach = ackHours > targets.ackHours();
        }

        Long estimateDays = null;
        if (acknowledgedAt != null) {
            LocalDate ackDate = LocalDate.ofInstant(acknowledgedAt, zone);
            if (earliestEstimate != null) {
                estimateDays = ChronoUnit.DAYS.between(ackDate, earliestEstimate);
            } else if (isOpen(stage)) {
                estimateDays = ChronoUnit.DAYS.between(ackDate, LocalDate.ofInstant(now, zone));
            }
        }
        boolean estimateBreach = estimateDays != null && estimateDays > targets.estimateDays();
        return new Result(ackHours, ackBreach, estimateDays, estimateBreach, earliestEstimate != null);
    }

    /** BR-L7: protection counts while {@code protectionUntil >= today}. */
    public static boolean protectionActive(LocalDate protectionUntil, LocalDate today) {
        return protectionUntil != null && !protectionUntil.isBefore(today);
    }

    /** Days of protection left (0 on the last day), or null when there is none or it has expired. */
    public static Long protectionDaysLeft(LocalDate protectionUntil, LocalDate today) {
        return protectionActive(protectionUntil, today) ? ChronoUnit.DAYS.between(today, protectionUntil) : null;
    }

    /** JavaScript {@code Math.round(ms / 3.6e6)}: half up, also for negative values (clock skew). */
    static long roundedHours(Instant from, Instant to) {
        long ms = Duration.between(from, to).toMillis();
        return Math.floorDiv(ms + HOUR_MS / 2, HOUR_MS);
    }
}
