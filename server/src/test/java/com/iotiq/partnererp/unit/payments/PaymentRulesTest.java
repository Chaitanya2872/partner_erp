package com.iotiq.partnererp.unit.payments;

import com.iotiq.partnererp.modules.payments.entity.PaymentMethod;
import com.iotiq.partnererp.modules.payments.service.PaymentRules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure payment rules: ex-GST amounts, references, masking and the idempotency fingerprint. */
class PaymentRulesTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void theExGstPartIsProportionalAndRoundedHalfUpToThePaisa() {
        // 500 × 1000 / 1180 = 423.7288… → 423.73
        assertThat(PaymentRules.netAmount(d("500.00"), d("1000.00"), d("1180.00"), d("1180.00"), d("0")))
                .isEqualByComparingTo("423.73");
        // 0.59 × 1000 / 1180 = 0.5 exactly → 0.50; 0.01 × 1000 / 1180 = 0.00847 → 0.01
        assertThat(PaymentRules.netAmount(d("0.59"), d("1000.00"), d("1180.00"), d("1180.00"), d("0")))
                .isEqualByComparingTo("0.50");
        assertThat(PaymentRules.netAmount(d("0.01"), d("1000.00"), d("1180.00"), d("1180.00"), d("0")))
                .isEqualByComparingTo("0.01");
        // A 5% line: 1050 × 1000 / 1050 = 1000
        assertThat(PaymentRules.netAmount(d("1050.00"), d("1000.00"), d("1050.00"), d("2000.00"), d("0")))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void theSettlingReceiptTakesTheRemainderSoTheTaxableValueIsRealisedExactly() {
        // Three receipts of 393.33 + 393.33 + 393.34 on 1180 (taxable 1000): 333.33 + 333.33 + remainder 333.34.
        BigDecimal first = PaymentRules.netAmount(d("393.33"), d("1000.00"), d("1180.00"), d("1180.00"), d("0"));
        BigDecimal second = PaymentRules.netAmount(d("393.33"), d("1000.00"), d("1180.00"), d("786.67"), first);
        BigDecimal third = PaymentRules.netAmount(d("393.34"), d("1000.00"), d("1180.00"), d("393.34"),
                first.add(second));
        assertThat(first).isEqualByComparingTo("333.33");
        assertThat(second).isEqualByComparingTo("333.33");
        assertThat(third).isEqualByComparingTo("333.34");
        assertThat(first.add(second).add(third)).isEqualByComparingTo("1000.00");
        // The remainder is kept between 0 and the amount.
        assertThat(PaymentRules.netAmount(d("1.00"), d("1000.00"), d("1180.00"), d("1.00"), d("1005.00")))
                .isEqualByComparingTo("0.00");
        assertThat(PaymentRules.netAmount(d("1.00"), d("1000.00"), d("1180.00"), d("1.00"), d("0")))
                .isEqualByComparingTo("1.00");
    }

    @Test
    void aZeroTotalInvoiceTakesNoPayment() {
        assertThatThrownBy(() -> PaymentRules.netAmount(d("1"), d("0"), d("0"), d("0"), d("0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void referencesAreTrimmedComparedWithoutCaseAndMasked() {
        assertThat(PaymentRules.normalizeReference("  UTR   123  456 ")).isEqualTo("UTR 123 456");
        assertThat(PaymentRules.normalizeReference("   ")).isNull();
        assertThat(PaymentRules.referenceKey(" Utr 1 ")).isEqualTo("utr 1");
        assertThat(PaymentRules.maskReference("HDFC0001234567")).isEqualTo("••4567");
        assertThat(PaymentRules.maskReference("1234")).isEqualTo("••••");
        assertThat(PaymentRules.maskReference(null)).isNull();
    }

    @Test
    void possibleCardNumbersAreRecognised() {
        assertThat(PaymentRules.looksLikeCardNumber("4111 1111 1111 1111")).isTrue();
        assertThat(PaymentRules.looksLikeCardNumber("4111-1111-1111-1111")).isTrue();
        assertThat(PaymentRules.looksLikeCardNumber("123456789012")).isTrue();
        assertThat(PaymentRules.looksLikeCardNumber("AUTH 004512")).isFalse();
        assertThat(PaymentRules.looksLikeCardNumber("XX1111")).isFalse();
        assertThat(PaymentRules.looksLikeCardNumber("UTR SBIN12345678901")).isFalse();
    }

    @Test
    void theFingerprintIgnoresTrailingZerosButNotTheDetails() {
        UUID invoice = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 26);
        String a = PaymentRules.requestHash(invoice, date, d("100.00"), PaymentMethod.UPI, "R1", null);
        assertThat(PaymentRules.requestHash(invoice, date, d("100"), PaymentMethod.UPI, "R1", null)).isEqualTo(a);
        assertThat(PaymentRules.requestHash(invoice, date, d("100.01"), PaymentMethod.UPI, "R1", null)).isNotEqualTo(a);
        assertThat(PaymentRules.requestHash(invoice, date, d("100"), PaymentMethod.CASH, "R1", null)).isNotEqualTo(a);
        assertThat(PaymentRules.requestHash(invoice, date, d("100"), PaymentMethod.UPI, "R1", "n")).isNotEqualTo(a);
        assertThat(a).hasSize(64);
    }
}
