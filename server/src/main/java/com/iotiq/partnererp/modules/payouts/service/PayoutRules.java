package com.iotiq.partnererp.modules.payouts.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Month;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Pure payout calculations (DECISIONS.md §L). All amounts are BigDecimal rounded HALF_UP to the paisa.
 */
public final class PayoutRules {

    public static final int MAX_REASON = 500;
    public static final int MAX_NOTES = 500;
    public static final int MAX_REFERENCE = 100;
    public static final BigDecimal MAX_ADJUSTMENT = new BigDecimal("99999999.99");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PayoutRules() {
    }

    public static BigDecimal r2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Commission on one receipt (BR-M4): r2(ex-GST amount × fee % / 100), with the fee % the invoice was issued with.
     */
    public static BigDecimal commission(BigDecimal exGstAmount, BigDecimal feePercent) {
        return r2(exGstAmount.multiply(feePercent).divide(HUNDRED, 10, RoundingMode.HALF_UP));
    }

    /** First day (1 April) of the Indian financial year containing {@code date}. */
    public static LocalDate fyStart(LocalDate date) {
        int year = date.getMonthValue() >= Month.APRIL.getValue() ? date.getYear() : date.getYear() - 1;
        return LocalDate.of(year, Month.APRIL, 1);
    }

    /** "2026-27" for the financial year starting 1 April 2026. */
    public static String fyLabel(LocalDate fyStart) {
        return fyStart.getYear() + "-" + String.format("%02d", (fyStart.getYear() + 1) % 100);
    }

    public enum TdsStatus {
        /** TDS calculated with a confirmed configuration. */
        CALCULATED,
        /** No confirmed configuration in force for the payout's date: previews only, approval is blocked. */
        CONFIG_INCOMPLETE,
        /** The partner's PAN isn't verified and no rate without PAN is configured: approval is blocked. */
        PAN_NOT_VERIFIED,
        /** Carried over from the Phase 2 accrual table. */
        LEGACY
    }

    /** The configuration in force (all values confirmed by Finance). */
    public record TdsRule(String sectionCode, BigDecimal ratePercent, BigDecimal rateWithoutPanPercent,
                          BigDecimal fyThreshold) {
    }

    /**
     * @param priorFyCommission commission of the partner's approved, processing and paid payouts in the same financial
     *                          year (negative adjustments included)
     * @param priorFyTdsBase    the part of it TDS was already calculated on
     */
    public record TdsInput(BigDecimal commissionTotal, BigDecimal priorFyCommission, BigDecimal priorFyTdsBase,
                           boolean panVerified) {
    }

    public record TdsResult(TdsStatus status, BigDecimal ratePercent, BigDecimal tdsBase, BigDecimal tds) {
    }

    /**
     * TDS on a payout (D-08). The cumulative commission of the financial year (earlier payouts plus this one) is compared
     * with the threshold; once it exceeds the threshold, TDS applies to all of the year's commission not yet taxed, so
     * the payout that crosses the threshold also covers the earlier untaxed ones (the source's catch-up rule, BR-O5).
     * Below the threshold no TDS is deducted. The rate is the configured one, or the rate without PAN when the
     * partner's PAN isn't verified; when that isn't configured the payout can't be approved.
     */
    public static TdsResult tds(TdsRule rule, TdsInput in) {
        if (rule == null) {
            return new TdsResult(TdsStatus.CONFIG_INCOMPLETE, null, null, null);
        }
        BigDecimal rate = in.panVerified() ? rule.ratePercent() : rule.rateWithoutPanPercent();
        if (rate == null) {
            return new TdsResult(TdsStatus.PAN_NOT_VERIFIED, null, null, null);
        }
        BigDecimal cumulative = in.priorFyCommission().add(in.commissionTotal());
        BigDecimal base = BigDecimal.ZERO;
        if (cumulative.compareTo(rule.fyThreshold()) > 0) {
            base = cumulative.subtract(in.priorFyTdsBase()).max(BigDecimal.ZERO);
        }
        base = r2(base);
        BigDecimal tds = r2(base.multiply(rate).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        return new TdsResult(TdsStatus.CALCULATED, rate, base, tds);
    }

    /** Trimmed text, or null when blank. */
    public static String clean(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    public static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    /** A case-insensitive LIKE pattern matching {@code query} anywhere, with wildcards escaped by backslash. */
    public static String like(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Fingerprint of the inputs, to recognise a resubmission of the same idempotency key. */
    public static String requestHash(Object... parts) {
        StringBuilder canonical = new StringBuilder();
        for (Object part : parts) {
            canonical.append(part instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString()
                    : String.valueOf(part)).append('\u001f');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
