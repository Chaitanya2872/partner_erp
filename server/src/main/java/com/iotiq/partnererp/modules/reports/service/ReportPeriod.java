package com.iotiq.partnererp.modules.reports.service;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.modules.payouts.service.PayoutRules;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The reporting period of dashboards and reports (DECISIONS P12-03). Dates are calendar dates in the business zone
 * (Asia/Kolkata), both ends inclusive. Timestamp columns (created_at, stage history) are compared with the zone's
 * midnights: {@code [from 00:00, to + 1 day 00:00)}. "This …" presets run up to today. The previous period, used for
 * trend indicators, has the same number of days and ends the day before {@code from}.
 *
 * <p>The financial year runs 1 April to 31 March ({@link PayoutRules#fyStart}); calendar quarters coincide with FY
 * quarters. Statutory calculations (TDS) keep their own FY logic and never read this period.
 */
public record ReportPeriod(Preset preset, LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo,
                           Granularity granularity) {

    /** THIS_YEAR and LAST_YEAR are calendar years, January to December (Phase 13, M-3); THIS_FY starts on 1 April. */
    public enum Preset {
        TODAY, LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH, LAST_MONTH, THIS_QUARTER, THIS_FY, THIS_YEAR, LAST_YEAR, CUSTOM
    }

    /** Trend buckets: one per day up to {@link #DAILY_MAX_DAYS} days, else one per month. */
    public enum Granularity { DAY, MONTH }

    /** Longest custom range, about three years: aggregation stays bounded. */
    public static final int MAX_DAYS = 1096;
    public static final int DAILY_MAX_DAYS = 62;
    public static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

    /**
     * @param preset null means CUSTOM when a date is given, otherwise {@code fallback}
     */
    public static ReportPeriod resolve(Preset preset, LocalDate from, LocalDate to, LocalDate today, Preset fallback) {
        Preset p = preset != null ? preset : (from != null || to != null ? Preset.CUSTOM : fallback);
        LocalDate start;
        LocalDate end = today;
        switch (p) {
            case TODAY -> start = today;
            case LAST_7_DAYS -> start = today.minusDays(6);
            case LAST_30_DAYS -> start = today.minusDays(29);
            case THIS_MONTH -> start = today.withDayOfMonth(1);
            case LAST_MONTH -> {
                start = today.withDayOfMonth(1).minusMonths(1);
                end = today.withDayOfMonth(1).minusDays(1);
            }
            case THIS_QUARTER -> start = LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1);
            case THIS_FY -> start = PayoutRules.fyStart(today);
            case THIS_YEAR -> start = today.withDayOfYear(1);
            case LAST_YEAR -> {
                start = today.withDayOfYear(1).minusYears(1);
                end = today.withDayOfYear(1).minusDays(1);
            }
            default -> {
                if (from == null || to == null) {
                    throw ValidationException.of(from == null ? "from" : "to", "Enter both dates of a custom range.");
                }
                if (to.isBefore(from)) {
                    throw ValidationException.of("to", "The end date can't be before the start date.");
                }
                if (from.isBefore(EARLIEST)) {
                    throw ValidationException.of("from", "Dates before 1 Jan 2000 aren't supported.");
                }
                if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
                    throw ValidationException.of("to", "A custom range can span at most " + MAX_DAYS + " days (about three years).");
                }
                start = from;
                end = to;
            }
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate previousTo = start.minusDays(1);
        return new ReportPeriod(p, start, end, previousTo.minusDays(days - 1), previousTo,
                days <= DAILY_MAX_DAYS ? Granularity.DAY : Granularity.MONTH);
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** Start of {@code from} in the business zone. */
    public Instant fromInstant() {
        return from.atStartOfDay(AppConstants.BUSINESS_ZONE).toInstant();
    }

    /** Exclusive end: midnight after {@code to}. */
    public Instant toInstant() {
        return to.plusDays(1).atStartOfDay(AppConstants.BUSINESS_ZONE).toInstant();
    }

    public Instant previousFromInstant() {
        return previousFrom.atStartOfDay(AppConstants.BUSINESS_ZONE).toInstant();
    }

    /** The bucket a date falls in: the date itself, or the first day of its month (never before {@code from}). */
    public LocalDate bucketOf(LocalDate date) {
        if (granularity == Granularity.DAY) {
            return date;
        }
        LocalDate month = date.withDayOfMonth(1);
        return month.isBefore(from) ? from : month;
    }

    /** Every bucket of the period in order, so empty days or months still appear in trends. */
    public List<LocalDate> buckets() {
        List<LocalDate> buckets = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            buckets.add(bucketOf(cursor));
            cursor = granularity == Granularity.DAY ? cursor.plusDays(1) : cursor.withDayOfMonth(1).plusMonths(1);
        }
        return buckets;
    }
}
