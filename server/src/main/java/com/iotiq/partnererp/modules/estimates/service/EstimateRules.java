package com.iotiq.partnererp.modules.estimates.service;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.estimates.entity.EstimateStatus;
import com.iotiq.partnererp.modules.leads.entity.Lead;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pure estimate rules: input validation (§6.1 PROPOSED bounds), the default referral fee (BR-E3) and the derived
 * "expired" flag (D-29). Shared by the service and its unit tests.
 */
public final class EstimateRules {

    public static final int MAX_LINES = 100;
    public static final int MAX_NOTES = 5000;
    public static final int MIN_REVISION_REASON = 10;
    public static final int MAX_REVISION_REASON = 500;
    static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999.999");
    static final BigDecimal MAX_MONEY = new BigDecimal("999999999999.99");
    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private EstimateRules() {
    }

    /** Required, greater than 0, at most 3 decimals. Returns the value at scale 3, or null after adding an error. */
    public static BigDecimal quantity(BigDecimal value, String field, List<FieldError> errors) {
        if (value == null) {
            errors.add(new FieldError(field, "Enter a quantity."));
            return null;
        }
        if (value.signum() <= 0) {
            errors.add(new FieldError(field, "Quantity must be greater than 0."));
            return null;
        }
        if (value.stripTrailingZeros().scale() > 3 || value.compareTo(MAX_QUANTITY) > 0) {
            errors.add(new FieldError(field, "Enter a quantity with at most 3 decimal places."));
            return null;
        }
        return value.setScale(3);
    }

    /** At least 0, at most 2 decimals. Returns the value at scale 2, or null after adding an error. */
    public static BigDecimal rate(BigDecimal value, String field, List<FieldError> errors) {
        if (value == null) {
            errors.add(new FieldError(field, "Enter a rate."));
            return null;
        }
        if (value.signum() < 0) {
            errors.add(new FieldError(field, "The rate can't be negative."));
            return null;
        }
        if (value.stripTrailingZeros().scale() > 2 || value.compareTo(MAX_MONEY) > 0) {
            errors.add(new FieldError(field, "Enter an amount in rupees with at most 2 decimal places."));
            return null;
        }
        return value.setScale(2);
    }

    /** 0 to 100 with at most 3 decimals (discount, GST, referral fee). Returns scale 3, or null after an error. */
    public static BigDecimal percent(BigDecimal value, String field, String label, List<FieldError> errors) {
        if (value == null) {
            errors.add(new FieldError(field, "Enter the " + label + "."));
            return null;
        }
        if (value.signum() < 0 || value.compareTo(HUNDRED) > 0 || value.stripTrailingZeros().scale() > 3) {
            errors.add(new FieldError(field, capitalize(label) + " must be between 0 and 100, with at most 3 decimals."));
            return null;
        }
        return value.setScale(3);
    }

    /** Trimmed text (line breaks kept); blank becomes null. */
    public static String text(String value, String field, int max, List<FieldError> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > max) {
            errors.add(new FieldError(field, "Must be at most " + max + " characters."));
        }
        return trimmed;
    }

    /** BR-E3: no fee when the partner buys; otherwise the lead's locked rate, falling back to the partner's tier rate. */
    public static BigDecimal defaultFee(BillTo billTo, Lead lead) {
        if (billTo == BillTo.PARTNER) {
            return BigDecimal.ZERO.setScale(3);
        }
        BigDecimal fee = lead.getLockedFeePercent() != null ? lead.getLockedFeePercent()
                : lead.getPartner().getTier().getCommissionPercent();
        return fee.setScale(3);
    }

    /** D-29: a sent estimate whose validity ended before today. Never stored. */
    public static boolean expired(EstimateStatus status, LocalDate validUntil, LocalDate today) {
        return status == EstimateStatus.SENT && validUntil != null && validUntil.isBefore(today);
    }

    private static String capitalize(String label) {
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
