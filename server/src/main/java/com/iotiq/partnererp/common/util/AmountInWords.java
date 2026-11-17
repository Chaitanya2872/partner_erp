package com.iotiq.partnererp.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount in Indian words (lakh and crore), for printed documents: {@code 1,23,456.50} is
 * "Rupees One Lakh Twenty Three Thousand Four Hundred Fifty Six and Fifty Paise Only".
 */
public final class AmountInWords {

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen",
            "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty",
            "Ninety"};

    private AmountInWords() {
    }

    public static String rupees(BigDecimal amount) {
        BigDecimal value = amount.abs().setScale(2, RoundingMode.HALF_UP);
        long paise = value.movePointRight(2).longValueExact();
        long rupees = paise / 100;
        long ps = paise % 100;
        StringBuilder out = new StringBuilder(amount.signum() < 0 ? "Minus " : "").append("Rupees ")
                .append(rupees == 0 ? "Zero" : words(rupees));
        if (ps > 0) {
            out.append(" and ").append(below100((int) ps)).append(" Paise");
        }
        return out.append(" Only").toString();
    }

    private static String words(long n) {
        StringBuilder parts = new StringBuilder();
        long crore = n / 10_000_000L;
        n %= 10_000_000L;
        if (crore > 0) {
            // Crores keep counting in hundreds of crore, as Indian usage does.
            parts.append(crore >= 100 ? words(crore) : below100((int) crore)).append(" Crore");
        }
        long lakh = n / 100_000L;
        n %= 100_000L;
        append(parts, lakh, "Lakh");
        long thousand = n / 1000L;
        n %= 1000L;
        append(parts, thousand, "Thousand");
        if (n > 0) {
            if (!parts.isEmpty()) {
                parts.append(' ');
            }
            parts.append(below1000((int) n));
        }
        return parts.toString();
    }

    private static void append(StringBuilder parts, long count, String unit) {
        if (count > 0) {
            if (!parts.isEmpty()) {
                parts.append(' ');
            }
            parts.append(below100((int) count)).append(' ').append(unit);
        }
    }

    private static String below1000(int n) {
        String rest = n % 100 == 0 ? "" : below100(n % 100);
        if (n < 100) {
            return rest;
        }
        return ONES[n / 100] + " Hundred" + (rest.isEmpty() ? "" : " " + rest);
    }

    private static String below100(int n) {
        return n < 20 ? ONES[n] : TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10]);
    }
}
