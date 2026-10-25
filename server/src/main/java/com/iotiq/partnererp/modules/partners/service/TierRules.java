package com.iotiq.partnererp.modules.partners.service;

import com.iotiq.partnererp.modules.partners.entity.TierReviewAction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Tier arithmetic from the source (BUSINESS_RULES §2.1, BR-P13, §3.1). Pure functions, no I/O.
 */
public final class TierRules {

    /** Tier assigned to every programme partner (BR-P5). */
    public static final int PROGRAMME_LEVEL = 4;
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 4;

    private TierRules() {
    }

    /** A tier threshold: level 1–4 and the minimum paid revenue (trailing 12 months, ex-GST). */
    public record Threshold(int level, BigDecimal minimumRevenue) {
    }

    /**
     * Result of a tier review for one partner.
     *
     * @param action    null when nothing changes
     * @param toLevel   the tier after the review
     * @param graceAfter the downgrade-grace flag after the review
     */
    public record Outcome(TierReviewAction action, int fromLevel, int toLevel, int targetLevel, boolean graceAfter) {

        public boolean changes() {
            return action != null;
        }
    }

    /** {@code tierForRev}: the highest tier whose minimum is at or below {@code revenue}. */
    public static int tierForRevenue(List<Threshold> tiers, BigDecimal revenue) {
        return tiers.stream()
                .filter(t -> t.minimumRevenue().compareTo(revenue) <= 0)
                .mapToInt(Threshold::level)
                .max()
                .orElse(MIN_LEVEL);
    }

    /** The tier a partner qualifies for: Strategic on the programme, otherwise by revenue. */
    public static int calculatedLevel(List<Threshold> tiers, BigDecimal revenue, boolean programme) {
        return programme ? PROGRAMME_LEVEL : tierForRevenue(tiers, revenue);
    }

    /**
     * {@code reviewCp}: upgrades jump straight to the target; a shortfall first starts a grace period and only
     * the next review below threshold steps down one tier; meeting the threshold again clears grace.
     */
    public static Outcome review(int currentLevel, boolean grace, int targetLevel) {
        if (targetLevel > currentLevel) {
            return new Outcome(TierReviewAction.UPGRADE, currentLevel, targetLevel, targetLevel, false);
        }
        if (targetLevel < currentLevel) {
            return grace
                    ? new Outcome(TierReviewAction.DOWNGRADE, currentLevel, currentLevel - 1, targetLevel, false)
                    : new Outcome(TierReviewAction.GRACE, currentLevel, currentLevel, targetLevel, true);
        }
        return grace
                ? new Outcome(TierReviewAction.RECOVERED, currentLevel, currentLevel, targetLevel, false)
                : new Outcome(null, currentLevel, currentLevel, targetLevel, false);
    }

    /** The next tier above {@code currentLevel}, if any. */
    public static Optional<Threshold> nextTier(List<Threshold> tiers, int currentLevel) {
        return tiers.stream()
                .filter(t -> t.level() > currentLevel)
                .min(Comparator.comparingInt(Threshold::level));
    }

    /** BR-P13: {@code min(100, round(revenue / next.min × 100))}, never negative. */
    public static int progressPercent(BigDecimal revenue, BigDecimal nextMinimum) {
        if (nextMinimum.signum() <= 0) {
            return 100;
        }
        BigDecimal percent = revenue.multiply(BigDecimal.valueOf(100)).divide(nextMinimum, 0, RoundingMode.HALF_UP);
        return Math.max(0, Math.min(100, percent.intValue()));
    }

    /** Amount still needed to reach {@code nextMinimum}; zero once reached. */
    public static BigDecimal remaining(BigDecimal revenue, BigDecimal nextMinimum) {
        BigDecimal gap = nextMinimum.subtract(revenue);
        return gap.signum() > 0 ? gap.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }
}
