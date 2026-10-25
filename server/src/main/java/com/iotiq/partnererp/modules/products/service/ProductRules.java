package com.iotiq.partnererp.modules.products.service;

import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.products.entity.ProductCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Field rules shared by the product form and the CSV import (BUSINESS_RULES §5, P7-05). Pure functions: every
 * method trims, validates and either returns the normalized value or adds a {@link FieldError}.
 */
public final class ProductRules {

    public static final String DEFAULT_UNIT = "nos";
    public static final int MAX_SKU = 50;
    public static final int MAX_NAME = 300;
    public static final int MAX_UNIT = 20;
    public static final int MAX_ALIASES = 20;
    /** numeric(14,2): twelve integer digits. */
    public static final BigDecimal MAX_PRICE = new BigDecimal("999999999999.99");
    public static final BigDecimal MAX_GST = new BigDecimal("100");

    /** Letters and digits plus . _ / + - ; must start with a letter or digit (also keeps CSV formulas out). */
    private static final Pattern SKU = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/+-]*");
    /** HSN (goods) or SAC (services): 4, 6 or 8 digits. */
    private static final Pattern HSN = Pattern.compile("[0-9]{4}([0-9]{2}){0,2}");

    private ProductRules() {
    }

    /** Trimmed text with inner whitespace collapsed; blank becomes null. */
    public static String text(String value, String field, int max, List<FieldError> errors) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            errors.add(new FieldError(field, "Must be at most " + max + " characters."));
        }
        return trimmed;
    }

    public static String sku(String value, List<FieldError> errors) {
        String sku = text(value, "sku", MAX_SKU, errors);
        if (sku == null) {
            errors.add(new FieldError("sku", "SKU is required."));
        } else if (!SKU.matcher(sku).matches()) {
            errors.add(new FieldError("sku", "Use letters, digits and . _ / + - only, starting with a letter or digit."));
        }
        return sku;
    }

    public static String hsn(String value, List<FieldError> errors) {
        String hsn = text(value, "hsn", 20, errors);
        if (hsn != null) {
            hsn = hsn.replace(" ", "");
            if (!HSN.matcher(hsn).matches()) {
                errors.add(new FieldError("hsn", "Enter an HSN or SAC code of 4, 6 or 8 digits."));
            }
        }
        return hsn;
    }

    public static String unit(String value, List<FieldError> errors) {
        String unit = text(value, "unit", MAX_UNIT, errors);
        return unit == null ? DEFAULT_UNIT : unit;
    }

    public static String requiredName(String value, String field, String label, List<FieldError> errors) {
        String name = text(value, field, MAX_NAME, errors);
        if (name == null) {
            errors.add(new FieldError(field, label + " is required."));
        }
        return name;
    }

    /** Non-negative amount with at most two decimals, below {@link #MAX_PRICE}; null stays null. */
    public static BigDecimal amount(BigDecimal value, String field, List<FieldError> errors) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            errors.add(new FieldError(field, "Can't be negative."));
        } else if (value.stripTrailingZeros().scale() > 2) {
            errors.add(new FieldError(field, "Use at most 2 decimal places."));
        } else if (value.compareTo(MAX_PRICE) > 0) {
            errors.add(new FieldError(field, "Must be at most 999,999,999,999.99."));
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** GST percentage between 0 and 100 with at most three decimals. */
    public static BigDecimal gst(BigDecimal value, List<FieldError> errors) {
        if (value == null) {
            errors.add(new FieldError("gstPercent", "GST % is required."));
            return null;
        }
        if (value.signum() < 0 || value.compareTo(MAX_GST) > 0) {
            errors.add(new FieldError("gstPercent", "GST % must be between 0 and 100."));
        } else if (value.stripTrailingZeros().scale() > 3) {
            errors.add(new FieldError("gstPercent", "Use at most 3 decimal places."));
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    /** Trimmed, de-duplicated (case-insensitively) alternative partner names, in input order. */
    public static List<String> aliases(List<String> values, List<FieldError> errors) {
        if (values == null) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            String alias = text(value, "aliases", MAX_NAME, errors);
            if (alias != null) {
                unique.putIfAbsent(alias.toLowerCase(Locale.ROOT), alias);
            }
        }
        if (unique.size() > MAX_ALIASES) {
            errors.add(new FieldError("aliases", "Use at most " + MAX_ALIASES + " alternative names."));
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * A category from its code ({@code SMART_GLIDE}) or its displayed label ({@code Smart Glide}), ignoring case,
     * punctuation and "&" versus "and". Unknown values give empty (D-18: rejected, never defaulted).
     */
    public static Optional<ProductCategory> category(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String key = categoryKey(value);
        for (ProductCategory c : ProductCategory.values()) {
            if (categoryKey(c.name()).equals(key) || categoryKey(c.getLabel()).equals(key)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    private static String categoryKey(String value) {
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace("&", " and ");
        return ascii.replaceAll("[^a-z0-9]", "");
    }

    /** BR-I6: round((1 − partner/B2C) × 100) when the B2C price is set, otherwise 0. Halves round up, as in JS. */
    public static int discountPercent(BigDecimal partnerPrice, BigDecimal b2cPrice) {
        if (b2cPrice == null || b2cPrice.signum() <= 0 || partnerPrice == null) {
            return 0;
        }
        BigDecimal pct = BigDecimal.ONE.subtract(partnerPrice.divide(b2cPrice, 10, RoundingMode.HALF_EVEN))
                .multiply(BigDecimal.valueOf(100));
        return pct.add(new BigDecimal("0.5")).setScale(0, RoundingMode.FLOOR).intValueExact();
    }

    /** BR-I5: "B2C price needed" when the B2C price is missing or zero. */
    public static boolean b2cPriceMissing(BigDecimal b2cPrice) {
        return b2cPrice == null || b2cPrice.signum() == 0;
    }

    /** Non-blocking warnings shown on the form and in the import preview. */
    public static List<String> warnings(String partnerName, String b2cName, BigDecimal partnerPrice,
                                        BigDecimal b2cPrice, BigDecimal costPrice) {
        List<String> warnings = new ArrayList<>();
        if (partnerName != null && partnerName.equalsIgnoreCase(b2cName)) {
            warnings.add("The partner-facing name is the same as the B2C name.");
        }
        if (b2cPriceMissing(b2cPrice)) {
            warnings.add("B2C price needed.");
        } else if (partnerPrice != null && partnerPrice.compareTo(b2cPrice) > 0) {
            warnings.add("The partner price is above the B2C price.");
        }
        if (costPrice != null && costPrice.signum() > 0 && partnerPrice != null && partnerPrice.compareTo(costPrice) < 0) {
            warnings.add("The partner price is below the landed cost.");
        }
        return warnings;
    }
}
