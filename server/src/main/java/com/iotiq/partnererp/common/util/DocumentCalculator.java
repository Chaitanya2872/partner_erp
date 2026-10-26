package com.iotiq.partnererp.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The source {@code totals(lines, discountPct, rateKey)} (BUSINESS_RULES §6.1), shared by estimates, invoices and
 * their B2C twins.
 *
 * <pre>
 * sub     = Σ qty × rate
 * tax     = Σ qty × rate × (1 − disc/100) × gst/100      (per-line GST)
 * discA   = sub × disc/100
 * taxable = sub − discA
 * total   = taxable + tax
 * </pre>
 *
 * Every value is computed exactly and each output is rounded once, half up to 2 decimals (D-25), so {@code total}
 * comes from the unrounded taxable and tax, exactly as in the source. The CGST/SGST halves are not stored: CGST is
 * half the rounded tax, rounded, and SGST the remainder, so the printed halves always add up (D-21).
 */
public final class DocumentCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private DocumentCalculator() {
    }

    /** One line's inputs: quantity, unit rate (partner or B2C) and GST %. */
    public record Line(BigDecimal quantity, BigDecimal rate, BigDecimal gstPercent) {
    }

    public record Totals(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxableAmount,
                         BigDecimal gstAmount, BigDecimal grandTotal) {

        public static final Totals ZERO = new Totals(money(BigDecimal.ZERO), money(BigDecimal.ZERO),
                money(BigDecimal.ZERO), money(BigDecimal.ZERO), money(BigDecimal.ZERO));

        /** Half the GST for CGST, rounded half up. */
        public BigDecimal cgstAmount() {
            return money(gstAmount.divide(TWO, 3, RoundingMode.UNNECESSARY));
        }

        /** The other half, so CGST + SGST equals the GST exactly. */
        public BigDecimal sgstAmount() {
            return gstAmount.subtract(cgstAmount());
        }
    }

    public static Totals totals(List<Line> lines, BigDecimal discountPercent) {
        BigDecimal sub = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal keep = HUNDRED.subtract(discountPercent).movePointLeft(2);
        for (Line line : lines) {
            BigDecimal amount = line.quantity().multiply(line.rate());
            sub = sub.add(amount);
            tax = tax.add(amount.multiply(keep).multiply(line.gstPercent().movePointLeft(2)));
        }
        BigDecimal discount = sub.multiply(discountPercent.movePointLeft(2));
        BigDecimal taxable = sub.subtract(discount);
        return new Totals(money(sub), money(discount), money(taxable), money(tax), money(taxable.add(tax)));
    }

    /** The line "Amount" column: quantity × rate before discount, rounded. */
    public static BigDecimal lineAmount(BigDecimal quantity, BigDecimal rate) {
        return money(quantity.multiply(rate));
    }

    /** {@code r2}: half up to 2 decimals. */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
