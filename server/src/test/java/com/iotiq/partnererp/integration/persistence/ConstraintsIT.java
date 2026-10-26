package com.iotiq.partnererp.integration.persistence;

import com.iotiq.partnererp.integration.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Database-level integrity rules. Each case runs in a transaction that is always rolled back, so
 * fixtures never leak between tests.
 */
class ConstraintsIT extends AbstractIntegrationTest {

    @Autowired
    private TransactionTemplate tx;

    @Test
    void skuIsUniqueIgnoringCase() {
        rolledBack(f -> assertViolation(() -> jdbc.update(
                "insert into products (sku, category, b2c_name, partner_name, partner_price) values ('sti-2m', 'SERVICES', 'x', 'x', 1)"), "ux_products_sku"));
    }

    @Test
    void productCategoryIsRestricted() {
        rolledBack(f -> assertViolation(() -> jdbc.update(
                "insert into products (sku, category, b2c_name, partner_name, partner_price) values ('X-1', 'GADGETS', 'x', 'x', 1)"), "ck_products_category"));
    }

    @Test
    void onlyFourTierLevels() {
        rolledBack(f -> assertViolation(() -> jdbc.update(
                "insert into partner_tiers (tier_level, name, commission_percent, minimum_revenue, payout_business_days) "
                        + "values (5, 'Platinum', 20, 0, 5)"), "ck_partner_tiers_level"));
    }

    @Test
    void companySettingsIsASingleton() {
        rolledBack(f -> assertViolation(() -> jdbc.update(
                "insert into company_settings (company_name, lead_protection_days, clawback_days, tds_percent, tds_threshold, "
                        + "acknowledgement_hours, estimate_sla_days, estimate_validity_days, invoice_due_days, default_gst_percent) "
                        + "select 'Second', lead_protection_days, clawback_days, tds_percent, tds_threshold, acknowledgement_hours, "
                        + "estimate_sla_days, estimate_validity_days, invoice_due_days, default_gst_percent from company_settings"),
                "ux_company_settings_singleton"));
    }

    @Test
    void phoneDigitsAreGeneratedFromLastTenDigits() {
        rolledBack(f -> {
            UUID lead = f.lead("+91 98765-43210");
            assertThat(jdbc.queryForObject("select client_phone_digits from leads where id = ?", String.class, lead))
                    .isEqualTo("9876543210");
        });
    }

    @Test
    void acknowledgementRequiresLockedFeeAndProtection() {
        rolledBack(f -> {
            UUID lead = f.lead("9000000001");
            assertViolation(() -> jdbc.update("update leads set acknowledged_at = now() where id = ?", lead), "ck_leads_ack_complete");
        });
    }

    @Test
    void documentDiscountIsAPercentage() {
        rolledBack(f -> assertViolation(() -> jdbc.update(
                "insert into estimates (estimate_number, partner_id, estimate_date, discount_percent) "
                        + "values ('EST-T-1', ?, current_date, 101)", f.partner()), "ck_estimates_discount"));
    }

    @Test
    void refundFlagMustMatchAmountSign() {
        rolledBack(f -> {
            UUID invoice = f.invoice();
            assertViolation(() -> jdbc.update(
                    "insert into payments (invoice_id, partner_id, payment_date, amount, net_amount, is_refund, payment_method) "
                            + "values (?, ?, current_date, -100, -84.75, false, 'UPI')", invoice, f.partner()), "ck_payments_type");
        });
    }

    @Test
    void paymentAmountCannotBeZero() {
        rolledBack(f -> {
            UUID invoice = f.invoice();
            assertViolation(() -> jdbc.update(
                    "insert into payments (invoice_id, partner_id, payment_date, amount, net_amount, payment_method) "
                            + "values (?, ?, current_date, 0, 0, 'UPI')", invoice, f.partner()), "ck_payments_amount");
        });
    }

