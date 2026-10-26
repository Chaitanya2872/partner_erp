package com.iotiq.partnererp.integration.payments;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 10 payments over HTTP: receipts against issued invoices, partial and multiple payments, server-side ex-GST
 * amounts, overpayment and date validation, duplicate prevention and idempotent resubmission, reversals, the
 * append-only ledger, invoice immutability, permissions, data scope, reports, export and the audit trail.
 */
class PaymentApiIT extends ApiIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final String SUPPLIER_GSTIN = "36AABCI1234A1ZC";

    private Session sa;
    private Session admin;
    private String tag;
    private UUID partnerId;
    private LocalDate today;
    private Map<String, Object> seededSettings;

    @BeforeEach
    void setUp() throws Exception {
        sa = login(createUser(RoleCode.SUPER_ADMIN));
        admin = login(createUser(RoleCode.ADMIN));
        tag = "Y" + String.format("%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000L));
        partnerId = id(content(post("/api/v1/partners"), sa, Map.of("name", "Partner " + tag,
                "category", "INTERIOR_DESIGNER")));
        today = LocalDate.now(ZONE);
        seededSettings = jdbc.queryForMap("select company_name, address from company_settings");
    }

    @AfterEach
    void cleanUp() {
        deleteTestProducts(tag + "%");
        jdbc.update("update company_settings set company_name = ?, address = ?, state_code = null, gstin = null, "
                + "einvoicing_applicable = null", seededSettings.get("company_name"), seededSettings.get("address"));
    }

    // ------------------------------------------------------------------------------------------------ recording

    @Test
    void partialAndMultipleReceiptsSettleAnIssuedInvoiceWithoutTouchingIt() throws Exception {
        UUID invoice = issuedViaApi();
        String before = invoiceRow(invoice);
        String body = mvc.perform(get("/api/v1/invoices/{id}", invoice).with(bearer(admin)))
                .andReturn().getResponse().getContentAsString();
        BigDecimal total = decimal(body, "$.data.totals.grandTotal");
        BigDecimal taxable = decimal(body, "$.data.totals.taxableAmount");
        assertThat(total).isEqualByComparingTo("1180.00");

        String first = record(admin, invoice, "500.00", "UPI", "UTR " + tag + " 1").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(first, "$.data.paymentNumber")).matches("PAY-" + today.getYear() + "-\\d{6}");
        assertThat(decimal(first, "$.data.netAmount")).isEqualByComparingTo("423.73");   // 500 × 1000 / 1180
        assertThat(decimal(first, "$.data.invoice.balance")).isEqualByComparingTo("680.00");
        assertThat((String) JsonPath.read(first, "$.data.invoice.paymentStatus")).isEqualTo("PARTLY_PAID");
        mvc.perform(get("/api/v1/invoices/{id}", invoice).with(bearer(admin)))
                .andExpect(jsonPath("$.data.paymentStatusLabel").value("Partially paid"))
                .andExpect(jsonPath("$.data.balance").value(680.00));

        String second = record(admin, invoice, "680", "NEFT_RTGS", "UTR " + tag + " 2").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // The settling receipt takes the remainder, so the receipts realise exactly the taxable value.
        assertThat(decimal(second, "$.data.netAmount")).isEqualByComparingTo("576.27");
        assertThat(decimal(first, "$.data.netAmount").add(decimal(second, "$.data.netAmount")))
                .isEqualByComparingTo(taxable);
        assertThat((String) JsonPath.read(second, "$.data.invoice.paymentStatus")).isEqualTo("PAID");
        record(admin, invoice, "1", "CASH", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVOICE_SETTLED"));

        // Payments never write the invoice: number, dates, tax snapshot, totals and version are as issued.
        assertThat(invoiceRow(invoice)).isEqualTo(before);
        mvc.perform(get("/api/v1/payments").param("invoiceId", invoice.toString()).with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].invoice.id").value(invoice.toString()));
        Map<String, Object> audit = jdbc.queryForMap("select user_id, new_values::text as nv from audit_logs "
                + "where action = 'PAYMENT_RECORDED' and entity_id = ?", id(first));
        assertThat(audit.get("user_id")).isNotNull();
        assertThat((String) audit.get("nv")).contains("\"balanceAfter\": 680.00", "\"reference\": \"••")
                .doesNotContain("UTR " + tag);
    }

    @Test
    void receiptsAreValidatedAgainstTheInvoiceTheDateAndTheBalance() throws Exception {
        UUID invoice = rawInvoice("ISSUED", "1000.00", "1180.00", today.minusDays(10));
        record(admin, invoice, "0", "UPI", "R1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("amount"));
        record(admin, invoice, "-10", "UPI", "R1").andExpect(status().isBadRequest());
        record(admin, invoice, "10.555", "UPI", "R1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("amount"));
        record(admin, invoice, "10", "UPI", "  ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("reference"));
        record(admin, invoice, "10", "CARD", "4111 1111 1111 1111").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("reference"));
        Map<String, Object> future = payment(invoice, "10", "UPI", "R-future");
        future.put("paymentDate", today.plusDays(1).toString());
        submit(admin, future).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("paymentDate"));
        Map<String, Object> early = payment(invoice, "10", "UPI", "R-early");
        early.put("paymentDate", today.minusDays(11).toString());
        submit(admin, early).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].message").value(org.hamcrest.Matchers.containsString("Advance")));
        Map<String, Object> noKey = payment(invoice, "10", "UPI", "R-key");
        noKey.remove("idempotencyKey");
        submit(admin, noKey).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("idempotencyKey"));
        record(admin, invoice, "1180.01", "UPI", "R-over").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("OVERPAYMENT"))
                .andExpect(jsonPath("$.error.detail").value(org.hamcrest.Matchers.containsString("₹1,180.00")));
        record(admin, invoice, "100", "CASH", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reference").doesNotExist());

        UUID draft = rawInvoice("DRAFT", "100.00", "118.00", null);
        record(admin, draft, "10", "UPI", "R-draft").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVOICE_DRAFT"));
        UUID cancelled = rawInvoice("CANCELLED", "100.00", "118.00", today.minusDays(2));
        record(admin, cancelled, "10", "UPI", "R-cancel").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVOICE_CANCELLED"));
        record(admin, UUID.randomUUID(), "10", "UPI", "R-missing").andExpect(status().isNotFound());
        assertThat(count("select count(*) from payments where invoice_id in (?, ?, ?)", invoice, draft, cancelled))
                .isEqualTo(1);

        // The database refuses what the API refuses, too.
        assertThatThrownBy(() -> jdbc.update("insert into payments (invoice_id, partner_id, payment_date, amount, "
                + "net_amount, payment_method) values (?, ?, current_date, 10, 8.47, 'UPI')", draft, partnerId))
                .hasMessageContaining("issued invoices");
    }

    @Test
    void duplicatesAreRefusedOrConfirmedAndResubmissionsReturnTheRecordedEntry() throws Exception {
        UUID invoice = rawInvoice("ISSUED", "10000.00", "11800.00", today.minusDays(3));
        UUID other = rawInvoice("ISSUED", "10000.00", "11800.00", today.minusDays(3));
        Map<String, Object> request = payment(invoice, "1000", "NEFT_RTGS", "UTR" + tag);
        String first = submit(admin, request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        submit(admin, request).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id(first).toString()));
        Map<String, Object> changed = new HashMap<>(request);
        changed.put("amount", "1001");
        submit(admin, changed).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
        submit(sa, request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));

        // Same reference on the same invoice: refused even when confirmed (case and spacing ignored).
        Map<String, Object> again = payment(invoice, "200", "NEFT_RTGS", " utr" + tag.toLowerCase() + " ");
        again.put("confirmDuplicate", true);
        submit(admin, again).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DUPLICATE_REFERENCE"));

        // Same reference on another invoice, or the same amount, date and method: confirm to record.
        Map<String, Object> elsewhere = payment(other, "1000", "NEFT_RTGS", "UTR" + tag);
        submit(admin, elsewhere).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POSSIBLE_DUPLICATE"));
        elsewhere.put("confirmDuplicate", true);
        String confirmed = submit(admin, elsewhere).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        assertThat(jdbc.queryForObject("select new_values->>'duplicateConfirmed' from audit_logs where entity_id = ?",
                String.class, id(confirmed))).isEqualTo("true");
        record(admin, invoice, "1000", "NEFT_RTGS", "UTR-other-" + tag).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("POSSIBLE_DUPLICATE"));
        record(admin, invoice, "999", "NEFT_RTGS", "UTR-other-" + tag).andExpect(status().isCreated());

        // Six simultaneous submissions of one form record one entry.
        Map<String, Object> burst = payment(invoice, "50", "UPI", "BURST" + tag);
        List<Integer> statuses = concurrently(6, () -> submit(admin, burst).andReturn().getResponse().getStatus());
        assertThat(statuses).containsOnly(201, 200);
        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
        assertThat(count("select count(*) from payments where reference_number = ?", "BURST" + tag)).isEqualTo(1);
    }

    @Test
    void concurrentReceiptsCanNeverOverpayAnInvoice() throws Exception {
        UUID invoice = rawInvoice("ISSUED", "1000.00", "1180.00", today.minusDays(1));
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            // Different amounts, so the same-amount duplicate check doesn't decide the outcome.
            Map<String, Object> request = payment(invoice, String.valueOf(500 + i), "UPI", "PAR" + i + tag);
            tasks.add(() -> submit(admin, request).andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = concurrently(4, tasks);
        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(2);
        assertThat(statuses.stream().filter(s -> s == 422)).hasSize(2);
        assertThat(new BigDecimal(jdbc.queryForObject("select sum(amount)::text from payments where invoice_id = ?",
                String.class, invoice))).isLessThanOrEqualTo(new BigDecimal("1180.00"));
        assertThat(count("select count(*) from payments where invoice_id = ?", invoice)).isEqualTo(2);
    }

    // ------------------------------------------------------------------------------------------------ reversals

    @Test
    void aReversalKeepsTheReceiptRestoresTheBalanceAndHappensOnce() throws Exception {
        UUID invoice = issuedViaApi();
        String before = invoiceRow(invoice);
        String receipt = record(admin, invoice, "500", "CHEQUE", "CHQ-" + tag).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID paymentId = id(receipt);
        String original = paymentRow(paymentId);
        assertThat((Boolean) JsonPath.read(receipt, "$.data.canReverse")).isFalse();   // the default Admin can't reverse

        reverse(admin, paymentId, "Cheque bounced").andExpect(status().isForbidden());
        Session reverser = login(user(List.of(module("PAYMENTS", false)), List.of("REVERSE_PAYMENT")));
        mvc.perform(get("/api/v1/payments/{id}", paymentId).with(bearer(reverser)))
                .andExpect(jsonPath("$.data.canReverse").value(true));
        reverse(reverser, paymentId, "  ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("reason"));
        reverse(reverser, paymentId, "x".repeat(501)).andExpect(status().isBadRequest());

        // Cancelling the invoice is refused while the receipt stands.
        cancel(invoice, "Customer withdrew").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVOICE_HAS_PAYMENTS"));

        String reversed = reverse(reverser, paymentId, "Cheque bounced").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(reversed, "$.data.reversal.reason")).isEqualTo("Cheque bounced");
        assertThat((Boolean) JsonPath.read(reversed, "$.data.canReverse")).isFalse();
        assertThat(decimal(reversed, "$.data.invoice.balance")).isEqualByComparingTo("1180.00");
        assertThat((String) JsonPath.read(reversed, "$.data.invoice.paymentStatus")).isEqualTo("UNPAID");
        UUID reversalId = UUID.fromString(JsonPath.read(reversed, "$.data.reversal.id"));
        mvc.perform(get("/api/v1/payments/{id}", reversalId).with(bearer(admin)))
                .andExpect(jsonPath("$.data.entryType").value("REVERSAL"))
                .andExpect(jsonPath("$.data.amount").value(-500.00))
                .andExpect(jsonPath("$.data.netAmount").value(-423.73))
                .andExpect(jsonPath("$.data.reversalOf.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.reversalReason").value("Cheque bounced"))
                .andExpect(jsonPath("$.data.paymentDate").value(today.toString()));

        // The receipt is untouched; only a second entry was added. Reversals happen once and are not reversed.
        assertThat(paymentRow(paymentId)).isEqualTo(original);
        assertThat(count("select count(*) from payments where invoice_id = ?", invoice)).isEqualTo(2);
        reverse(reverser, paymentId, "Again").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_ALREADY_REVERSED"));
        reverse(reverser, reversalId, "Undo").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_IS_REVERSAL"));
        assertThatThrownBy(() -> jdbc.update("update payments set amount = 1 where id = ?", paymentId))
                .hasMessageContaining("can't be changed");
        assertThatThrownBy(() -> jdbc.update("delete from payments where id = ?", reversalId))
                .hasMessageContaining("can't be deleted");
        assertThatThrownBy(() -> jdbc.update("insert into payments (invoice_id, partner_id, payment_date, amount, "
                        + "net_amount, payment_method, entry_type, reversed_payment_id, reversal_reason) "
                        + "values (?, ?, current_date, -500, -423.73, 'CHEQUE', 'REVERSAL', ?, 'x')", invoice, partnerId,
                paymentId)).hasMessageContaining("ux_payments_reversed");

        // The same reference can be recorded again once the receipt is reversed; the invoice is still as issued.
        record(admin, invoice, "500", "CHEQUE", "CHQ-" + tag).andExpect(status().isCreated());
        assertThat(invoiceRow(invoice)).isEqualTo(before);

        // Audit and history: the reason, the reversal number and a masked reference only.
        String audit = jdbc.queryForObject("select new_values::text from audit_logs where action = 'PAYMENT_REVERSED' "
                + "and entity_id = ?", String.class, paymentId);
        assertThat(audit).contains("Cheque bounced", "reversalNumber", "\"balanceAfter\": 1180.00")
                .doesNotContain("CHQ-" + tag);
        String history = content(get("/api/v1/payments/{id}/history", reversalId), admin);
        assertThat((List<String>) JsonPath.read(history, "$.data[*].action"))
                .containsExactly("PAYMENT_REVERSED", "PAYMENT_RECORDED");
        mvc.perform(get("/api/v1/payments").param("invoiceId", invoice.toString()).param("state", "REVERSED")
                        .with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].reversed").value(true));
    }

    @Test
    void reversalsAreSerialisedBlockedByPayoutsAndUnlockCancellation() throws Exception {
        UUID invoice = rawInvoice("ISSUED", "1000.00", "1180.00", today.minusDays(5));
        UUID receipt = id(record(admin, invoice, "590", "UPI", "UPI-" + tag).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        List<Integer> statuses = concurrently(5, () -> reverse(sa, receipt, "Entered twice")
                .andReturn().getResponse().getStatus());
        assertThat(statuses.stream().filter(s -> s == 200)).hasSize(1);
        assertThat(statuses.stream().filter(s -> s == 409)).hasSize(4);
        assertThat(count("select count(*) from payments where reversed_payment_id = ?", receipt)).isEqualTo(1);

        // A receipt with a partner payout waits for Phase 11's payout adjustment.
        UUID withPayout = id(record(admin, invoice, "100", "UPI", "UPI2-" + tag).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        jdbc.update("insert into partner_payouts (payment_id, partner_id, invoice_id, base_amount, commission_percent, "
                + "gross_commission, accrued_on, due_date) values (?, ?, ?, 84.75, 8, 6.78, current_date, current_date)",
                withPayout, partnerId, invoice);
        mvc.perform(get("/api/v1/payments/{id}", withPayout).with(bearer(sa)))
                .andExpect(jsonPath("$.data.canReverse").value(false))
                .andExpect(jsonPath("$.data.reverseBlockedBy").value(org.hamcrest.Matchers.containsString("Phase 11")));
        reverse(sa, withPayout, "Wrong invoice").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_HAS_PAYOUT"));

        // Once nothing stands against it (and no payout exists), an invoice can be cancelled.
        UUID clean = rawInvoice("ISSUED", "1000.00", "1180.00", today.minusDays(5));
        UUID mistaken = id(record(admin, clean, "1180", "UPI", "UPI3-" + tag).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        cancel(clean, "Raised twice").andExpect(status().isConflict());
        reverse(sa, mistaken, "Recorded against the wrong invoice").andExpect(status().isOk());
        cancel(clean, "Raised twice").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        record(admin, clean, "10", "UPI", "UPI4-" + tag).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVOICE_CANCELLED"));

        // Partner paid revenue (BR-P12) leaves reversed receipts out entirely: only the ₹100 receipt counts.
        mvc.perform(get("/api/v1/partners/{id}", partnerId).with(bearer(sa)))
                .andExpect(jsonPath("$.data.revenue12m").value(84.75));
    }

    // ------------------------------------------------------------------------------------------------ queries

    @Test
    void theLedgerIsSearchedReportedAndExported() throws Exception {
        UUID invoice = rawInvoice("ISSUED", "1000.00", "1180.00", today.minusDays(30));   // due 15 days ago
        UUID settled = rawInvoice("ISSUED", "100.00", "118.00", today.minusDays(2));
        record(admin, invoice, "300", "UPI", "=cmd|" + tag).andExpect(status().isCreated());
        UUID cash = id(record(admin, invoice, "200", "CASH", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        record(admin, settled, "118", "CARD", "AUTH" + tag).andExpect(status().isCreated());
        reverse(sa, cash, "Counted twice").andExpect(status().isOk());

        mvc.perform(get("/api/v1/payments").param("q", "auth" + tag).with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].method").value("CARD"));
        mvc.perform(get("/api/v1/payments").param("partnerId", partnerId.toString()).param("type", "REVERSAL")
                .with(bearer(admin))).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/payments").param("partnerId", partnerId.toString()).param("method", "UPI")
                .with(bearer(admin))).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/payments").param("partnerId", partnerId.toString()).param("state", "ACTIVE")
                .with(bearer(admin))).andExpect(jsonPath("$.data.length()").value(2));

        String report = content(get("/api/v1/payments/report").param("partnerId", partnerId.toString()), admin);
        assertThat(decimal(report, "$.data.received")).isEqualByComparingTo("618.00");
        assertThat(decimal(report, "$.data.reversed")).isEqualByComparingTo("200.00");
        assertThat(decimal(report, "$.data.netReceived")).isEqualByComparingTo("418.00");
        assertThat((Integer) JsonPath.read(report, "$.data.receipts")).isEqualTo(3);
        assertThat((Integer) JsonPath.read(report, "$.data.reversals")).isEqualTo(1);
        assertThat((List<Object>) JsonPath.read(report, "$.data.byMethod[?(@.method == 'CASH')].netReceived"))
                .singleElement().satisfies(v -> assertThat(new BigDecimal(v.toString())).isZero());
        assertThat(decimal(report, "$.data.receivables.outstandingAmount")).isGreaterThanOrEqualTo(new BigDecimal("880.00"));
        assertThat((Integer) JsonPath.read(report, "$.data.receivables.overdueInvoices")).isPositive();

        // Restricted picker: issued invoices with a balance only.
        List<String> options = JsonPath.read(content(get("/api/v1/payments/invoice-options").param("q", "TST-"),
                admin), "$.data[*].id");
        assertThat(options).contains(invoice.toString()).doesNotContain(settled.toString());

        // Export needs Payments export; formula-like text is neutralised.
        mvc.perform(get("/api/v1/payments/export").with(bearer(admin))).andExpect(status().isForbidden());
        String csv = mvc.perform(get("/api/v1/payments/export").param("partnerId", partnerId.toString()).with(bearer(sa)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿payment,type,date,invoice").contains("'=cmd|" + tag, "Reversal", "Counted")
                .contains(",-200.00,-169.49,").doesNotContain(",=cmd");
        assertThat(csv.lines().count()).isEqualTo(5);
    }

    @Test
    void permissionsAndDataScopesAreEnforced() throws Exception {
        UUID invoice = rawInvoice("ISSUED", "1000.00", "1180.00", today.minusDays(1));
        UUID payment = id(record(admin, invoice, "100", "UPI", "P-" + tag).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        Session reader = login(user(List.of(module("PAYMENTS", false)), List.of()));
        mvc.perform(get("/api/v1/payments/{id}", payment).with(bearer(reader))).andExpect(status().isOk());
        record(reader, invoice, "10", "UPI", "P2-" + tag).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/invoice-options").with(bearer(reader))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/meta").with(bearer(reader)))
                .andExpect(jsonPath("$.data.canWrite").value(false)).andExpect(jsonPath("$.data.canReverse").value(false));

        Session outsider = login(user(List.of(module("INVOICES", false)), List.of()));
        mvc.perform(get("/api/v1/payments").with(bearer(outsider))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/{id}", payment).with(bearer(outsider))).andExpect(status().isForbidden());

        Map<String, Object> scoped = module("PAYMENTS", true);
        scoped.put("dataScope", "SELF");
        Session self = login(user(List.of(scoped), List.of("REVERSE_PAYMENT")));
        mvc.perform(get("/api/v1/payments").param("q", tag).with(bearer(self)))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/payments/{id}", payment).with(bearer(self))).andExpect(status().isNotFound());
        reverse(self, payment, "x").andExpect(status().isNotFound());
        record(self, invoice, "10", "UPI", "P3-" + tag).andExpect(status().isNotFound());
        assertThat((List<?>) JsonPath.read(content(get("/api/v1/payments/invoice-options"), self), "$.data")).isEmpty();
        assertThat(count("select count(*) from payments where invoice_id = ?", invoice)).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** An invoice issued through the API (one 18% line of ₹1,000, intra-state), so its tax snapshot is real. */
    private UUID issuedViaApi() throws Exception {
        String name = jdbc.queryForObject("select company_name from company_settings", String.class);
        mvc.perform(withJson(put("/api/v1/settings/gst-registration").with(bearer(sa)),
                body(Map.of("legalName", name, "address", "Plot 7, Test Park, Hyderabad 500081", "stateCode", "36",
                        "gstin", SUPPLIER_GSTIN, "einvoicingApplicable", false,
                        "version", count("select version from company_settings"))))).andExpect(status().isOk());
        UUID lead = id(content(post("/api/v1/leads"), admin, Map.of("clientName", "Client " + tag, "partnerId", partnerId,
                "projectType", "VILLA", "city", "Hyderabad")));
        mvc.perform(post("/api/v1/leads/{id}/acknowledge", lead).with(bearer(sa))).andExpect(status().isOk());
        Map<String, Object> product = new HashMap<>(Map.of("sku", tag + "-P", "category", "SMART_GLIDE",
                "hsn", "85365090", "partnerName", "Partner product " + tag, "partnerPrice", new BigDecimal("1000"),
                "gstPercent", 18, "b2cName", "B2C " + tag));
        UUID productId = id(content(post("/api/v1/products"), sa, product));
        UUID invoice = id(content(post("/api/v1/invoices"), admin, Map.of("leadId", lead, "billingName", "Client " + tag,
                "billingAddress", "Road 1, Hyderabad", "billingStateCode", "36", "placeOfSupplyCode", "36",
                "placeOfSupplyBasis", "DELIVERY_LOCATION",
                "lines", List.of(Map.of("productId", productId, "quantity", 1)))));
        content(post("/api/v1/invoices/{id}/issue", invoice), admin, Map.of("version", 0, "regularSupplyConfirmed", true));
        return invoice;
    }

    /** An invoice inserted directly: issued (dated, due 15 days later), draft or cancelled. */
    private UUID rawInvoice(String status, String taxable, String total, LocalDate date) {
        UUID id = UUID.randomUUID();
        boolean draft = "DRAFT".equals(status);
        jdbc.update("insert into invoices (id, invoice_number, partner_id, invoice_date, due_date, status, issued_at, "
                        + "tax_type, taxable_amount, grand_total, cancelled_at, cancel_reason) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, draft ? null : "TST-" + id.toString().substring(0, 12), partnerId, date,
                date == null ? null : date.plusDays(15), status, draft ? null : java.sql.Timestamp.from(java.time.Instant.now()),
                draft ? null : "INTRA", new BigDecimal(taxable), new BigDecimal(total),
                "CANCELLED".equals(status) ? java.sql.Timestamp.from(java.time.Instant.now()) : null,
                "CANCELLED".equals(status) ? "Test" : null);
        return id;
    }

    private String invoiceRow(UUID id) {
        return jdbc.queryForObject("select row_to_json(i)::text from (select invoice_number, status, invoice_date, "
                + "due_date, supplier_gstin, supplier_state_code, place_of_supply_code, tax_type, taxable_amount, "
                + "cgst_amount, sgst_amount, igst_amount, grand_total, version, updated_at, "
                + "(select json_agg(it order by it.line_no) from invoice_items it where it.invoice_id = invoices.id) lines "
                + "from invoices where id = ?) i", String.class, id);
    }

    private String paymentRow(UUID id) {
        return jdbc.queryForObject("select row_to_json(p)::text from payments p where id = ?", String.class, id);
    }

    private Map<String, Object> payment(UUID invoice, String amount, String method, String reference) {
        Map<String, Object> map = new HashMap<>();
        map.put("invoiceId", invoice);
        map.put("paymentDate", today.toString());
        map.put("amount", amount);
        map.put("method", method);
        if (reference != null) {
            map.put("reference", reference);
        }
        map.put("idempotencyKey", UUID.randomUUID());
        return map;
    }

    private ResultActions record(Session session, UUID invoice, String amount, String method, String reference)
            throws Exception {
        return submit(session, payment(invoice, amount, method, reference));
    }

    private ResultActions submit(Session session, Map<String, Object> request) throws Exception {
        return mvc.perform(withJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/payments").with(bearer(session)), body(request)));
    }

    private ResultActions reverse(Session session, UUID id, String reason) throws Exception {
        return mvc.perform(withJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/payments/{id}/reverse", id).with(bearer(session)), body(Map.of("reason", reason))));
    }

    private ResultActions cancel(UUID invoice, String reason) throws Exception {
        long version = count("select version from invoices where id = ?", invoice);
        return mvc.perform(withJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/invoices/{id}/cancel", invoice).with(bearer(sa)),
                body(Map.of("version", version, "reason", reason))));
    }

    private <T> List<T> concurrently(int n, Callable<T> task) throws Exception {
        List<Callable<T>> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tasks.add(task);
        }
        return concurrently(n, tasks);
    }

    private <T> List<T> concurrently(int threads, List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<T> results = new ArrayList<>();
            for (Future<T> f : pool.invokeAll(tasks)) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private String content(MockHttpServletRequestBuilder request, Session session) throws Exception {
        return mvc.perform(request.with(bearer(session))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String content(MockHttpServletRequestBuilder request, Session session, Map<String, Object> body)
            throws Exception {
        return mvc.perform(withJson(request.with(bearer(session)), body(body))).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static UUID id(String body) {
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private static BigDecimal decimal(String body, String path) {
        Object value = JsonPath.read(body, path);
        return new BigDecimal(value.toString());
    }

    private static Map<String, Object> module(String code, boolean write) {
        Map<String, Object> map = new HashMap<>();
        map.put("moduleCode", code);
        map.put("read", true);
        map.put("write", write);
        return map;
    }

    private TestUser user(List<Map<String, Object>> modules, List<String> actions) throws Exception {
        TestUser user = createUser(RoleCode.ADMIN);
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", user.id()).with(bearer(sa)),
                        body(Map.of("modules", modules, "actions", actions))))
                .andExpect(status().isOk());
        return user;
    }
}
