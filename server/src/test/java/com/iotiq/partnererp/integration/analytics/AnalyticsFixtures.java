package com.iotiq.partnererp.integration.analytics;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ledger rows inserted directly for the Phase 12 analytics tests, dated in past years that no other test uses, so
 * period figures are exact while the shared test database holds other tests' data. Inserts go through the
 * database's own guards (issued invoices only take payments, reversals must mirror their receipt).
 */
final class AnalyticsFixtures {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate jdbc;

    AnalyticsFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static String number(String prefix) {
        return prefix + ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L);
    }

    static Timestamp at(LocalDateTime local) {
        return Timestamp.from(local.atZone(ZONE).toInstant());
    }

    static Timestamp at(LocalDate date) {
        return at(date.atTime(12, 0));
    }

    void partnerCreatedAt(UUID partner, LocalDateTime createdAt) {
        jdbc.update("update partners set created_at = ? where id = ?", at(createdAt), partner);
    }

    /** An issued invoice with its tax split as GST = total − taxable. */
    UUID issued(UUID partner, LocalDate date, LocalDate due, String taxable, String total, String feePercent) {
        return issued(partner, date, due, taxable, total, feePercent, "Analytics client");
    }

    UUID issued(UUID partner, LocalDate date, LocalDate due, String taxable, String total, String feePercent,
                String billingName) {
        UUID id = UUID.randomUUID();
        BigDecimal t = new BigDecimal(taxable);
        BigDecimal g = new BigDecimal(total);
        jdbc.update("insert into invoices (id, invoice_number, partner_id, invoice_date, due_date, status, issued_at, "
                        + "tax_type, taxable_amount, gst_amount, grand_total, fee_percent, billing_name) "
                        + "values (?, ?, ?, ?, ?, 'ISSUED', ?, 'INTRA', ?, ?, ?, ?, ?)",
                id, number("TAN-"), partner, date, due, at(date), t, g.subtract(t), g, new BigDecimal(feePercent),
                billingName);
        return id;
    }

    UUID cancelled(UUID partner, LocalDate date, String taxable, String total) {
        UUID id = UUID.randomUUID();
        BigDecimal t = new BigDecimal(taxable);
        BigDecimal g = new BigDecimal(total);
        jdbc.update("insert into invoices (id, invoice_number, partner_id, invoice_date, due_date, status, issued_at, "
                        + "tax_type, taxable_amount, gst_amount, grand_total, fee_percent, cancelled_at, cancel_reason) "
                        + "values (?, ?, ?, ?, ?, 'CANCELLED', ?, 'INTRA', ?, ?, ?, 5, ?, 'Raised in error (test)')",
                id, number("TAN-"), partner, date, date.plusDays(15), at(date), t, g.subtract(t), g, at(date.plusDays(1)));
        return id;
    }

    /** A draft with one product line, then issued: lines can only be written while the invoice is a draft. */
    UUID issuedWithLine(UUID partner, LocalDate date, UUID product, String quantity, String taxable, String gstPercent,
                        String b2cUnitPrice, String b2cName) {
        UUID id = UUID.randomUUID();
        BigDecimal t = new BigDecimal(taxable);
        BigDecimal tax = t.multiply(new BigDecimal(gstPercent)).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        jdbc.update("insert into invoices (id, partner_id, invoice_date, due_date, status, taxable_amount, gst_amount, "
                + "grand_total, fee_percent) values (?, ?, ?, ?, 'DRAFT', ?, ?, ?, 0)", id, partner, date,
                date.plusDays(15), t, tax, t.add(tax));
        BigDecimal qty = new BigDecimal(quantity);
        jdbc.update("insert into invoice_items (id, invoice_id, line_no, product_id, description, b2c_name, hsn, unit, "
                        + "quantity, unit_price, b2c_unit_price, gst_percent, line_total, taxable_value, cgst_amount, "
                        + "sgst_amount, igst_amount, tax_amount, total_amount) values (?, ?, 1, ?, 'Analytics line', ?, "
                        + "'8536', 'nos', ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                UUID.randomUUID(), id, product, b2cName, qty, t.divide(qty, 2, java.math.RoundingMode.HALF_UP),
                new BigDecimal(b2cUnitPrice), new BigDecimal(gstPercent), t, t,
                tax.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP),
                tax.subtract(tax.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP)), tax, t.add(tax));
        jdbc.update("update invoices set status = 'ISSUED', invoice_number = ?, issued_at = ?, tax_type = 'INTRA' "
                + "where id = ?", number("TAN-"), at(date), id);
        return id;
    }

    UUID receipt(UUID invoice, LocalDate date, String amount, String net, String method) {
        UUID id = UUID.randomUUID();
        UUID partner = jdbc.queryForObject("select partner_id from invoices where id = ?", UUID.class, invoice);
        jdbc.update("insert into payments (id, invoice_id, partner_id, payment_date, amount, net_amount, payment_method, "
                        + "payment_number, entry_type) values (?, ?, ?, ?, ?, ?, ?, ?, 'RECEIPT')",
                id, invoice, partner, date, new BigDecimal(amount), new BigDecimal(net), method, number("TPA-"));
        return id;
    }

    UUID reversal(UUID receipt, LocalDate date) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into payments (id, invoice_id, partner_id, payment_date, amount, net_amount, payment_method, "
                        + "payment_number, entry_type, reversed_payment_id, reversal_reason) "
                        + "select ?, invoice_id, partner_id, ?, -amount, -net_amount, payment_method, ?, 'REVERSAL', id, "
                        + "'Bounced (test)' from payments where id = ?",
                id, date, number("TPR-"), receipt);
        return id;
    }

    UUID lead(UUID partner, LocalDateTime createdAt, String stage, String budget) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into leads (id, lead_number, partner_id, client_name, client_phone, project_type, stage, "
                        + "budget, created_at) values (?, ?, ?, 'Analytics client', '9000000000', 'VILLA', ?, ?, ?)",
                id, number("TLD-"), partner, stage, budget == null ? null : new BigDecimal(budget), at(createdAt));
        history(id, "CREATED", null, "NEW", createdAt);
        return id;
    }

    void history(UUID lead, String event, String from, String to, LocalDateTime at) {
        jdbc.update("insert into lead_stage_history (lead_id, event, from_stage, to_stage, occurred_at) values (?, ?, ?, ?, ?)",
                lead, event, from, to, at(at));
    }

    UUID estimate(UUID partner, UUID lead, LocalDate date, String status, String total) {
        UUID id = UUID.randomUUID();
        BigDecimal g = new BigDecimal(total);
        jdbc.update("insert into estimates (id, estimate_number, partner_id, lead_id, estimate_date, valid_until, status, "
                        + "taxable_amount, gst_amount, grand_total) values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?)",
                id, number("TES-"), partner, lead, date, date.plusDays(30), status, g, g);
        return id;
    }

    /** An issued invoice raised from an estimate. */
    void invoiceFrom(UUID estimate, UUID partner, LocalDate date) {
        jdbc.update("insert into invoices (id, invoice_number, partner_id, estimate_id, invoice_date, due_date, status, "
                        + "issued_at, tax_type, taxable_amount, gst_amount, grand_total, fee_percent) "
                        + "values (?, ?, ?, ?, ?, ?, 'ISSUED', ?, 'INTRA', 100, 18, 118, 0)",
                UUID.randomUUID(), number("TAN-"), partner, estimate, date, date.plusDays(15), at(date));
    }

    static Instant instant(LocalDateTime local) {
        return local.atZone(ZONE).toInstant();
    }
}
