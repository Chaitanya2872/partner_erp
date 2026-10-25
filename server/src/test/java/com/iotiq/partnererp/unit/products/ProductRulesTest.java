package com.iotiq.partnererp.unit.products;

import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.products.entity.ProductCategory;
import com.iotiq.partnererp.modules.products.service.ProductRules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRulesTest {

    @Test
    void discountFollowsBrI6() {
        assertThat(ProductRules.discountPercent(new BigDecimal("8000"), new BigDecimal("10000"))).isEqualTo(20);
        assertThat(ProductRules.discountPercent(new BigDecimal("8000"), new BigDecimal("12000"))).isEqualTo(33);
        // 1 − 5749/7665 = 24.997 % → 25 (rounded, halves up as Math.round).
        assertThat(ProductRules.discountPercent(new BigDecimal("5749"), new BigDecimal("7665"))).isEqualTo(25);
        assertThat(ProductRules.discountPercent(new BigDecimal("1"), new BigDecimal("2"))).isEqualTo(50);
        assertThat(ProductRules.discountPercent(new BigDecimal("105"), new BigDecimal("100"))).isEqualTo(-5);
        // Math.round(-2.5) is -2 in JS; the server matches.
        assertThat(ProductRules.discountPercent(new BigDecimal("102.5"), new BigDecimal("100"))).isEqualTo(-2);
        assertThat(ProductRules.discountPercent(new BigDecimal("5749"), null)).isZero();
        assertThat(ProductRules.discountPercent(new BigDecimal("5749"), BigDecimal.ZERO)).isZero();
    }

    @Test
    void b2cPriceIsMissingWhenNullOrZero() {
        assertThat(ProductRules.b2cPriceMissing(null)).isTrue();
        assertThat(ProductRules.b2cPriceMissing(new BigDecimal("0.00"))).isTrue();
        assertThat(ProductRules.b2cPriceMissing(new BigDecimal("0.01"))).isFalse();
    }

    @Test
    void categoriesMatchByCodeOrLabelButNeverDefault() {
        assertThat(ProductRules.category("SMART_GLIDE")).contains(ProductCategory.SMART_GLIDE);
        assertThat(ProductRules.category("smart relay & sensing")).contains(ProductCategory.SMART_RELAY_SENSING);
        assertThat(ProductRules.category("Locks and Entry")).contains(ProductCategory.LOCKS_ENTRY);
        assertThat(ProductRules.category(" Services ")).contains(ProductCategory.SERVICES);
        assertThat(ProductRules.category("Switching")).isEmpty();
        assertThat(ProductRules.category("Other")).isEmpty();
        assertThat(ProductRules.category("")).isEmpty();
    }

    @Test
    void skuAndHsnFormats() {
        List<FieldError> errors = new ArrayList<>();
        assertThat(ProductRules.sku("  SRC-6P1 ", errors)).isEqualTo("SRC-6P1");
        assertThat(ProductRules.sku("A.B_C/D+E", errors)).isEqualTo("A.B_C/D+E");
        assertThat(errors).isEmpty();
        ProductRules.sku("=HYPERLINK(1)", errors);
        ProductRules.sku("two words", errors);
        ProductRules.sku(" ", errors);
        assertThat(errors).extracting(FieldError::message).containsExactly(
                "Use letters, digits and . _ / + - only, starting with a letter or digit.",
                "Use letters, digits and . _ / + - only, starting with a letter or digit.",
                "SKU is required.");

        errors.clear();
        assertThat(ProductRules.hsn("8536", errors)).isEqualTo("8536");
        assertThat(ProductRules.hsn("998314", errors)).isEqualTo("998314");
        assertThat(ProductRules.hsn("8536 5090", errors)).isEqualTo("85365090");
        assertThat(ProductRules.hsn("", errors)).isNull();
        assertThat(errors).isEmpty();
        ProductRules.hsn("853", errors);
        ProductRules.hsn("85365", errors);
        ProductRules.hsn("8536509012", errors);
        assertThat(errors).hasSize(3);
    }

    @Test
    void amountsAndGst() {
        List<FieldError> errors = new ArrayList<>();
        assertThat(ProductRules.amount(new BigDecimal("5749"), "p", errors)).isEqualByComparingTo("5749.00")
                .hasScaleOf(2);
        assertThat(ProductRules.amount(null, "p", errors)).isNull();
        assertThat(ProductRules.amount(new BigDecimal("999999999999.99"), "p", errors)).isNotNull();
        assertThat(ProductRules.gst(new BigDecimal("12.5"), errors)).isEqualByComparingTo("12.500");
        assertThat(ProductRules.gst(BigDecimal.ZERO, errors)).isEqualByComparingTo("0");
        assertThat(errors).isEmpty();
        ProductRules.amount(new BigDecimal("-0.01"), "p", errors);
        ProductRules.amount(new BigDecimal("1.005"), "p", errors);
        ProductRules.amount(new BigDecimal("1000000000000"), "p", errors);
        ProductRules.gst(new BigDecimal("100.001"), errors);
        ProductRules.gst(new BigDecimal("5.0001"), errors);
        ProductRules.gst(null, errors);
        assertThat(errors).extracting(FieldError::message).containsExactly("Can't be negative.",
                "Use at most 2 decimal places.", "Must be at most 999,999,999,999.99.",
                "GST % must be between 0 and 100.", "Use at most 3 decimal places.", "GST % is required.");
    }

    @Test
    void aliasesAreTrimmedDeduplicatedAndCapped() {
        List<FieldError> errors = new ArrayList<>();
        assertThat(ProductRules.aliases(List.of(" Drive  A ", "drive a", "", "Drive B"), errors))
                .containsExactly("Drive A", "Drive B");
        assertThat(ProductRules.aliases(null, errors)).isEmpty();
        assertThat(errors).isEmpty();
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            many.add("Name " + i);
        }
        ProductRules.aliases(many, errors);
        assertThat(errors).extracting(FieldError::message).containsExactly("Use at most 20 alternative names.");
        errors.clear();
        ProductRules.aliases(List.of(String.join("", Collections.nCopies(301, "x"))), errors);
        assertThat(errors).extracting(FieldError::message).containsExactly("Must be at most 300 characters.");
    }

    @Test
    void warningsAreAdvisory() {
        assertThat(ProductRules.warnings("Lock", "lock", new BigDecimal("10"), null, null))
                .containsExactly("The partner-facing name is the same as the B2C name.", "B2C price needed.");
        assertThat(ProductRules.warnings("A", "B", new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("130")))
                .containsExactly("The partner price is above the B2C price.", "The partner price is below the landed cost.");
        assertThat(ProductRules.warnings("A", "B", new BigDecimal("80"), new BigDecimal("100"), new BigDecimal("50")))
                .isEmpty();
    }
}
