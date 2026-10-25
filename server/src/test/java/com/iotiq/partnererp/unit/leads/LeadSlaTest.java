package com.iotiq.partnererp.unit.leads;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.modules.leads.service.LeadSla;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden vectors for the source's {@code leadSla} (BUSINESS_RULES §4.1) and protection status (BR-L7).
 */
class LeadSlaTest {

    private static final ZoneId ZONE = AppConstants.BUSINESS_ZONE;
    private static final LeadSla.Targets TARGETS = new LeadSla.Targets(48, 5);
    /** 2026-10-01 10:00 IST. */
    private static final Instant CREATED = Instant.parse("2026-10-01T04:30:00Z");

    private static LeadSla.Result open(Duration sinceCreation) {
        return LeadSla.evaluate(CREATED, null, LeadStage.NEW, null, CREATED.plus(sinceCreation), ZONE, TARGETS);
    }

    @Test
    void ackBreachStartsAtFortyEightAndAHalfHoursBecauseOfRounding() {
        LeadSla.Result justBefore = open(Duration.ofHours(48).plusMinutes(29).plusSeconds(59));
        assertThat(justBefore.ackHours()).isEqualTo(48);
        assertThat(justBefore.ackBreach()).isFalse();

        LeadSla.Result atBoundary = open(Duration.ofHours(48).plusMinutes(30));
        assertThat(atBoundary.ackHours()).isEqualTo(49);
        assertThat(atBoundary.ackBreach()).isTrue();
    }

    @Test
    void acknowledgedLeadsMeasureCreationToAcknowledgementNotNow() {
        Instant now = CREATED.plus(Duration.ofDays(30));
        LeadSla.Result inTime = LeadSla.evaluate(CREATED, CREATED.plus(Duration.ofHours(48).plusMinutes(29)),
                LeadStage.REGISTERED, null, now, ZONE, new LeadSla.Targets(48, 100));
        assertThat(inTime.ackHours()).isEqualTo(48);
        assertThat(inTime.ackBreach()).isFalse();

        LeadSla.Result late = LeadSla.evaluate(CREATED, CREATED.plus(Duration.ofHours(48).plusMinutes(30)),
                LeadStage.WON, null, now, ZONE, TARGETS);
        assertThat(late.ackHours()).isEqualTo(49);
        assertThat(late.ackBreach()).isTrue();
    }

    @Test
    void unacknowledgedWonOrLostLeadsHaveNoClocks() {
        for (LeadStage closed : new LeadStage[]{LeadStage.WON, LeadStage.LOST}) {
            LeadSla.Result r = LeadSla.evaluate(CREATED, null, closed, null, CREATED.plus(Duration.ofDays(10)), ZONE, TARGETS);
            assertThat(r.ackHours()).isNull();
            assertThat(r.ackBreach()).isFalse();
            assertThat(r.estimateDays()).isNull();
            assertThat(r.estimateBreach()).isFalse();
            assertThat(r.breached()).isFalse();
        }
    }

    @Test
    void estimateClockRunsFromTheAcknowledgementDateWhileOpenWithoutEstimates() {
        Instant ack = CREATED.plus(Duration.ofHours(2));                    // 2026-10-01 IST
        Instant fiveDaysLater = Instant.parse("2026-10-06T12:00:00Z");      // 2026-10-06 IST
        LeadSla.Result five = LeadSla.evaluate(CREATED, ack, LeadStage.PRESALES, null, fiveDaysLater, ZONE, TARGETS);
        assertThat(five.estimateDays()).isEqualTo(5);
        assertThat(five.estimateBreach()).isFalse();
        assertThat(five.hasEstimate()).isFalse();

        LeadSla.Result six = LeadSla.evaluate(CREATED, ack, LeadStage.PRESALES, null, fiveDaysLater.plus(Duration.ofDays(1)), ZONE, TARGETS);
        assertThat(six.estimateDays()).isEqualTo(6);
        assertThat(six.estimateBreach()).isTrue();
    }

    @Test
    void closedLeadsWithoutEstimatesStopTheEstimateClock() {
        Instant ack = CREATED.plus(Duration.ofHours(2));
        LeadSla.Result lost = LeadSla.evaluate(CREATED, ack, LeadStage.LOST, null, CREATED.plus(Duration.ofDays(40)), ZONE, TARGETS);
        assertThat(lost.estimateDays()).isNull();
        assertThat(lost.estimateBreach()).isFalse();
    }

    @Test
    void theEarliestEstimateDateIsMeasuredEvenOnClosedLeads() {
        Instant ack = CREATED.plus(Duration.ofHours(2));
        Instant now = CREATED.plus(Duration.ofDays(60));
        LeadSla.Result late = LeadSla.evaluate(CREATED, ack, LeadStage.WON, LocalDate.of(2026, 10, 8), now, ZONE, TARGETS);
        assertThat(late.estimateDays()).isEqualTo(7);
        assertThat(late.estimateBreach()).isTrue();
        assertThat(late.hasEstimate()).isTrue();

        LeadSla.Result onTarget = LeadSla.evaluate(CREATED, ack, LeadStage.NEGOTIATION, LocalDate.of(2026, 10, 6), now, ZONE, TARGETS);
        assertThat(onTarget.estimateDays()).isEqualTo(5);
        assertThat(onTarget.estimateBreach()).isFalse();

        // An estimate dated before the acknowledgement gives negative days, as in the source.
        LeadSla.Result early = LeadSla.evaluate(CREATED, ack, LeadStage.ESTIMATE_SENT, LocalDate.of(2026, 9, 29), now, ZONE, TARGETS);
        assertThat(early.estimateDays()).isEqualTo(-2);
        assertThat(early.estimateBreach()).isFalse();
    }

    @Test
    void theAcknowledgementDateIsTheBusinessZoneDate() {
        // 2026-10-01 20:00 UTC is 2026-10-02 01:30 in India.
        Instant ack = Instant.parse("2026-10-01T20:00:00Z");
        LeadSla.Result r = LeadSla.evaluate(CREATED, ack, LeadStage.REGISTERED, LocalDate.of(2026, 10, 7), ack, ZONE, TARGETS);
        assertThat(r.estimateDays()).isEqualTo(5);
        assertThat(r.estimateBreach()).isFalse();
    }

    @Test
    void protectionCountsUpToAndIncludingItsLastDay() {
        LocalDate today = LocalDate.of(2026, 10, 25);
        assertThat(LeadSla.protectionActive(today, today)).isTrue();
        assertThat(LeadSla.protectionDaysLeft(today, today)).isZero();
        assertThat(LeadSla.protectionActive(today.minusDays(1), today)).isFalse();
        assertThat(LeadSla.protectionDaysLeft(today.minusDays(1), today)).isNull();
        assertThat(LeadSla.protectionDaysLeft(today.plusDays(15), today)).isEqualTo(15);
        assertThat(LeadSla.protectionActive(null, today)).isFalse();
    }
}
