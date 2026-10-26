package com.iotiq.partnererp.unit.estimates;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.estimates.entity.EstimateStatus;
import com.iotiq.partnererp.modules.estimates.service.EstimateRules;
import com.iotiq.partnererp.modules.leads.entity.Lead;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import com.iotiq.partnererp.modules.sequences.service.DocumentNumberService;
import com.iotiq.partnererp.modules.settings.entity.PartnerTier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EstimateRulesTest {

    @Test
    void quantitiesMustBePositiveWithAtMostThreeDecimals() {
        List<FieldError> errors = new ArrayList<>();
        assertThat(EstimateRules.quantity(new BigDecimal("2.5"), "q", errors)).isEqualByComparingTo("2.5").hasScaleOf(3);
        assertThat(EstimateRules.quantity(new BigDecimal("1.0000"), "q", errors)).hasScaleOf(3);
        assertThat(errors).isEmpty();
        assertThat(EstimateRules.quantity(BigDecimal.ZERO, "q", errors)).isNull();
        assertThat(EstimateRules.quantity(new BigDecimal("-1"), "q", errors)).isNull();
        assertThat(EstimateRules.quantity(new BigDecimal("1.0001"), "q", errors)).isNull();
        assertThat(EstimateRules.quantity(null, "q", errors)).isNull();
        assertThat(errors).hasSize(4).allMatch(e -> e.field().equals("q"));
    }

    @Test
    void ratesAndPercentagesAreBounded() {
        List<FieldError> errors = new ArrayList<>();
        assertThat(EstimateRules.rate(new BigDecimal("0"), "r", errors)).hasScaleOf(2);
        assertThat(EstimateRules.rate(new BigDecimal("10.5"), "r", errors)).isEqualByComparingTo("10.50");
        assertThat(EstimateRules.percent(new BigDecimal("100"), "p", "discount", errors)).hasScaleOf(3);
        assertThat(EstimateRules.percent(new BigDecimal("12.375"), "p", "GST", errors)).isEqualByComparingTo("12.375");
        assertThat(errors).isEmpty();
        assertThat(EstimateRules.rate(new BigDecimal("-0.01"), "r", errors)).isNull();
        assertThat(EstimateRules.rate(new BigDecimal("1.005"), "r", errors)).isNull();
        assertThat(EstimateRules.percent(new BigDecimal("100.001"), "p", "discount", errors)).isNull();
        assertThat(EstimateRules.percent(new BigDecimal("-1"), "p", "discount", errors)).isNull();
        assertThat(errors).extracting(FieldError::message)
                .contains("The rate can't be negative.", "Discount must be between 0 and 100, with at most 3 decimals.");
    }

    @Test
    void theDefaultFeeIsTheLockedRateThenTheTierRateAndZeroWhenThePartnerBuys() {
        PartnerTier tier = new PartnerTier();
        tier.setCommissionPercent(new BigDecimal("10"));
        Partner partner = new Partner();
        partner.setTier(tier);
        Lead lead = new Lead();
        lead.setPartner(partner);

        assertThat(EstimateRules.defaultFee(BillTo.CLIENT, lead)).isEqualByComparingTo("10").hasScaleOf(3);
        lead.setLockedFeePercent(new BigDecimal("8"));
        assertThat(EstimateRules.defaultFee(BillTo.CLIENT, lead)).isEqualByComparingTo("8");
        assertThat(EstimateRules.defaultFee(BillTo.PARTNER, lead)).isEqualByComparingTo("0");
    }

    @Test
    void onlySentEstimatesPastTheirValidityAreExpired() {
        LocalDate today = LocalDate.of(2026, 10, 26);
        assertThat(EstimateRules.expired(EstimateStatus.SENT, today.minusDays(1), today)).isTrue();
        assertThat(EstimateRules.expired(EstimateStatus.SENT, today, today)).isFalse();
        assertThat(EstimateRules.expired(EstimateStatus.DRAFT, today.minusDays(1), today)).isFalse();
        assertThat(EstimateRules.expired(EstimateStatus.ACCEPTED, today.minusDays(1), today)).isFalse();
        assertThat(EstimateRules.expired(EstimateStatus.SENT, null, today)).isFalse();
    }

    @Test
    void numbersUseTheYearAndASixDigitSequence() {
        assertThat(DocumentNumberService.format("EST", 2026, 1)).isEqualTo("EST-2026-000001");
        assertThat(DocumentNumberService.format("LEAD", 2027, 999999)).isEqualTo("LEAD-2027-999999");
    }
}
