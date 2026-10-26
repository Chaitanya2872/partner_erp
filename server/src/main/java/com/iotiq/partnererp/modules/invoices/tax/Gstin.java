package com.iotiq.partnererp.modules.invoices.tax;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * GSTIN checks: the 15-character structure (state code, PAN, entity number, check character) and the published
 * mod-36 check character. A valid GSTIN only proves the number is well formed, not that it is registered.
 */
public final class Gstin {

    private static final Pattern FORMAT = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]{3}$");
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private Gstin() {
    }

    /** Upper-cased and trimmed; blank becomes null. */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    /** Null when {@code gstin} (already normalized) is valid; otherwise the reason. */
    public static String problem(String gstin) {
        if (!FORMAT.matcher(gstin).matches()) {
            return "Enter a 15-character GSTIN, for example 36AABCI1234A1ZC.";
        }
        if (GstState.byCode(gstin.substring(0, 2)).isEmpty()) {
            return "The GSTIN starts with " + gstin.substring(0, 2) + ", which isn't a state code.";
        }
        if (checkCharacter(gstin) != gstin.charAt(14)) {
            return "This GSTIN's check character doesn't match. Check it for typing mistakes.";
        }
        return null;
    }

    static char checkCharacter(String gstin) {
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int product = ALPHABET.indexOf(gstin.charAt(i)) * (i % 2 == 0 ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        return ALPHABET.charAt((36 - sum % 36) % 36);
    }
}
