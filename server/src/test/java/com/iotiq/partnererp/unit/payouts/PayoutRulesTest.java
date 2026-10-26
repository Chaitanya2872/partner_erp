package com.iotiq.partnererp.unit.payouts;

import com.iotiq.partnererp.modules.payouts.entity.PayoutStatus;
import com.iotiq.partnererp.modules.payouts.service.PayoutRules;
import com.iotiq.partnererp.modules.payouts.service.PayoutRules.TdsInput;
import com.iotiq.partnererp.modules.payouts.service.PayoutRules.TdsResult;
import com.iotiq.partnererp.modules.payouts.service.PayoutRules.TdsRule;
import com.iotiq.partnererp.modules.payouts.service.PayoutRules.TdsStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure payout rules: commission rounding, financial years, TDS against a configured rule (test values only, not
 * statutory ones) and the payout lifecycle.
 */
class PayoutRulesTest {

    /** Test configuration: 10% with a verified PAN, 20% without, threshold ₹1,000. Not a statutory rule. */
    private static final TdsRule RULE = new TdsRule("TEST", d("10"), d("20"), d("1000.00"));
    private static final TdsRule NO_PAN_RATE = new TdsRule("TEST", d("10"), null, d("1000.00"));

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static TdsResult tds(TdsRule rule, String commission, String priorCommission, String priorTaxed,
                                 boolean pan) {
        return PayoutRules.tds(rule, new TdsInput(d(commission), d(priorCommission), d(priorTaxed), pan));
    }

    @Test
    void commissionIsRoundedHalfUpToThePaisaWithTheInvoiceRate() {
        assertThat(PayoutRules.commission(d("5000.00"), d("8"))).isEqualByComparingTo("400.00");
        // 333.33 × 7.5% = 24.99975 → 25.00; 0.07 × 7.5% = 0.00525 → 0.01; 0.06 × 7.5% = 0.0045 → 0.00
        assertThat(PayoutRules.commission(d("333.33"), d("7.5"))).isEqualByComparingTo("25.00");
        assertThat(PayoutRules.commission(d("0.07"), d("7.5"))).isEqualByComparingTo("0.01");
        assertThat(PayoutRules.commission(d("0.06"), d("7.5"))).isEqualByComparingTo("0.00");
        assertThat(PayoutRules.commission(d("1234.56"), d("12.345"))).isEqualByComparingTo("152.41");
        assertThat(PayoutRules.commission(d("1000"), d("0"))).isEqualByComparingTo("0.00");
        assertThat(PayoutRules.commission(d("5000.00"), d("8")).scale()).isEqualTo(2);
    }

