package com.iotiq.partnererp.unit.reports;

import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.modules.reports.service.ReportPeriod;
import com.iotiq.partnererp.modules.reports.service.ReportPeriod.Granularity;
import com.iotiq.partnererp.modules.reports.service.ReportPeriod.Preset;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private static ReportPeriod preset(Preset preset) {
        return ReportPeriod.resolve(preset, null, null, TODAY, Preset.THIS_FY);
    }

    @Test
    void presetsRunUpToTodayInclusive() {
        assertThat(preset(Preset.TODAY)).extracting(ReportPeriod::from, ReportPeriod::to).containsExactly(TODAY, TODAY);
        assertThat(preset(Preset.LAST_7_DAYS).from()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(preset(Preset.LAST_7_DAYS).days()).isEqualTo(7);
        assertThat(preset(Preset.LAST_30_DAYS).from()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(preset(Preset.THIS_MONTH).from()).isEqualTo(LocalDate.of(2026, 10, 1));
        ReportPeriod lastMonth = preset(Preset.LAST_MONTH);
        assertThat(lastMonth.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(lastMonth.to()).isEqualTo(LocalDate.of(2026, 9, 30));
        // Calendar quarters coincide with Indian FY quarters (Q3 of FY 2026-27 starts 1 October).
        assertThat(preset(Preset.THIS_QUARTER).from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(preset(Preset.THIS_FY).from()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void theFinancialYearStartsOnFirstAprilEvenInJanuaryToMarch() {
        ReportPeriod fy = ReportPeriod.resolve(Preset.THIS_FY, null, null, LocalDate.of(2027, 2, 14), Preset.THIS_FY);
        assertThat(fy.from()).isEqualTo(LocalDate.of(2026, 4, 1));
        ReportPeriod quarter = ReportPeriod.resolve(Preset.THIS_QUARTER, null, null, LocalDate.of(2027, 3, 31), Preset.THIS_FY);
        assertThat(quarter.from()).isEqualTo(LocalDate.of(2027, 1, 1));
        ReportPeriod lastMonth = ReportPeriod.resolve(Preset.LAST_MONTH, null, null, LocalDate.of(2027, 3, 1), Preset.THIS_FY);
        assertThat(lastMonth.from()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(lastMonth.to()).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void calendarYearPresetsRunJanuaryToDecember() {
        ReportPeriod thisYear = preset(Preset.THIS_YEAR);
        assertThat(thisYear.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(thisYear.to()).isEqualTo(TODAY);
        assertThat(thisYear.granularity()).isEqualTo(Granularity.MONTH);
        ReportPeriod lastYear = preset(Preset.LAST_YEAR);
        assertThat(lastYear.from()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(lastYear.to()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(lastYear.previousTo()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(lastYear.days()).isEqualTo(365);
        // On 1 January "this year" is one day, unlike the financial year.
        ReportPeriod newYear = ReportPeriod.resolve(Preset.THIS_YEAR, null, null, LocalDate.of(2027, 1, 1), Preset.THIS_FY);
        assertThat(newYear.days()).isEqualTo(1);
    }

    @Test
    void thePreviousPeriodHasTheSameLengthAndEndsTheDayBefore() {
        ReportPeriod p = ReportPeriod.resolve(null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), TODAY, Preset.THIS_FY);
        assertThat(p.preset()).isEqualTo(Preset.CUSTOM);
        assertThat(p.previousTo()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(p.previousFrom()).isEqualTo(LocalDate.of(2026, 1, 29));
    }

    @Test
    void timestampsUseIndianMidnightsWithAnExclusiveEnd() {
        ReportPeriod p = ReportPeriod.resolve(null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), TODAY, Preset.THIS_FY);
        assertThat(p.fromInstant()).isEqualTo(Instant.parse("2026-05-31T18:30:00Z"));
        assertThat(p.toInstant()).isEqualTo(Instant.parse("2026-06-30T18:30:00Z"));
    }

    @Test
    void shortRangesAreBucketedByDayAndLongerOnesByMonth() {
        ReportPeriod daily = ReportPeriod.resolve(null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), TODAY, Preset.THIS_FY);
        assertThat(daily.granularity()).isEqualTo(Granularity.DAY);
        assertThat(daily.buckets()).hasSize(30);

        ReportPeriod monthly = ReportPeriod.resolve(null, LocalDate.of(2026, 4, 15), LocalDate.of(2026, 7, 10), TODAY, Preset.THIS_FY);
        assertThat(monthly.granularity()).isEqualTo(Granularity.MONTH);
        // The first bucket starts at the period start, not before it.
        assertThat(monthly.buckets()).containsExactly(LocalDate.of(2026, 4, 15), LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        assertThat(monthly.bucketOf(LocalDate.of(2026, 4, 20))).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(monthly.bucketOf(LocalDate.of(2026, 6, 30))).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void customRangesAreValidatedAndBounded() {
        assertThatThrownBy(() -> ReportPeriod.resolve(Preset.CUSTOM, LocalDate.of(2026, 1, 1), null, TODAY, Preset.THIS_FY))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ReportPeriod.resolve(null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31), TODAY,
                Preset.THIS_FY)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ReportPeriod.resolve(null, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1), TODAY,
                Preset.THIS_FY)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ReportPeriod.resolve(null, LocalDate.of(1999, 12, 31), LocalDate.of(2000, 1, 31), TODAY,
                Preset.THIS_FY)).isInstanceOf(ValidationException.class);
        // Exactly the maximum is accepted.
        ReportPeriod max = ReportPeriod.resolve(null, LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 1, 1).plusDays(ReportPeriod.MAX_DAYS - 1), TODAY, Preset.THIS_FY);
        assertThat(max.days()).isEqualTo(ReportPeriod.MAX_DAYS);
    }

    @Test
    void withoutPresetOrDatesTheFallbackApplies() {
        assertThat(ReportPeriod.resolve(null, null, null, TODAY, Preset.THIS_MONTH).preset()).isEqualTo(Preset.THIS_MONTH);
    }
}
