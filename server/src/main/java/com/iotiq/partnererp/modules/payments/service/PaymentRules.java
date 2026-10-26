package com.iotiq.partnererp.modules.payments.service;

import com.iotiq.partnererp.modules.payments.entity.PaymentMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pure payment rules shared by the service and its unit tests (DECISIONS.md §K).
 */
public final class PaymentRules {

    public static final int MAX_REFERENCE = 100;
    public static final int MAX_NOTES = 500;
    public static final int MAX_REASON = 500;
    /** Largest amount numeric(14, 2) holds. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    /** Methods whose reference (UTR, cheque number, approval code ...) may be left empty. */
    public static final Set<PaymentMethod> REFERENCE_OPTIONAL = Set.of(PaymentMethod.CASH);

    /** 12 or more digits in a row, ignoring spaces and hyphens: possibly a full card number. */
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\d{12,}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private PaymentRules() {
    }

    /**
     * Ex-GST part of a receipt (BR-M3): {@code amount × taxable / grand total}, HALF_UP to the paisa. The receipt that
     * settles the balance exactly takes the remainder, {@code taxable − Σ ex-GST already recorded}, so a fully paid
     * invoice realises exactly its taxable value (P10-05). The result stays between 0 and the amount.
     */
    public static BigDecimal netAmount(BigDecimal amount, BigDecimal taxable, BigDecimal grandTotal, BigDecimal balance,
                                       BigDecimal netRecorded) {
        if (grandTotal.signum() <= 0) {
            throw new IllegalArgumentException("Payments need a positive invoice total");
        }
        BigDecimal net = amount.compareTo(balance) == 0
                ? taxable.subtract(netRecorded)
                : amount.multiply(taxable).divide(grandTotal, 2, RoundingMode.HALF_UP);
        return net.max(BigDecimal.ZERO).min(amount).setScale(2, RoundingMode.HALF_UP);
    }

    /** Trimmed reference with inner whitespace collapsed; null when blank. */
    public static String normalizeReference(String reference) {
        if (reference == null) {
            return null;
        }
        String r = WHITESPACE.matcher(reference.strip()).replaceAll(" ");
        return r.isEmpty() ? null : r;
    }

    /** Key used to compare references: case-insensitive. */
    public static String referenceKey(String reference) {
        String r = normalizeReference(reference);
        return r == null ? null : r.toLowerCase(Locale.ROOT);
    }

    /** True when the text contains what may be a full card number, which must never be stored. */
    public static boolean looksLikeCardNumber(String reference) {
        return reference != null && LONG_DIGIT_RUN.matcher(reference.replaceAll("[\\s-]", "")).find();
    }

    /** References are recorded in audit entries as "••" plus their last four characters. */
    public static String maskReference(String reference) {
        if (reference == null) {
            return null;
        }
        return reference.length() <= 4 ? "••••" : "••" + reference.substring(reference.length() - 4);
    }

    /** Fingerprint of the recorded inputs, to recognise a resubmission of the same idempotency key. */
    public static String requestHash(UUID invoiceId, LocalDate date, BigDecimal amount, PaymentMethod method,
                                     String reference, String notes) {
        String canonical = String.join("\u001f", String.valueOf(invoiceId), String.valueOf(date),
                amount == null ? "" : amount.stripTrailingZeros().toPlainString(), String.valueOf(method),
                reference == null ? "" : reference, notes == null ? "" : notes);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
