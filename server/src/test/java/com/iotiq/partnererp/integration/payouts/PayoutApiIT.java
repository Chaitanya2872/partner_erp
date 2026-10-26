package com.iotiq.partnererp.integration.payouts;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.payouts.service.PayoutRules;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.servlet.ResultActions;

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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 11 partner payouts over HTTP: commission from realised receipts at the invoice's rate snapshot, generation and
 * allocation (idempotent, one live allocation per receipt, concurrent runs), the approval workflow with separation of
 * duties, configurable TDS (missing configuration, thresholds, PAN, adjustments), payment reversals before and after
 * approval and after payment (negative carry-forward), manual adjustments, masking of bank details, export and audit.
 *
 * <p>TDS values used here (10% / ₹1,000) are test values only, not statutory ones. Payout tables and TDS
 * configurations are append-only in production; this test database is cleared around each test with the guards
 * switched off for the cleanup only.
 */
class PayoutApiIT extends ApiIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private Session sa;
    private Session sa2;
    private Session admin;
    private String tag;
    private LocalDate today;
    private int counter;
    private int references;

    @BeforeEach
    void setUp() throws Exception {
        clearPayouts();
        sa = login(createUser(RoleCode.SUPER_ADMIN));
        sa2 = login(createUser(RoleCode.SUPER_ADMIN));
        admin = login(createUser(RoleCode.ADMIN));
        tag = "Z" + String.format("%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000L));
        today = LocalDate.now(ZONE);
    }

    @AfterEach
    void cleanUp() {
        clearPayouts();
    }

    // ------------------------------------------------------------------------------------------------ commission

    @Test
    void commissionUsesTheInvoiceRateSnapshotAcrossPartialAndMultipleReceipts() throws Exception {
        UUID partner = partner(true, true, true);
        UUID a = invoice(partner, "10000.00", "11800.00", "8", today.minusDays(20));
        UUID b = invoice(partner, "1000.00", "1180.00", "10", today.minusDays(20));
        UUID c = invoice(partner, "1000.00", "1180.00", "7.5", today.minusDays(20));
        UUID free = invoice(partner, "1000.00", "1180.00", "0", today.minusDays(20));
        receipt(a, "5900");                   // partial: ₹5,000 ex-GST → ₹400
        receipt(a, "5900");                   // settling: ₹5,000 → ₹400
        receipt(b, "1180");                   // ₹1,000 at 10% → ₹100
        receipt(c, "393.33");                 // ₹333.33 at 7.5% = 24.99975 → ₹25.00
        receipt(free, "1180");                // no fee on this invoice
        // The partner's current tier never changes commission already earned at the invoice's rate.
        jdbc.update("update partners set tier_id = (select id from partner_tiers where tier_level = 4) where id = ?",
                partner);

        String preview = preview(partner);
        assertThat(decimal(preview, "$.data.commissionTotal")).isEqualByComparingTo("925.00");
        assertThat((List<?>) JsonPath.read(preview, "$.data.lines")).hasSize(4);
        assertThat((List<Double>) JsonPath.read(preview, "$.data.lines[*].commissionPercent"))
                .containsExactlyInAnyOrder(8.0, 8.0, 10.0, 7.5);
        assertThat((Boolean) JsonPath.read(preview, "$.data.canCreate")).isTrue();
        assertThat((String) JsonPath.read(preview, "$.data.tds.status")).isEqualTo("CONFIG_INCOMPLETE");

        UUID key = UUID.randomUUID();
        String created = create(sa, partner, key, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID payout = id(created);
        assertThat((String) JsonPath.read(created, "$.data.payoutNumber")).matches("PO-" + today.getYear() + "-\\d{6}");
        assertThat((String) JsonPath.read(created, "$.data.status")).isEqualTo("DRAFT");
        assertThat(decimal(created, "$.data.commissionTotal")).isEqualByComparingTo("925.00");
        assertThat((Object) JsonPath.read(created, "$.data.netAmount")).isNull();
        assertThat(sum("select sum(amount) from payout_lines where payout_id = ? and released_at is null", payout))
                .isEqualByComparingTo("925.00");

        // Resubmitting the form returns the same payout; reusing its key for other inputs is refused.
        create(sa, partner, key, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(payout.toString()));
        create(sa, partner, key, "other notes").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
        create(sa, partner, UUID.randomUUID(), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYOUT_OPEN_EXISTS"));
        // The partner's balance shows the commission as in progress, none left to allocate.
        mvc.perform(get("/api/v1/payouts/partners/{id}", partner).with(bearer(sa)))
                .andExpect(jsonPath("$.data.eligibleCommission").value(0.0))
                .andExpect(jsonPath("$.data.inProgress").value(925.0));
    }

    @Test
    void concurrentRunsAllocateEachReceiptOnce() throws Exception {
        UUID partner = partner(true, true, true);
        UUID invoice = invoice(partner, "10000.00", "11800.00", "8", today.minusDays(5));
        receipt(invoice, "1180");
        receipt(invoice, "2360");
        receipt(invoice, "1180");
        List<Integer> statuses = concurrently(5, () -> create(sa, partner, UUID.randomUUID(), null)
                .andReturn().getResponse().getStatus());
        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
        assertThat(statuses.stream().filter(s -> s == 409)).hasSize(4);
        assertThat(count("select count(*) from payouts where partner_id = ?", partner)).isEqualTo(1);
        assertThat(count("select count(*) from payout_lines l join payouts p on p.id = l.payout_id "
                + "where p.partner_id = ? and l.released_at is null", partner)).isEqualTo(3);
        // The database refuses a second live allocation of a receipt whatever the code does.
        UUID payment = jdbc.queryForObject("select payment_id from payout_lines l join payouts p on p.id = l.payout_id "
                + "where p.partner_id = ? limit 1", UUID.class, partner);
        assertThat(jdbc.queryForObject("select count(*) from payout_lines where payment_id = ? and released_at is null",
                Long.class, payment)).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------------ TDS

    @Test
    void withoutConfirmedTdsPayoutsAreDraftedButNotApproved() throws Exception {
        UUID partner = partner(true, true, true);
        receipt(invoice(partner, "10000.00", "11800.00", "8", today.minusDays(5)), "5900");
        mvc.perform(get("/api/v1/payouts/meta").with(bearer(sa)))
                .andExpect(jsonPath("$.data.tdsConfigured").value(false))
                .andExpect(jsonPath("$.data.tdsMessage").value(containsString("Finance")));
        UUID payout = id(create(sa, partner, UUID.randomUUID(), null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        action(sa, payout, "submit", Map.of()).andExpect(status().isOk());
        mvc.perform(get("/api/v1/payouts/{id}", payout).with(bearer(sa2)))
                .andExpect(jsonPath("$.data.tds.status").value("CONFIG_INCOMPLETE"))
                .andExpect(jsonPath("$.data.blockers[0]").value(containsString("TDS settings")));
        action(sa2, payout, "approve", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TDS_NOT_READY"));

        // Once Finance's configuration is confirmed, the payout must be recalculated before approval.
        confirmTds("10", null, "1000.00", PayoutRules.fyStart(today));
        action(sa2, payout, "approve", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYOUT_RECALCULATION_REQUIRED"));
        action(sa, payout, "recalculate", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tds.status").value("CALCULATED"))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
        action(sa2, payout, "approve", Map.of("note", "Checked")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.netAmount").value(400.0));
    }

    @Test
    void tdsFollowsTheThresholdWithCatchUpAndRecordsEachCalculation() throws Exception {
        confirmTds("10", null, "1000.00", PayoutRules.fyStart(today));
        UUID partner = partner(true, true, true);
        receipt(invoice(partner, "10000.00", "11800.00", "8", today.minusDays(9)), "5900");   // ₹400
        String first = payThrough(partner);
        assertThat(decimal(first, "$.data.tds.tdsAmount")).isEqualByComparingTo("0.00");
        assertThat(decimal(first, "$.data.netAmount")).isEqualByComparingTo("400.00");

        receipt(invoice(partner, "10000.00", "11800.00", "8", today.minusDays(8)), "11800");  // ₹800
        String second = payThrough(partner);
        // ₹400 + ₹800 = ₹1,200 crosses ₹1,000: TDS on the whole ₹1,200 at 10%.
        assertThat(decimal(second, "$.data.tds.tdsBase")).isEqualByComparingTo("1200.00");
        assertThat(decimal(second, "$.data.tds.tdsAmount")).isEqualByComparingTo("120.00");
        assertThat(decimal(second, "$.data.tds.priorFyCommission")).isEqualByComparingTo("400.00");
        assertThat(decimal(second, "$.data.netAmount")).isEqualByComparingTo("680.00");
        assertThat((List<String>) JsonPath.read(second, "$.data.calculations[*].event"))
                .containsExactly("APPROVED", "GENERATED");

        // Then only new commission; a TDS adjustment needs a reason and is shown beside the computed amount.
        receipt(invoice(partner, "1000.00", "1180.00", "10", today.minusDays(7)), "1180");     // ₹100
        UUID third = id(create(sa, partner, UUID.randomUUID(), null).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/payouts/{id}", third).with(bearer(sa)))
                .andExpect(jsonPath("$.data.tds.tdsBase").value(100.0))
                .andExpect(jsonPath("$.data.tds.tdsAmount").value(10.0));
        action(sa, third, "tds-adjustment", Map.of("tdsAmount", "5.00")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("reason"));
        action(sa, third, "tds-adjustment", Map.of("tdsAmount", "100.00", "reason", "x"))
                .andExpect(status().isBadRequest());
        action(sa, third, "tds-adjustment", Map.of("tdsAmount", "5.00", "reason", "Lower deduction certificate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tds.adjusted").value(true))
                .andExpect(jsonPath("$.data.tds.computedTds").value(10.0))
                .andExpect(jsonPath("$.data.tds.tdsAmount").value(5.0))
                .andExpect(jsonPath("$.data.netAmount").value(95.0))
                .andExpect(jsonPath("$.data.calculations[0].event").value("ADJUSTED"))
                .andExpect(jsonPath("$.data.calculations[0].adjustmentReason").value("Lower deduction certificate"));
        action(sa, third, "submit", Map.of()).andExpect(status().isOk());
        action(sa2, third, "approve", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.netAmount").value(95.0));
        assertThat(count("select count(*) from audit_logs where action = 'PAYOUT_TDS_ADJUSTED' and entity_id = ?", third))
                .isEqualTo(1);
    }

    @Test
    void anUnverifiedPanBlocksApprovalUnlessARateWithoutPanIsConfigured() throws Exception {
        confirmTds("10", null, "0.00", PayoutRules.fyStart(today));
        UUID partner = partner(true, true, false);
        receipt(invoice(partner, "10000.00", "11800.00", "8", today.minusDays(3)), "5900");
        UUID payout = id(create(sa, partner, UUID.randomUUID(), null).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/payouts/{id}", payout).with(bearer(sa)))
                .andExpect(jsonPath("$.data.tds.status").value("PAN_NOT_VERIFIED"))
                .andExpect(jsonPath("$.data.blockers[0]").value(containsString("PAN")));
        action(sa, payout, "submit", Map.of()).andExpect(status().isOk());
        action(sa2, payout, "approve", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TDS_NOT_READY"));
        verifyPan(partner);
        action(sa, payout, "recalculate", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tds.status").value("CALCULATED"))
                .andExpect(jsonPath("$.data.tds.tdsAmount").value(40.0));
        // Changing the PAN withdraws the verification (V18 trigger).
        jdbc.update("update partners set pan = ? where id = ?", "ABCPZ" + tag.substring(5) + "Q", partner);
        assertThat(count("select count(*) from partners where id = ? and pan_verified_at is null", partner)).isEqualTo(1);
    }

    @Test
    void tdsSettingsAreEnteredByOnePersonAndConfirmedByAnother() throws Exception {
        String draft = tds(sa, Map.of("sectionCode", "TEST", "ratePercent", "10")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(draft, "$.data.configured")).isFalse();
        int version = JsonPath.read(draft, "$.data.draft.version");
        confirm(sa2, version, "Confirmed by Finance").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("TDS_INCOMPLETE"));
        draft = tds(sa, Map.of("sectionCode", "TEST", "ratePercent", "10", "fyThreshold", "1000",
                "effectiveFrom", PayoutRules.fyStart(today).toString(), "version", version))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        version = JsonPath.read(draft, "$.data.draft.version");
        tds(sa, Map.of("ratePercent", "101", "version", version)).andExpect(status().isBadRequest());
        confirm(sa, version, "Confirmed").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SELF_APPROVAL"));
        confirm(sa2, version, " ").andExpect(status().isBadRequest());
        confirm(admin, version, "Confirmed").andExpect(status().isForbidden());
        confirm(sa2, version, "Confirmed by Finance on test data").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.inForce.sectionCode").value("TEST"))
                .andExpect(jsonPath("$.data.draft").doesNotExist());
        UUID confirmed = jdbc.queryForObject("select id from tds_configurations where status = 'CONFIRMED'", UUID.class);
        assertThatThrownBy(() -> jdbc.update("update tds_configurations set rate_percent = 5 where id = ?", confirmed))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("can't be changed");
        // A new version must take effect after the current one.
        String next = tds(sa, Map.of("sectionCode", "TEST", "ratePercent", "5", "fyThreshold", "1000",
                "effectiveFrom", PayoutRules.fyStart(today).toString())).andReturn().getResponse().getContentAsString();
        confirm(sa2, JsonPath.read(next, "$.data.draft.version"), "Second version").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TDS_EFFECTIVE_DATE"));
        assertThat(count("select count(*) from audit_logs where action = 'TDS_CONFIGURATION_CONFIRMED' and entity_id = ?",
                confirmed)).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------------ workflow

    @Test
    void creatorsCannotApproveAndEachStepNeedsItsPermission() throws Exception {
        confirmTds("10", null, "100000.00", PayoutRules.fyStart(today));
        UUID partner = partner(true, true, true);
        receipt(invoice(partner, "10000.00", "11800.00", "8", today.minusDays(3)), "5900");
        create(admin, partner, UUID.randomUUID(), null).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payouts").with(bearer(admin))).andExpect(status().isOk());
        UUID payout = id(create(sa, partner, UUID.randomUUID(), null).andReturn().getResponse().getContentAsString());
        action(sa, payout, "submit", Map.of()).andExpect(status().isOk());
        action(sa, payout, "approve", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SELF_APPROVAL"));
        action(sa, payout, "reject", Map.of("reason", "x")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SELF_APPROVAL"));
        mvc.perform(get("/api/v1/payouts/{id}", payout).with(bearer(sa)))
                .andExpect(jsonPath("$.data.actions.approve").value(false));
        action(admin, payout, "approve", Map.of()).andExpect(status().isForbidden());

        // An approver without the disbursement action can approve but not record the transfer.
        Map<String, Object> approveOnly = module("PAYOUTS", false);
        approveOnly.put("approve", true);
        Session approver = login(user(List.of(approveOnly, module("PARTNERS", false)), List.of()));
        mvc.perform(get("/api/v1/payouts/{id}", payout).with(bearer(approver)))
                .andExpect(jsonPath("$.data.actions.approve").value(true));
        action(approver, payout, "approve", Map.of()).andExpect(status().isOk());
        action(approver, payout, "processing", Map.of()).andExpect(status().isForbidden());
        Session disburser = login(user(List.of(module("PAYOUTS", false)), List.of("MARK_PAYOUT_PAID")));
        create(disburser, partner, UUID.randomUUID(), null).andExpect(status().isForbidden());
        action(disburser, payout, "processing", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflow.bankAccountLast4").value("••9012"));
        markPaid(disburser, payout, today, "UTR" + tag, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        // Payouts need the All scope: a narrower one sees nothing and can't generate.
        Map<String, Object> scoped = module("PAYOUTS", true);
        scoped.put("dataScope", "SELF");
        Session narrow = login(user(List.of(scoped), List.of()));
        mvc.perform(get("/api/v1/payouts").with(bearer(narrow))).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/payouts/{id}", payout).with(bearer(narrow))).andExpect(status().isNotFound());
        create(narrow, partner, UUID.randomUUID(), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYOUTS_SCOPE"));
    }

    @Test
    void whoeverChangesTheTdsByHandCantApproveThatPayout() throws Exception {
        confirmTds("10", null, "100.00", PayoutRules.fyStart(today));
        UUID partner = partner(true, true, true);
        receipt(invoice(partner, "10000.00", "11800.00", "8", today.minusDays(3)), "5900");
        UUID payout = id(create(sa, partner, UUID.randomUUID(), null).andReturn().getResponse().getContentAsString());
        action(sa, payout, "submit", Map.of()).andExpect(status().isOk());
        action(sa2, payout, "tds-adjustment", Map.of("tdsAmount", "20.00", "reason", "Lower-deduction certificate (test)"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tds.adjusted").value(true));
        mvc.perform(get("/api/v1/payouts/{id}", payout).with(bearer(sa2)))
                .andExpect(jsonPath("$.data.actions.approve").value(false))
                .andExpect(jsonPath("$.data.actions.reject").value(true));
        action(sa2, payout, "approve", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SELF_APPROVAL"));

        Session third = login(createUser(RoleCode.SUPER_ADMIN));
        action(third, payout, "approve", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.netAmount").value(380.00));
    }

    @Test
    void invalidTransitionsReasonsAndStaleVersionsAreRefused() throws Exception {
        UUID partner = partner(true, true, true);
        receipt(invoice(partner, "10000.00", "11800.00", "8", today.minusDays(3)), "5900");
        UUID payout = id(create(sa, partner, UUID.randomUUID(), null).andReturn().getResponse().getContentAsString());
        action(sa2, payout, "approve", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_TRANSITION"));
        markPaid(sa2, payout, today, "UTR1", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_TRANSITION"));
        action(sa2, payout, "fail", Map.of("reason", "x")).andExpect(status().isConflict());
        action(sa, payout, "cancel", Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("reason"));
        mvc.perform(withJson(post("/api/v1/payouts/{id}/submit", payout).with(bearer(sa)),
                        body(Map.of("version", version(payout) + 1))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));
        action(sa, payout, "cancel", Map.of("reason", "Generated too early")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.workflow.statusReason").value("Generated too early"))
                .andExpect(jsonPath("$.data.lines.length()").value(0))
                .andExpect(jsonPath("$.data.releasedLines[0].releaseReason").value("CANCELLED"));
        action(sa, payout, "submit", Map.of()).andExpect(status().isConflict());
        // The cancelled payout's receipt is free for the next payout; the cancelled one stays as history.
        UUID next = id(create(sa, partner, UUID.randomUUID(), null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(next).isNotEqualTo(payout);
        assertThat(count("select count(*) from payouts where partner_id = ?", partner)).isEqualTo(2);
    }

    @Test
    void aPayoutIsPaidInTheFinancialYearItsTdsWasCalculatedFor() throws Exception {
        LocalDate lastYearEnd = PayoutRules.fyStart(today).minusDays(1);
        confirmTds("10", null, "100000.00", PayoutRules.fyStart(lastYearEnd));
        UUID partner = partner(true, true, true);
        UUID invoice = invoice(partner, "10000.00", "11800.00", "8", lastYearEnd.minusDays(10));
        Map<String, Object> request = payment(invoice, "5900");
        request.put("paymentDate", lastYearEnd.toString());
        content(post("/api/v1/payments"), sa, request);
        Map<String, Object> body = new HashMap<>(Map.of("partnerId", partner, "idempotencyKey", UUID.randomUUID(),
                "asOfDate", lastYearEnd.toString()));
        UUID payout = id(content(post("/api/v1/payouts"), sa, body));
        action(sa, payout, "submit", Map.of()).andExpect(status().isOk());
        action(sa2, payout, "approve", Map.of()).andExpect(status().isOk());
        action(sa2, payout, "processing", Map.of()).andExpect(status().isOk());
        markPaid(sa2, payout, today, "UTR" + tag, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYOUT_FY_MISMATCH"));
        markPaid(sa2, payout, today.plusDays(1), "UTR" + tag, null).andExpect(status().isBadRequest());
        action(sa2, payout, "fail", Map.of()).andExpect(status().isBadRequest());
        action(sa2, payout, "fail", Map.of("reason", "Transfer returned by the bank")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.releasedLines[0].releaseReason").value("FAILED"));
    }

    // ------------------------------------------------------------------------------------------------ reversals

    @Test
    void aReceiptReversedBeforeAPayoutRunIsLeftOut() throws Exception {
        UUID partner = partner(true, true, true);
        UUID invoice = invoice(partner, "10000.00", "11800.00", "8", today.minusDays(3));
        UUID kept = receipt(invoice, "5900");
        UUID reversed = receipt(invoice, "1180");
        reverse(reversed, "Entered twice").andExpect(status().isOk());
        String preview = preview(partner);
        assertThat((List<String>) JsonPath.read(preview, "$.data.lines[*].paymentId")).containsExactly(kept.toString());
        assertThat(decimal(preview, "$.data.commissionTotal")).isEqualByComparingTo("400.00");
    }

    @Test
    void aReceiptReversedInADraftPayoutForcesARecalculation() throws Exception {
        UUID partner = partner(true, true, true);
        UUID invoice = invoice(partner, "10000.00", "11800.00", "8", today.minusDays(3));
        receipt(invoice, "5900");
        UUID second = receipt(invoice, "1180");
        UUID payout = id(create(sa, partner, UUID.randomUUID(), null).andReturn().getResponse().getContentAsString());
        reverse(second, "Wrong invoice").andExpect(status().isOk());
        mvc.perform(get("/api/v1/payouts").param("partnerId", partner.toString()).with(bearer(sa)))
                .andExpect(jsonPath("$.data[0].blocked").value(true));
        mvc.perform(get("/api/v1/payouts/{id}", payout).with(bearer(sa)))
                .andExpect(jsonPath("$.data.reversedSources.length()").value(1))
                .andExpect(jsonPath("$.data.blockers[0]").value(containsString("Recalculate")));
        action(sa, payout, "submit", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYOUT_RECALCULATION_REQUIRED"));
        action(sa, payout, "recalculate", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionTotal").value(400.0))
                .andExpect(jsonPath("$.data.lines.length()").value(1))
                .andExpect(jsonPath("$.data.releasedLines[0].releaseReason").value("RECALCULATED"))
                .andExpect(jsonPath("$.data.reversedSources.length()").value(0));
        assertThat(count("select count(*) from partner_adjustments where partner_id = ?", partner)).isZero();
        action(sa, payout, "submit", Map.of()).andExpect(status().isOk());
    }

    @Test
    void aReceiptReversedAfterApprovalBlocksThePayoutUntilItIsCancelled() throws Exception {
        confirmTds("10", null, "100000.00", PayoutRules.fyStart(today));
        UUID partner = partner(true, true, true);
        UUID invoice = invoice(partner, "10000.00", "11800.00", "8", today.minusDays(3));
        receipt(invoice, "5900");
        UUID second = receipt(invoice, "1180");
        UUID payout = id(create(sa, partner, UUID.randomUUID(), null).andReturn().getResponse().getContentAsString());
        action(sa, payout, "submit", Map.of()).andExpect(status().isOk());
        action(sa2, payout, "approve", Map.of()).andExpect(status().isOk());
        String frozen = payoutRow(payout);

        reverse(second, "Cheque bounced").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select new_values::text from audit_logs where action = 'PAYMENT_REVERSED' "
                + "and entity_id = ?", String.class, second)).contains("\"payoutStatus\": \"APPROVED\"");
        assertThat(payoutRow(payout)).isEqualTo(frozen);
        action(sa2, payout, "processing", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYOUT_BLOCKED"));
        mvc.perform(get("/api/v1/payouts/{id}", payout).with(bearer(sa2)))
                .andExpect(jsonPath("$.data.actions.startProcessing").value(false))
                .andExpect(jsonPath("$.data.blockers[0]").value(containsString("Cancel")));
        action(sa2, payout, "cancel", Map.of("reason", "Receipt reversed after approval")).andExpect(status().isOk());
        String next = preview(partner);
        assertThat(decimal(next, "$.data.commissionTotal")).isEqualByComparingTo("400.00");
        assertThat(count("select count(*) from partner_adjustments where partner_id = ?", partner)).isZero();
    }

    @Test
    void aReceiptReversedDuringProcessingIsPaidOnlyWithAcknowledgement() throws Exception {
        confirmTds("10", null, "100000.00", PayoutRules.fyStart(today));
        UUID partner = partner(true, true, true);
        UUID invoice = invoice(partner, "10000.00", "11800.00", "8", today.minusDays(3));
        receipt(invoice, "5900");
        UUID second = receipt(invoice, "1180");
        UUID payout = id(create(sa, partner, UUID.randomUUID(), null).andReturn().getResponse().getContentAsString());
        action(sa, payout, "submit", Map.of()).andExpect(status().isOk());
        action(sa2, payout, "approve", Map.of()).andExpect(status().isOk());
        action(sa2, payout, "processing", Map.of()).andExpect(status().isOk());
        reverse(second, "Cheque bounced").andExpect(status().isOk());
        markPaid(sa2, payout, today, "UTR" + tag, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYOUT_BLOCKED"));
        markPaid(sa2, payout, today, "UTR" + tag, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.commissionTotal").value(480.0));
        Map<String, Object> adjustment = jdbc.queryForMap("select kind, amount, status from partner_adjustments "
                + "where partner_id = ?", partner);
        assertThat(adjustment.get("kind")).isEqualTo("REVERSAL");
        assertThat((BigDecimal) adjustment.get("amount")).isEqualByComparingTo("-80.00");
        assertThat(adjustment.get("status")).isEqualTo("APPROVED");
    }

    @Test
    void aReceiptReversedAfterPaymentCarriesANegativeAdjustmentForward() throws Exception {
        confirmTds("10", null, "100000.00", PayoutRules.fyStart(today));
        UUID partner = partner(true, true, true);
        UUID receipt = receipt(invoice(partner, "10000.00", "11800.00", "8", today.minusDays(9)), "5900");  // ₹400
        UUID paid = id(payThrough(partner));
        String before = payoutRow(paid);

        reverse(receipt, "Customer payment recalled").andExpect(status().isOk());
        assertThat(payoutRow(paid)).isEqualTo(before);
        Map<String, Object> adjustment = jdbc.queryForMap("select id, kind, amount, status, source_payment_id, reason "
                + "from partner_adjustments where partner_id = ?", partner);
        assertThat(adjustment.get("kind")).isEqualTo("REVERSAL");
        assertThat((BigDecimal) adjustment.get("amount")).isEqualByComparingTo("-400.00");
        assertThat((String) adjustment.get("reason")).contains("Customer payment recalled");
        UUID reversalEntry = jdbc.queryForObject("select id from payments where reversed_payment_id = ?", UUID.class,
                receipt);
        assertThat(adjustment.get("source_payment_id")).isEqualTo(reversalEntry);
        mvc.perform(get("/api/v1/payouts/partners/{id}", partner).with(bearer(sa)))
                .andExpect(jsonPath("$.data.carriedAdjustments").value(-400.0));

        // ₹100 of new commission doesn't cover the ₹400 carried: nothing can be paid, and the deduction stays.
        receipt(invoice(partner, "1000.00", "1180.00", "10", today.minusDays(2)), "1180");
        String short_ = preview(partner);
        assertThat(decimal(short_, "$.data.commissionTotal")).isEqualByComparingTo("-300.00");
        assertThat((Boolean) JsonPath.read(short_, "$.data.canCreate")).isFalse();
        create(sa, partner, UUID.randomUUID(), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NOTHING_TO_PAY"));

        receipt(invoice(partner, "10000.00", "11800.00", "8", today.minusDays(1)), "11800");               // ₹800
        String next = create(sa, partner, UUID.randomUUID(), null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(next, "$.data.commissionTotal")).isEqualByComparingTo("500.00");
        assertThat(decimal(next, "$.data.adjustmentsTotal")).isEqualByComparingTo("-400.00");
        assertThat((List<String>) JsonPath.read(next, "$.data.lines[*].lineType")).contains("ADJUSTMENT");
        mvc.perform(get("/api/v1/payouts/adjustments").param("partnerId", partner.toString()).with(bearer(sa)))
                .andExpect(jsonPath("$.data[0].allocatedTo.id").value(id(next).toString()));
    }

    @Test
    void aReceiptReversedWhilePendingBlocksApprovalUntilRecalculated() throws Exception {
        confirmTds("10", null, "100000.00", PayoutRules.fyStart(today));
        UUID partner = partner(true, true, true);
        UUID invoice = invoice(partner, "10000.00", "11800.00", "8", today.minusDays(3));
        receipt(invoice, "5900");
        UUID second = receipt(invoice, "1180");
        UUID payout = id(create(sa, partner, UUID.randomUUID(), null).andReturn().getResponse().getContentAsString());
        action(sa, payout, "submit", Map.of()).andExpect(status().isOk());
        reverse(second, "Cheque bounced").andExpect(status().isOk());
        action(sa2, payout, "approve", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYOUT_RECALCULATION_REQUIRED"));
        // Recalculating releases the reversed receipt; the payout stays pending for the approver.
        action(sa, payout, "recalculate", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.commissionTotal").value(400.0))
                .andExpect(jsonPath("$.data.releasedLines[0].releaseReason").value("RECALCULATED"));
        action(sa2, payout, "approve", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tds.tdsAmount").value(0.0))
                .andExpect(jsonPath("$.data.netAmount").value(400.0));
        assertThat(count("select count(*) from partner_adjustments where partner_id = ?", partner)).isZero();
    }

    @Test
    void rejectedAndFailedPayoutsReleaseTheirReceiptsForTheNextRun() throws Exception {
        confirmTds("10", null, "100000.00", PayoutRules.fyStart(today));
        UUID partner = partner(true, true, true);
        receipt(invoice(partner, "10000.00", "11800.00", "8", today.minusDays(3)), "5900");
        UUID first = id(create(sa, partner, UUID.randomUUID(), null).andReturn().getResponse().getContentAsString());
        action(sa, first, "submit", Map.of()).andExpect(status().isOk());
        action(sa2, first, "reject", Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("reason"));
        action(sa2, first, "reject", Map.of("reason", "Statement not attached")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.releasedLines[0].releaseReason").value("REJECTED"));

        UUID second = id(create(sa, partner, UUID.randomUUID(), null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        action(sa, second, "submit", Map.of()).andExpect(status().isOk());
        action(sa2, second, "approve", Map.of()).andExpect(status().isOk());
        action(sa2, second, "processing", Map.of()).andExpect(status().isOk());
        action(sa2, second, "fail", Map.of()).andExpect(status().isBadRequest());
        action(sa2, second, "fail", Map.of("reason", "Beneficiary account closed")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.releasedLines[0].releaseReason").value("FAILED"))
                // A closed payout keeps its totals: the lines released on closing still make up its commission.
                .andExpect(jsonPath("$.data.receiptsTotal").value(400.00))
                .andExpect(jsonPath("$.data.commissionTotal").value(400.00));
        markPaid(sa2, second, today, "UTR1", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_TRANSITION"));

        // Both closed payouts stay on record; the receipt is in exactly one live line, of the third payout.
        String third = create(sa, partner, UUID.randomUUID(), null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(third, "$.data.commissionTotal")).isEqualByComparingTo("400.00");
        assertThat(count("select count(*) from payouts where partner_id = ?", partner)).isEqualTo(3);
        assertThat(count("select count(*) from payout_lines l join payouts p on p.id = l.payout_id "
                + "where p.partner_id = ? and l.released_at is null", partner)).isEqualTo(1);
    }

    @Test
    void commissionFollowsMoneyReceivedNotTheInvoicePaidStatus() throws Exception {
        UUID partner = partner(true, true, true);
        UUID invoice = invoice(partner, "1000.00", "1180.00", "10", today.minusDays(3));
        // ₹0.40 short: the invoice reads as paid within the ₹0.50 tolerance, but only money received earns commission.
        receipt(invoice, "1179.60");
        BigDecimal net = jdbc.queryForObject("select net_amount from payments where invoice_id = ?", BigDecimal.class,
                invoice);
        assertThat(net).isEqualByComparingTo("999.66");
        assertThat(decimal(preview(partner), "$.data.commissionTotal")).isEqualByComparingTo("99.97");
    }

    @Test
    void anInvoiceWithCommissionInAnUnpaidPayoutCantBeCancelled() throws Exception {
        UUID partner = partner(true, true, true);
        UUID invoice = invoice(partner, "10000.00", "11800.00", "8", today.minusDays(3));
        UUID receipt = receipt(invoice, "5900");
        UUID payout = id(create(sa, partner, UUID.randomUUID(), null).andReturn().getResponse().getContentAsString());
        reverse(receipt, "Recorded against the wrong invoice").andExpect(status().isOk());
        cancelInvoice(invoice).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVOICE_IN_PAYOUT"));
        action(sa, payout, "cancel", Map.of("reason", "Only receipt reversed")).andExpect(status().isOk());
        cancelInvoice(invoice).andExpect(status().isOk());
    }

    @Test
    void aPaidReceiptIsDeductedOnlyOnce() throws Exception {
        confirmTds("10", null, "100000.00", PayoutRules.fyStart(today));
        UUID partner = partner(true, true, true);
        UUID receipt = receipt(invoice(partner, "10000.00", "11800.00", "8", today.minusDays(9)), "5900");
        payThrough(partner);
        reverse(receipt, "Customer payment recalled").andExpect(status().isOk());
        reverse(receipt, "Again").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_ALREADY_REVERSED"));
        assertThat(count("select count(*) from partner_adjustments where partner_id = ?", partner)).isEqualTo(1);
        // The database refuses a second deduction for the same paid line whatever the code does.
        Map<String, Object> first = jdbc.queryForMap("select source_line_id, source_payment_id, created_by "
                + "from partner_adjustments where partner_id = ?", partner);
        assertThatThrownBy(() -> jdbc.update("insert into partner_adjustments (adjustment_number, partner_id, kind, "
                        + "amount, reason, status, source_payment_id, source_line_id, decided_at, created_by) values "
                        + "('ADJ-T-DUP', ?, 'REVERSAL', -400, 'duplicate', 'APPROVED', ?, ?, now(), ?)",
                partner, receipt, first.get("source_line_id"), first.get("created_by")))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ux_adjustments_source_line");
    }

    // ------------------------------------------------------------------------------------------------ adjustments

    @Test
    void manualAdjustmentsNeedAReasonAndASecondPerson() throws Exception {
        UUID partner = partner(true, true, true);
        adjustment(sa, partner, "250.00", " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("reason"));
        adjustment(sa, partner, "0", "x").andExpect(status().isBadRequest());
        adjustment(admin, partner, "250.00", "Missed referral").andExpect(status().isForbidden());
        String created = adjustment(sa, partner, "250.00", "Missed referral bonus").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = id(created);
        assertThat((String) JsonPath.read(created, "$.data.adjustmentNumber")).matches("ADJ-\\d{4}-\\d{6}");
        decide(sa, id, "approve", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SELF_APPROVAL"));
        decide(sa2, id, "reject", null).andExpect(status().isBadRequest());
        // Pending adjustments are not paid out.
        assertThat((List<?>) JsonPath.read(preview(partner), "$.data.lines")).isEmpty();
        decide(sa2, id, "approve", "Agreed with sales").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        decide(sa2, id, "reject", "Too late").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADJUSTMENT_DECIDED"));
        String preview = preview(partner);
        assertThat(decimal(preview, "$.data.adjustmentsTotal")).isEqualByComparingTo("250.00");
        assertThat((Boolean) JsonPath.read(preview, "$.data.canCreate")).isTrue();
        assertThat((List<String>) JsonPath.read(jdbc.queryForObject("select json_agg(action)::text from (select action "
                + "from audit_logs where entity_id = ? order by created_at) a", String.class, id), "$[*]"))
                .containsExactly("PARTNER_ADJUSTMENT_CREATED", "PARTNER_ADJUSTMENT_APPROVED");
    }

    // ------------------------------------------------------------------------------------------------ data

    @Test
    void bankDetailsAreMaskedAndNeverExportedOrAudited() throws Exception {
        confirmTds("10", null, "100000.00", PayoutRules.fyStart(today));
        UUID partner = partner(true, true, true);
        receipt(invoice(partner, "10000.00", "11800.00", "8", today.minusDays(3)), "5900");
        UUID payout = id(payThrough(partner));
        String account = jdbc.queryForObject("select bank_account from partners where id = ?", String.class, partner);
        String pan = jdbc.queryForObject("select pan from partners where id = ?", String.class, partner);

        Map<String, Object> exporter = module("PAYOUTS", false);
        exporter.put("export", true);
        Session reader = login(user(List.of(exporter), List.of()));
        mvc.perform(get("/api/v1/payouts/{id}", payout).with(bearer(reader)))
                .andExpect(jsonPath("$.data.partner.bankAccount").value("••9012"))
                .andExpect(jsonPath("$.data.partner.pan").value("••" + pan.substring(6)))
                .andExpect(jsonPath("$.data.partner.sensitiveVisible").value(false));
        mvc.perform(get("/api/v1/payouts/{id}", payout).with(bearer(sa)))
                .andExpect(jsonPath("$.data.partner.bankAccount").value(account));
        String csv = mvc.perform(get("/api/v1/payouts/export").with(bearer(reader))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("payout,partner").contains("UTR" + tag).doesNotContain(account).doesNotContain(pan);
        mvc.perform(get("/api/v1/payouts/export").with(bearer(admin))).andExpect(status().isForbidden());
        String audit = jdbc.queryForObject("select string_agg(coalesce(old_values::text, '') || new_values::text, ' ') "
                + "from audit_logs where entity_id = ?", String.class, payout);
        assertThat(audit).doesNotContain(account).doesNotContain(pan).contains("••9012").doesNotContain("UTR" + tag);

        String history = mvc.perform(get("/api/v1/payouts/{id}/history", payout).with(bearer(reader)))
                .andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(history, "$.data[*].action")).containsExactly("PAYOUT_PAID",
                "PAYOUT_PROCESSING", "PAYOUT_APPROVED", "PAYOUT_SUBMITTED", "PAYOUT_CREATED");
        mvc.perform(get("/api/v1/payouts/dashboard").with(bearer(reader)))
                .andExpect(jsonPath("$.data.tdsConfigured").value(true))
                .andExpect(jsonPath("$.data.byStatus[?(@.status == 'PAID')].count").value(1));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Removes payout data and TDS settings from this test database (guards are disabled for the cleanup only). */
    private void clearPayouts() {
        tx.executeWithoutResult(s -> {
            for (String table : List.of("payouts", "payout_lines", "partner_adjustments", "payout_tds_calculations",
                    "tds_configurations")) {
                jdbc.execute("ALTER TABLE " + table + " DISABLE TRIGGER USER");
            }
            jdbc.update("delete from payout_tds_calculations");
            jdbc.update("delete from payout_lines where adjustment_id is not null");
            jdbc.update("delete from partner_adjustments");
            jdbc.update("delete from payout_lines");
            jdbc.update("delete from payouts");
            jdbc.update("delete from tds_configurations");
            for (String table : List.of("payouts", "payout_lines", "partner_adjustments", "payout_tds_calculations",
                    "tds_configurations")) {
                jdbc.execute("ALTER TABLE " + table + " ENABLE TRIGGER USER");
            }
        });
    }

    private UUID partner(boolean pan, boolean bank, boolean verified) throws Exception {
        counter++;
        Map<String, Object> body = new HashMap<>(Map.of("name", "Payout partner " + tag + " " + counter,
                "category", "INTERIOR_DESIGNER"));
        if (pan) {
            body.put("pan", "ABCP" + (char) ('A' + counter) + tag.substring(5) + "F");
        }
        if (bank) {
            body.put("bankName", "Test Bank");
            body.put("bankAccount", "12345678" + tag.substring(5) + counter + "9012");
            body.put("ifsc", "HDFC0001234");
        }
        UUID id = id(content(post("/api/v1/partners"), sa, body));
        if (verified) {
            verifyPan(id);
        }
        return id;
    }

    private void verifyPan(UUID partner) throws Exception {
        long version = count("select version from partners where id = ?", partner);
        content(post("/api/v1/partners/{id}/pan-verification", partner), sa,
                Map.of("verified", true, "note", "Checked on the income tax portal (test)", "version", version));
    }

    /** An issued invoice inserted directly with its fee % (the server sets it from the lead in real use). */
    private UUID invoice(UUID partner, String taxable, String total, String feePercent, LocalDate date) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into invoices (id, invoice_number, partner_id, invoice_date, due_date, status, issued_at, "
                        + "tax_type, taxable_amount, grand_total, fee_percent) values (?, ?, ?, ?, ?, 'ISSUED', now(), "
                        + "'INTRA', ?, ?, ?)",
                id, "TPO-" + id.toString().substring(0, 12), partner, date, date.plusDays(15), new BigDecimal(taxable),
                new BigDecimal(total), new BigDecimal(feePercent));
        return id;
    }

    private Map<String, Object> payment(UUID invoice, String amount) {
        Map<String, Object> map = new HashMap<>();
        map.put("invoiceId", invoice);
        map.put("paymentDate", today.toString());
        map.put("amount", amount);
        map.put("method", "UPI");
        map.put("reference", "UPI " + tag + "x" + (++references));
        map.put("idempotencyKey", UUID.randomUUID());
        map.put("confirmDuplicate", true);
        return map;
    }

    private UUID receipt(UUID invoice, String amount) throws Exception {
        return id(content(post("/api/v1/payments"), sa, payment(invoice, amount)));
    }

    private ResultActions cancelInvoice(UUID invoice) throws Exception {
        long version = count("select version from invoices where id = ?", invoice);
        return mvc.perform(withJson(post("/api/v1/invoices/{id}/cancel", invoice).with(bearer(sa)),
                body(Map.of("version", version, "reason", "Raised in error (test)"))));
    }

    private ResultActions reverse(UUID payment, String reason) throws Exception {
        return mvc.perform(withJson(post("/api/v1/payments/{id}/reverse", payment).with(bearer(sa)),
                body(Map.of("reason", reason))));
    }

    private void confirmTds(String rate, String rateWithoutPan, String threshold, LocalDate effectiveFrom)
            throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("sectionCode", "TEST", "description", "Test values only",
                "ratePercent", rate, "fyThreshold", threshold, "effectiveFrom", effectiveFrom.toString()));
        if (rateWithoutPan != null) {
            body.put("rateWithoutPanPercent", rateWithoutPan);
        }
        String draft = tds(sa, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        confirm(sa2, JsonPath.read(draft, "$.data.draft.version"), "Test configuration confirmed")
                .andExpect(status().isOk());
    }

    private ResultActions tds(Session session, Map<String, Object> body) throws Exception {
        return mvc.perform(withJson(put("/api/v1/settings/tds").with(bearer(session)), body(body)));
    }

    private ResultActions confirm(Session session, Object version, String note) throws Exception {
        return mvc.perform(withJson(post("/api/v1/settings/tds/confirm").with(bearer(session)),
                body(Map.of("version", version, "note", note))));
    }

    private String preview(UUID partner) throws Exception {
        return content(post("/api/v1/payouts/preview"), sa, Map.of("partnerId", partner));
    }

    private ResultActions create(Session session, UUID partner, UUID key, String notes) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("partnerId", partner, "idempotencyKey", key));
        if (notes != null) {
            body.put("notes", notes);
        }
        return mvc.perform(withJson(post("/api/v1/payouts").with(bearer(session)), body(body)));
    }

    private ResultActions action(Session session, UUID payout, String path, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(extra);
        body.put("version", version(payout));
        return mvc.perform(withJson(post("/api/v1/payouts/{id}/" + path, payout).with(bearer(session)), body(body)));
    }

    private ResultActions markPaid(Session session, UUID payout, LocalDate date, String reference, Boolean acknowledge)
            throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("paidDate", date.toString(), "reference", reference));
        if (acknowledge != null) {
            body.put("acknowledgeReversedSources", acknowledge);
        }
        return action(session, payout, "mark-paid", body);
    }

    /** Generates, submits (sa), approves (sa2), processes and pays (sa2) the partner's next payout. */
    private String payThrough(UUID partner) throws Exception {
        UUID payout = id(create(sa, partner, UUID.randomUUID(), null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        action(sa, payout, "submit", Map.of()).andExpect(status().isOk());
        action(sa2, payout, "approve", Map.of()).andExpect(status().isOk());
        action(sa2, payout, "processing", Map.of()).andExpect(status().isOk());
        return markPaid(sa2, payout, today, "UTR" + tag + counter++, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions adjustment(Session session, UUID partner, String amount, String reason) throws Exception {
        return mvc.perform(withJson(post("/api/v1/payouts/adjustments").with(bearer(session)), body(Map.of(
                "partnerId", partner, "amount", amount, "reason", reason, "idempotencyKey", UUID.randomUUID()))));
    }

    private ResultActions decide(Session session, UUID adjustment, String path, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (note != null) {
            body.put("note", note);
        }
        return mvc.perform(withJson(post("/api/v1/payouts/adjustments/{id}/" + path, adjustment).with(bearer(session)),
                body(body)));
    }

    private long version(UUID payout) {
        return count("select version from payouts where id = ?", payout);
    }

    private String payoutRow(UUID id) {
        return jdbc.queryForObject("select row_to_json(p)::text from payouts p where id = ?", String.class, id);
    }

    private BigDecimal sum(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, args);
    }

    private <T> List<T> concurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<T>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(task);
            }
            List<T> results = new ArrayList<>();
            for (Future<T> f : pool.invokeAll(tasks)) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private String content(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                           Session session, Map<String, Object> body) throws Exception {
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
