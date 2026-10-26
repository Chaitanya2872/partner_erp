package com.iotiq.partnererp.unit.invoices;

import com.iotiq.partnererp.modules.invoices.tax.GstCalculator;
import com.iotiq.partnererp.modules.invoices.tax.GstCalculator.Line;
import com.iotiq.partnererp.modules.invoices.tax.GstCalculator.LineTax;
import com.iotiq.partnererp.modules.invoices.tax.GstCalculator.Result;
import com.iotiq.partnererp.modules.invoices.tax.TaxType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden vectors for invoice tax (BUSINESS_RULES §6.7), worked by hand: per-line taxable values and tax components,
 * each rounded half up to paise, and totals that are the exact sums of the lines.
 */
class GstCalculatorTest {

    @Test
    void intraStateSplitsEachLineIntoEqualCgstAndSgst() {
        // line 1: 2 × 1000 less 7.5% = 1850.00; CGST = SGST = 1850 × 9% = 166.50
        // line 2: 3 × 250.50 = 751.50 less 7.5% = 695.1375 → 695.14; CGST = SGST = 695.14 × 2.5% = 17.3785 → 17.38
        Result r = GstCalculator.calculate(List.of(line("2", "1000", "18"), line("3", "250.50", "5")), bd("7.5"),
                TaxType.INTRA);
        assertLine(r.lines().get(0), "2000.00", "1850.00", "166.50", "166.50", "0.00", "333.00", "2183.00");
        assertLine(r.lines().get(1), "751.50", "695.14", "17.38", "17.38", "0.00", "34.76", "729.90");
        assertThat(r.totals().subtotal()).isEqualByComparingTo("2751.50");
        assertThat(r.totals().discountAmount()).isEqualByComparingTo("206.36");
        assertThat(r.totals().taxableAmount()).isEqualByComparingTo("2545.14");
        assertThat(r.totals().cgst()).isEqualByComparingTo("183.88");
        assertThat(r.totals().sgst()).isEqualByComparingTo("183.88");
        assertThat(r.totals().igst()).isEqualByComparingTo("0.00");
        assertThat(r.totals().gstAmount()).isEqualByComparingTo("367.76");
        // The estimate (§6.1, rounded once) gives 2912.89 for the same lines: per-line rounding adds a paisa.
        assertThat(r.totals().grandTotal()).isEqualByComparingTo("2912.90");
    }

    @Test
    void interStateChargesTheWholeRateAsIgst() {
        Result r = GstCalculator.calculate(List.of(line("2", "1000", "18"), line("3", "250.50", "5")), bd("7.5"),
                TaxType.INTER);
        assertLine(r.lines().get(0), "2000.00", "1850.00", "0.00", "0.00", "333.00", "333.00", "2183.00");
        // 695.14 × 5% = 34.757 → 34.76
        assertLine(r.lines().get(1), "751.50", "695.14", "0.00", "0.00", "34.76", "34.76", "729.90");
        assertThat(r.totals().igst()).isEqualByComparingTo("367.76");
        assertThat(r.totals().cgst().add(r.totals().sgst())).isEqualByComparingTo("0");
        assertThat(r.totals().grandTotal()).isEqualByComparingTo("2912.90");
    }

    @Test
    void roundingEachHalfSeparatelyCanDifferFromRoundingTheWholeRateByAPaisa() {
        // 11 × 2.5% = 0.275 → 0.28 twice = 0.56 intra; 11 × 5% = 0.55 inter.
        assertLine(GstCalculator.line(line("1", "11", "5"), bd("0"), TaxType.INTRA),
                "11.00", "11.00", "0.28", "0.28", "0.00", "0.56", "11.56");
        assertLine(GstCalculator.line(line("1", "11", "5"), bd("0"), TaxType.INTER),
                "11.00", "11.00", "0.00", "0.00", "0.55", "0.55", "11.55");
        // Half up, never banker's rounding: 10.30 × 2.5% = 0.2575 → 0.26.
        assertThat(GstCalculator.line(line("1", "10.30", "5"), bd("0"), TaxType.INTRA).cgst()).isEqualByComparingTo("0.26");
        // Fractional quantities: 1.5 × 99.99 = 149.985 → 149.99 (amount); taxable 149.985 less 0% → 149.99.
        assertThat(GstCalculator.line(line("1.5", "99.99", "18"), bd("0"), TaxType.INTER).taxableValue())
                .isEqualByComparingTo("149.99");
    }

    @Test
    void totalsAreExactSumsOfTheStoredLines() {
        Result r = GstCalculator.calculate(List.of(line("1.2", "10.02", "18"), line("7", "13.13", "12"),
                line("1", "999.99", "28")), bd("3.333"), TaxType.INTRA);
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (LineTax l : r.lines()) {
            assertThat(l.tax()).isEqualByComparingTo(l.cgst().add(l.sgst()).add(l.igst()));
            assertThat(l.total()).isEqualByComparingTo(l.taxableValue().add(l.tax()));
            assertThat(l.cgst()).isEqualByComparingTo(l.sgst());
            taxable = taxable.add(l.taxableValue());
            tax = tax.add(l.tax());
            total = total.add(l.total());
        }
        assertThat(r.totals().taxableAmount()).isEqualByComparingTo(taxable);
        assertThat(r.totals().gstAmount()).isEqualByComparingTo(tax);
        assertThat(r.totals().grandTotal()).isEqualByComparingTo(total);
        assertThat(r.totals().subtotal().subtract(r.totals().discountAmount())).isEqualByComparingTo(taxable);
    }

    @Test
    void withoutATaxTypeADraftCarriesNoTaxAndAFullDiscountLeavesNothingToTax() {
        assertLine(GstCalculator.line(line("2", "100", "18"), bd("0"), null),
                "200.00", "200.00", "0.00", "0.00", "0.00", "0.00", "200.00");
        assertLine(GstCalculator.line(line("2", "100", "18"), bd("100"), TaxType.INTRA),
                "200.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00");
    }

    @Test
    void theResultIsDeterministic() {
        List<Line> lines = List.of(line("3", "333.33", "18"), line("0.001", "0.01", "28"));
        assertThat(GstCalculator.calculate(lines, bd("12.5"), TaxType.INTRA))
                .isEqualTo(GstCalculator.calculate(lines, bd("12.5"), TaxType.INTRA));
    }

    private static void assertLine(LineTax t, String amount, String taxable, String cgst, String sgst, String igst,
                                   String tax, String total) {
        assertThat(t.amount()).isEqualByComparingTo(amount);
        assertThat(t.taxableValue()).isEqualByComparingTo(taxable);
        assertThat(t.cgst()).isEqualByComparingTo(cgst);
        assertThat(t.sgst()).isEqualByComparingTo(sgst);
        assertThat(t.igst()).isEqualByComparingTo(igst);
        assertThat(t.tax()).isEqualByComparingTo(tax);
        assertThat(t.total()).isEqualByComparingTo(total);
        assertThat(t.total().scale()).isEqualTo(2);
    }

    private static Line line(String qty, String rate, String gst) {
        return new Line(bd(qty), bd(rate), bd(gst));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
