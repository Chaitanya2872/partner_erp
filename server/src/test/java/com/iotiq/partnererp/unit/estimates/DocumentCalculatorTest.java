package com.iotiq.partnererp.unit.estimates;

import com.iotiq.partnererp.common.util.DocumentCalculator;
import com.iotiq.partnererp.common.util.DocumentCalculator.Line;
import com.iotiq.partnererp.common.util.DocumentCalculator.Totals;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden vectors for the source {@code totals()} (BUSINESS_RULES §6.1), worked by hand with the source formula:
 * full precision throughout, each output rounded once, half up.
 */
class DocumentCalculatorTest {

    @Test
    void theTotalComesFromTheUnroundedTaxableAndTaxNotFromTheRoundedParts() {
        // 1.2 × 10.02 = 12.024; tax 2.16432; total 14.18832 → 14.19 (12.02 + 2.16 would give 14.18).
        Totals t = DocumentCalculator.totals(List.of(line("1.2", "10.02", "18")), bd("0"));
        assertTotals(t, "12.02", "0.00", "12.02", "2.16", "14.19");
    }

    @Test
    void theDocumentDiscountAppliesToEveryLineAndGstIsPerLine() {
        // sub 3751.49; discount 7.5% = 281.36175; taxable 3470.12825
        // tax = 2000×0.925×0.18 + 751.5×0.925×0.05 + 999.99×0.925×0.28 = 333 + 34.756875 + 258.99741 = 626.754285
        // total = 4096.882535
        Totals t = DocumentCalculator.totals(List.of(line("2", "1000", "18"), line("3", "250.50", "5"),
                line("1", "999.99", "28")), bd("7.5"));
        assertTotals(t, "3751.49", "281.36", "3470.13", "626.75", "4096.88");
        assertThat(t.cgstAmount()).isEqualByComparingTo("313.38");
        assertThat(t.sgstAmount()).isEqualByComparingTo("313.37");
        assertThat(t.cgstAmount().add(t.sgstAmount())).isEqualByComparingTo(t.gstAmount());
    }

    @Test
    void halvesRoundUpAndAFullDiscountLeavesNothingToTax() {
        assertTotals(DocumentCalculator.totals(List.of(line("0.5", "0.01", "0")), bd("0")),
                "0.01", "0.00", "0.01", "0.00", "0.01");
        assertTotals(DocumentCalculator.totals(List.of(line("1", "500", "18")), bd("100")),
                "500.00", "500.00", "0.00", "0.00", "0.00");
    }

    @Test
    void zeroRatesGiveZeroTotalsAndTheLineAmountIsBeforeDiscount() {
        // A B2C copy whose products have no B2C price.
        assertTotals(DocumentCalculator.totals(List.of(line("4", "0", "18"), line("1", "0", "5")), bd("10")),
                "0.00", "0.00", "0.00", "0.00", "0.00");
        assertThat(DocumentCalculator.lineAmount(bd("3"), bd("33.335"))).isEqualByComparingTo("100.01");
        assertThat(DocumentCalculator.totals(List.of(), bd("0")).grandTotal()).isEqualByComparingTo("0");
    }

    @Test
    void cgstTakesTheRoundedHalfAndSgstTheRest() {
        Totals t = new Totals(bd("0"), bd("0"), bd("0"), bd("0.01"), bd("0.01"));
        assertThat(t.cgstAmount()).isEqualByComparingTo("0.01");
        assertThat(t.sgstAmount()).isEqualByComparingTo("0.00");
    }

    private static void assertTotals(Totals t, String sub, String discount, String taxable, String tax, String total) {
        assertThat(t.subtotal()).isEqualByComparingTo(sub);
        assertThat(t.discountAmount()).isEqualByComparingTo(discount);
        assertThat(t.taxableAmount()).isEqualByComparingTo(taxable);
        assertThat(t.gstAmount()).isEqualByComparingTo(tax);
        assertThat(t.grandTotal()).isEqualByComparingTo(total);
        assertThat(t.grandTotal()).hasScaleOf(2);
    }

    private static Line line(String qty, String rate, String gst) {
        return new Line(bd(qty), bd(rate), bd(gst));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