    @Test
    void onePayoutPerPayment() {
        rolledBack(f -> {
            UUID payment = f.payment();
            String insert = "insert into partner_payouts (payment_id, partner_id, invoice_id, base_amount, commission_percent, "
                    + "gross_commission, accrued_on, due_date) values (?, ?, ?, 1000, 8, 80, current_date, current_date)";
            jdbc.update(insert, payment, f.partner(), f.invoice());
            assertViolation(() -> jdbc.update(insert, payment, f.partner(), f.invoice()), "ux_payouts_payment");
        });
    }

    @Test
    void paidPayoutRequiresPaidDate() {
        rolledBack(f -> assertViolation(() -> jdbc.update(
                "insert into partner_payouts (payment_id, partner_id, invoice_id, base_amount, commission_percent, "
                        + "gross_commission, accrued_on, due_date, status) "
                        + "values (?, ?, ?, 1000, 8, 80, current_date, current_date, 'PAID')",
                f.payment(), f.partner(), f.invoice()), "ck_payouts_paid_fields"));
    }

    @Test
    void b2cTwinHasExactlyOneMatchingParent() {
        rolledBack(f -> {
            UUID estimate = f.estimate();
            UUID invoice = f.invoice();
            assertViolation(() -> jdbc.update(
                    "insert into b2c_documents (document_type, estimate_id, invoice_id, internal_number, parent_number, "
                            + "partner_id, document_date) values ('ESTIMATE', ?, ?, 'INT-T-1', 'EST-T-1', ?, current_date)",
                    estimate, invoice, f.partner()), "ck_b2c_parent");
        });
    }

    @Test
    void oneTwinPerEstimate() {
        rolledBack(f -> {
            UUID estimate = f.estimate();
            String insert = "insert into b2c_documents (document_type, estimate_id, internal_number, parent_number, "
                    + "partner_id, document_date) values ('ESTIMATE', ?, ?, 'EST-T-1', ?, current_date)";
            jdbc.update(insert, estimate, "INT-T-1", f.partner());
            assertViolation(() -> jdbc.update(insert, estimate, "INT-T-2", f.partner()), "ux_b2c_estimate");
        });
    }

    private void rolledBack(Consumer<Fixtures> body) {
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            body.accept(new Fixtures());
        });
    }

    private static void assertViolation(Runnable statement, String constraint) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining(constraint);
    }

    /** Lazily created rows for the current transaction. */
    private final class Fixtures {

        private UUID partner;
        private UUID invoice;
        private UUID estimate;

        UUID partner() {
            if (partner == null) {
                partner = jdbc.queryForObject(
                        "insert into partners (name, category, tier_id) "
                                + "select 'Test Partner', 'ARCHITECT', id from partner_tiers where tier_level = 1 returning id",
                        UUID.class);
            }
            return partner;
        }

        UUID lead(String phone) {
            return jdbc.queryForObject(
                    "insert into leads (partner_id, client_name, client_phone, project_type) "
                            + "values (?, 'Client', ?, 'VILLA') returning id", UUID.class, partner(), phone);
        }

        UUID estimate() {
            if (estimate == null) {
                estimate = jdbc.queryForObject(
                        "insert into estimates (estimate_number, partner_id, estimate_date) "
                                + "values ('EST-T-1', ?, current_date) returning id", UUID.class, partner());
            }
            return estimate;
        }

        UUID invoice() {
            if (invoice == null) {
                invoice = jdbc.queryForObject(
                        "insert into invoices (invoice_number, partner_id, invoice_date, taxable_amount, grand_total, "
                                + "status, issued_at, tax_type) values ('INV-T-1', ?, current_date, 1000, 1180, "
                                + "'ISSUED', now(), 'INTRA') returning id", UUID.class, partner());
            }
            return invoice;
        }

        UUID payment() {
            return jdbc.queryForObject(
                    "insert into payments (invoice_id, partner_id, payment_date, amount, net_amount, payment_method) "
                            + "values (?, ?, current_date, 1180, 1000, 'NEFT_RTGS') returning id",
                    UUID.class, invoice(), partner());
        }
    }
}
