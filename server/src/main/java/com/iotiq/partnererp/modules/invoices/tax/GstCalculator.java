package com.iotiq.partnererp.modules.invoices.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Invoice tax (Phase 9 decision 1, BUSINESS_RULES §6.7). Every amount is a BigDecimal rounded half up to paise, line
 * by line, so the stored line snapshots add up exactly to the invoice totals:
 *
 * <pre>
 * amount   = r2(qty × rate)                          (the "Amount" column, before discount)
 * taxable  = r2(qty × rate × (100 − discount%) / 100)
 * IGST     = r2(taxable × gst% / 100)                when inter-state
 * CGST     = r2(taxable × gst% / 200)                when intra-state
 * SGST/UTGST = CGST                                  (equal halves of the rate)
 * tax      = CGST + SGST + IGST
 * total    = taxable + tax
 * invoice totals = Σ lines; discount = Σ amount − Σ taxable
 * </pre>
 *
 * Without a tax type (no place of supply yet) a draft carries no tax; it can't be issued until the type is known.
 *
 * <p>Estimates round once over the whole document (§6.1), so an invoice converted from an estimate can differ from
 * it by a few paise; the difference is shown before issue.
 */
public final class GstCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO_HUNDRED = BigDecimal.valueOf(200);

    private GstCalculator() {
    }

    public record Line(BigDecimal quantity, BigDecimal rate, BigDecimal gstPercent) {
    }

    public record LineTax(BigDecimal amount, BigDecimal taxableValue, BigDecimal cgst, BigDecimal sgst,
                          BigDecimal igst, BigDecimal tax, BigDecimal total) {
    }

    public record Totals(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxableAmount, BigDecimal cgst,
                         BigDecimal sgst, BigDecimal igst, BigDecimal gstAmount, BigDecimal grandTotal) {
    }

    public record Result(List<LineTax> lines, Totals totals) {
    }

    public static LineTax line(Line line, BigDecimal discountPercent, TaxType type) {
        BigDecimal gross = line.quantity().multiply(line.rate());
        BigDecimal taxable = money(gross.multiply(HUNDRED.subtract(discountPercent)).divide(HUNDRED));
        BigDecimal cgst = BigDecimal.ZERO.setScale(2);
        BigDecimal igst = BigDecimal.ZERO.setScale(2);
        if (type == TaxType.INTER) {
            igst = money(taxable.multiply(line.gstPercent()).divide(HUNDRED));
        } else if (type == TaxType.INTRA) {
            cgst = money(taxable.multiply(line.gstPercent()).divide(TWO_HUNDRED));
        }
        BigDecimal sgst = cgst;
        BigDecimal tax = cgst.add(sgst).add(igst);
        return new LineTax(money(gross), taxable, cgst, sgst, igst, tax, taxable.add(tax));
    }

    public static Result calculate(List<Line> lines, BigDecimal discountPercent, TaxType type) {
        List<LineTax> out = new ArrayList<>();
        BigDecimal sub = zero();
        BigDecimal taxable = zero();
        BigDecimal cgst = zero();
        BigDecimal sgst = zero();
        BigDecimal igst = zero();
        for (Line l : lines) {
            LineTax t = line(l, discountPercent, type);
            out.add(t);
            sub = sub.add(t.amount());
            taxable = taxable.add(t.taxableValue());
            cgst = cgst.add(t.cgst());
            sgst = sgst.add(t.sgst());
            igst = igst.add(t.igst());
        }
        BigDecimal gst = cgst.add(sgst).add(igst);
        return new Result(out, new Totals(sub, sub.subtract(taxable), taxable, cgst, sgst, igst, gst, taxable.add(gst)));
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