    @Test
    void theFinancialYearStartsOnTheFirstOfApril() {
        assertThat(PayoutRules.fyStart(LocalDate.of(2026, 3, 31))).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(PayoutRules.fyStart(LocalDate.of(2026, 4, 1))).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(PayoutRules.fyStart(LocalDate.of(2027, 1, 15))).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(PayoutRules.fyLabel(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
        assertThat(PayoutRules.fyLabel(LocalDate.of(2099, 4, 1))).isEqualTo("2099-00");
    }

    @Test
    void withoutAConfirmedConfigurationTdsIsNotCalculated() {
        TdsResult r = tds(null, "5000.00", "0", "0", true);
        assertThat(r.status()).isEqualTo(TdsStatus.CONFIG_INCOMPLETE);
        assertThat(r.tds()).isNull();
        assertThat(r.tdsBase()).isNull();
    }

    @Test
    void belowOrAtTheThresholdNothingIsDeducted() {
        TdsResult below = tds(RULE, "400.00", "0", "0", true);
        assertThat(below.status()).isEqualTo(TdsStatus.CALCULATED);
        assertThat(below.tds()).isEqualByComparingTo("0.00");
        assertThat(below.tdsBase()).isEqualByComparingTo("0.00");
        // Exactly at the threshold is not above it.
        assertThat(tds(RULE, "600.00", "400.00", "0", true).tds()).isEqualByComparingTo("0.00");
    }

    @Test
    void theCrossingPayoutCatchesUpOnTheYearsUntaxedCommission() {
        // ₹400 earlier this year, untaxed; ₹800 now: cumulative ₹1,200 > ₹1,000 → TDS on ₹1,200 = ₹120.
        TdsResult crossing = tds(RULE, "800.00", "400.00", "0", true);
        assertThat(crossing.tdsBase()).isEqualByComparingTo("1200.00");
        assertThat(crossing.tds()).isEqualByComparingTo("120.00");
        assertThat(crossing.ratePercent()).isEqualByComparingTo("10");
        // After that, only the new commission is taxed.
        TdsResult after = tds(RULE, "100.00", "1200.00", "1200.00", true);
        assertThat(after.tdsBase()).isEqualByComparingTo("100.00");
        assertThat(after.tds()).isEqualByComparingTo("10.00");
    }

    @Test
    void negativeAdjustmentsReduceTheBaseButNeverBelowZero() {
        // Earlier taxed ₹1,200, now −₹50 net (a reversal adjustment larger than new commission): base stays 0.
        TdsResult r = tds(RULE, "-50.00", "1200.00", "1200.00", true);
        assertThat(r.tdsBase()).isEqualByComparingTo("0.00");
        assertThat(r.tds()).isEqualByComparingTo("0.00");
        // ₹300 after a ₹100 reversal was carried in a previous payout: taxed base catches up only on the net increase.
        assertThat(tds(RULE, "300.00", "1100.00", "1200.00", true).tdsBase()).isEqualByComparingTo("200.00");
    }

    @Test
    void anUnverifiedPanUsesTheConfiguredRateOrBlocks() {
        TdsResult higher = tds(RULE, "2000.00", "0", "0", false);
        assertThat(higher.status()).isEqualTo(TdsStatus.CALCULATED);
        assertThat(higher.tds()).isEqualByComparingTo("400.00");
        TdsResult blocked = tds(NO_PAN_RATE, "2000.00", "0", "0", false);
        assertThat(blocked.status()).isEqualTo(TdsStatus.PAN_NOT_VERIFIED);
        assertThat(blocked.tds()).isNull();
    }

    @Test
    void tdsIsRoundedHalfUpToThePaisa() {
        TdsRule rule = new TdsRule("TEST", d("7.5"), null, d("0"));
        // 1234.57 × 7.5% = 92.59275 → 92.59; 1234.58 × 7.5% = 92.5935 → 92.59; 1234.60 × 7.5% = 92.595 → 92.60
        assertThat(tds(rule, "1234.57", "0", "0", true).tds()).isEqualByComparingTo("92.59");
        assertThat(tds(rule, "1234.60", "0", "0", true).tds()).isEqualByComparingTo("92.60");
    }

    @Test
    void theLifecycleOnlyMovesForward() {
        assertThat(PayoutStatus.DRAFT.canMoveTo(PayoutStatus.PENDING_APPROVAL)).isTrue();
        assertThat(PayoutStatus.DRAFT.canMoveTo(PayoutStatus.APPROVED)).isFalse();
        assertThat(PayoutStatus.PENDING_APPROVAL.canMoveTo(PayoutStatus.REJECTED)).isTrue();
        assertThat(PayoutStatus.APPROVED.canMoveTo(PayoutStatus.PAID)).isFalse();
        assertThat(PayoutStatus.PROCESSING.canMoveTo(PayoutStatus.PAID)).isTrue();
        assertThat(PayoutStatus.PROCESSING.canMoveTo(PayoutStatus.CANCELLED)).isFalse();
        for (PayoutStatus done : new PayoutStatus[]{PayoutStatus.PAID, PayoutStatus.REJECTED, PayoutStatus.CANCELLED,
                PayoutStatus.FAILED}) {
            assertThat(done.isFinal()).isTrue();
            for (PayoutStatus next : PayoutStatus.values()) {
                assertThat(done.canMoveTo(next)).isFalse();
            }
        }
        assertThat(PayoutStatus.PAID.isLive()).isTrue();
        assertThat(PayoutStatus.CANCELLED.isLive()).isFalse();
    }

    @Test
    void theRequestFingerprintIgnoresTrailingZeros() {
        UUID partner = UUID.randomUUID();
        assertThat(PayoutRules.requestHash(partner, d("10.50"), "x"))
                .isEqualTo(PayoutRules.requestHash(partner, d("10.5"), "x"))
                .isNotEqualTo(PayoutRules.requestHash(partner, d("10.5"), "y"));
        assertThat(PayoutRules.like("50%_off\\")).isEqualTo("%50\\%\\_off\\\\%");
    }
}
