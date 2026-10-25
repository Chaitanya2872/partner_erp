package com.iotiq.partnererp.unit.partners;

import com.iotiq.partnererp.modules.partners.entity.TierReviewAction;
import com.iotiq.partnererp.modules.partners.service.TierRules;
import com.iotiq.partnererp.modules.partners.service.TierRules.Outcome;
import com.iotiq.partnererp.modules.partners.service.TierRules.Threshold;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier arithmetic against the source defaults (BUSINESS_RULES §2.1, §3.1, BR-P13).
 */
class TierRulesTest {

    private static final List<Threshold> DEFAULT_TIERS = List.of(
            new Threshold(1, new BigDecimal("0.00")),
            new Threshold(2, new BigDecimal("1000000.00")),
            new Threshold(3, new BigDecimal("3000000.00")),
            new Threshold(4, new BigDecimal("10000000.00")));

    @ParameterizedTest(name = "₹{0} → tier {1}")
    @CsvSource({
            "0, 1",
            "-5000.00, 1",          // refunds can push revenue below zero
            "999999.99, 1",
            "1000000.00, 2",        // the minimum itself qualifies (min <= rev)
            "2999999.99, 2",
            "3000000.00, 3",
            "9999999.99, 3",
            "10000000.00, 4",
            "250000000.00, 4"})
    void tierForRevenueIsTheHighestTierWhoseMinimumIsMet(String revenue, int expected) {
        assertThat(TierRules.tierForRevenue(DEFAULT_TIERS, new BigDecimal(revenue))).isEqualTo(expected);
    }

    @Test
    void programmeAlwaysQualifiesForStrategic() {
        assertThat(TierRules.calculatedLevel(DEFAULT_TIERS, BigDecimal.ZERO, true)).isEqualTo(4);
        assertThat(TierRules.calculatedLevel(DEFAULT_TIERS, BigDecimal.ZERO, false)).isEqualTo(1);
    }

    @Test
    void upgradesJumpStraightToTheTargetAndClearGrace() {
        assertThat(TierRules.review(1, false, 4))
                .isEqualTo(new Outcome(TierReviewAction.UPGRADE, 1, 4, 4, false));
        assertThat(TierRules.review(2, true, 3))
                .isEqualTo(new Outcome(TierReviewAction.UPGRADE, 2, 3, 3, false));
    }

    @Test
    void aFirstShortfallStartsGraceWithoutChangingTheTier() {
        assertThat(TierRules.review(3, false, 1))
                .isEqualTo(new Outcome(TierReviewAction.GRACE, 3, 3, 1, true));
    }

    @Test
    void aSecondShortfallStepsDownOneTierOnlyEvenWhenFarBelow() {
        assertThat(TierRules.review(4, true, 1))
                .isEqualTo(new Outcome(TierReviewAction.DOWNGRADE, 4, 3, 1, false));
    }

    @Test
    void meetingTheThresholdAgainDuringGraceRecovers() {
        assertThat(TierRules.review(2, true, 2))
                .isEqualTo(new Outcome(TierReviewAction.RECOVERED, 2, 2, 2, false));
    }

    @Test
    void onTargetWithoutGraceIsNoChange() {
        Outcome outcome = TierRules.review(2, false, 2);
        assertThat(outcome.changes()).isFalse();
        assertThat(outcome.toLevel()).isEqualTo(2);
    }

    @Test
    void fullCycleFromStrategicDownToElite() {
        // Review 1: below threshold → grace. Review 2: still below → step down one tier.
        Outcome first = TierRules.review(4, false, 2);
        Outcome second = TierRules.review(first.toLevel(), first.graceAfter(), 2);
        assertThat(first.action()).isEqualTo(TierReviewAction.GRACE);
        assertThat(second.action()).isEqualTo(TierReviewAction.DOWNGRADE);
        assertThat(second.toLevel()).isEqualTo(3);
        assertThat(second.graceAfter()).isFalse();
    }

    @ParameterizedTest(name = "₹{0} of ₹{1} → {2}%, ₹{3} to go")
    @CsvSource({
            "0, 1000000.00, 0, 1000000.00",
            "250000.00, 1000000.00, 25, 750000.00",
            "994999.99, 1000000.00, 99, 5000.01",
            "995000.00, 1000000.00, 100, 5000.00",   // round() reaches 100 before the minimum is met
            "1500000.00, 1000000.00, 100, 0.00",
            "-20000.00, 1000000.00, 0, 1020000.00"})
    void progressToTheNextTier(String revenue, String minimum, int percent, String remaining) {
        BigDecimal rev = new BigDecimal(revenue);
        BigDecimal min = new BigDecimal(minimum);
        assertThat(TierRules.progressPercent(rev, min)).isEqualTo(percent);
        assertThat(TierRules.remaining(rev, min)).isEqualByComparingTo(remaining);
    }

    @Test
    void nextTierIsTheLowestAboveTheCurrentOne() {
        assertThat(TierRules.nextTier(DEFAULT_TIERS, 1)).map(Threshold::level).contains(2);
        assertThat(TierRules.nextTier(DEFAULT_TIERS, 4)).isEmpty();
    }
}
