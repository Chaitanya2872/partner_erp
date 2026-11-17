package com.iotiq.partnererp.integration.analytics;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 12 dashboard and analytics over HTTP: KPI definitions against a ledger built for a past period (collections
 * from standing receipts, reversals excluded, the ₹0.50 tolerance never counted as collected, commission at the
 * invoice's rate snapshot), lead closures through reopening, estimate conversion, product snapshots without B2C data,
 * period boundaries in Indian time, empty periods, alerts, and access by module permission and data scope.
 *
 * <p>Each test writes into its own past year, so its period figures are exact although the database is shared with
 * other tests; figures "as of today" (receivables) are checked as differences.
 */
@SuppressWarnings("unchecked")
class DashboardApiIT extends ApiIntegrationTest {

    private TestUser saUser;
    private Session sa;
    private AnalyticsFixtures fx;
    private String tag;
    private int counter;

    @BeforeEach
    void setUp() throws Exception {
        saUser = createUser(RoleCode.SUPER_ADMIN);
        sa = login(saUser);
        fx = new AnalyticsFixtures(jdbc);
        tag = UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void cleanUp() {
        deleteTestProducts("TANP-%");
        // Payout rows are append-only in production; the guards are off for this cleanup only.
        tx.executeWithoutResult(s -> {
            jdbc.execute("ALTER TABLE payouts DISABLE TRIGGER USER");
            jdbc.execute("ALTER TABLE partner_adjustments DISABLE TRIGGER USER");
            jdbc.update("delete from partner_adjustments where reason like 'Analytics dashboard%'");
            jdbc.update("delete from payouts where notes = 'Analytics dashboard test'");
            jdbc.execute("ALTER TABLE payouts ENABLE TRIGGER USER");
            jdbc.execute("ALTER TABLE partner_adjustments ENABLE TRIGGER USER");
        });
    }

    // ------------------------------------------------------------------------------------------------ executive KPIs

    @Test
    void executiveKpisFollowThePaymentLedgerAndInvoiceSnapshots() throws Exception {
        String before = overview(sa, "2003-05-01", "2003-05-31");

        UUID p = partner("Ledger", null);
        UUID q = partner("Earlier", null);
        fx.partnerCreatedAt(p, LocalDateTime.of(2003, 5, 2, 10, 0));
        fx.partnerCreatedAt(q, LocalDateTime.of(2003, 4, 10, 10, 0));
        LocalDate may = LocalDate.of(2003, 5, 1);
        UUID inv1 = fx.issued(p, may.plusDays(4), may.plusDays(19), "10000.00", "11800.00", "8");
        UUID inv2 = fx.issued(p, may.plusDays(5), may.plusDays(20), "1000.00", "1180.00", "10");
        fx.cancelled(p, may.plusDays(6), "5000.00", "5900.00");
        UUID inv4 = fx.issued(p, may.plusDays(7), may.plusDays(22), "847.46", "1000.00", "0");
        fx.issued(p, LocalDate.of(2003, 4, 15), LocalDate.of(2003, 4, 30), "2000.00", "2360.00", "8");
        fx.receipt(inv1, may.plusDays(9), "5900.00", "5000.00", "UPI");
        fx.receipt(inv1, may.plusDays(11), "5900.00", "5000.00", "NEFT_RTGS");
        UUID bounced = fx.receipt(inv2, may.plusDays(14), "1180.00", "1000.00", "UPI");
        fx.reversal(bounced, may.plusDays(15));
        fx.receipt(inv2, may.plusDays(19), "590.00", "500.00", "CASH");
        // ₹0.30 short: the invoice shows as Paid (BR-V6) but only ₹999.70 was collected.
        fx.receipt(inv4, may.plusDays(24), "999.70", "847.21", "UPI");
        // Commission earned is frozen on the invoice, so a later tier change changes nothing.
        jdbc.update("update partners set tier_id = (select id from partner_tiers where tier_level = 4) where id = ?", p);

        String body = overview(sa, "2003-05-01", "2003-05-31");
        assertThat((String) JsonPath.read(body, "$.data.period.preset")).isEqualTo("CUSTOM");
        assertThat((String) JsonPath.read(body, "$.data.period.timezone")).isEqualTo("Asia/Kolkata");
        assertThat((Integer) JsonPath.read(body, "$.data.partners.newInPeriod")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(body, "$.data.partners.newPrevious")).isEqualTo(1);

        assertThat((Integer) JsonPath.read(body, "$.data.invoices.issued")).isEqualTo(3);
        assertThat(decimal(body, "$.data.invoices.gross")).isEqualByComparingTo("13980.00");
        assertThat(decimal(body, "$.data.invoices.taxable")).isEqualByComparingTo("11847.46");
        assertThat(decimal(body, "$.data.invoices.gst")).isEqualByComparingTo("2132.54");
        assertThat(decimal(body, "$.data.invoices.taxablePrevious")).isEqualByComparingTo("2000.00");
        assertThat((Integer) JsonPath.read(body, "$.data.invoices.cancelled")).isEqualTo(1);
        assertThat(decimal(body, "$.data.invoices.cancelledValue")).isEqualByComparingTo("5900.00");

        assertThat((Integer) JsonPath.read(body, "$.data.payments.receipts")).isEqualTo(4);
        assertThat(decimal(body, "$.data.payments.collected")).isEqualByComparingTo("13389.70");
        assertThat(decimal(body, "$.data.payments.collectedExGst")).isEqualByComparingTo("11347.21");
        assertThat(decimal(body, "$.data.payments.gstCollected")).isEqualByComparingTo("2042.49");
        assertThat(decimal(body, "$.data.payments.collectedExGstPrevious")).isEqualByComparingTo("0.00");
        assertThat((Integer) JsonPath.read(body, "$.data.payments.reversals")).isEqualTo(1);
        assertThat(decimal(body, "$.data.payments.reversedAmount")).isEqualByComparingTo("1180.00");
        assertThat((List<String>) JsonPath.read(body, "$.data.paymentMethods[*].key"))
                .containsExactly("UPI", "NEFT_RTGS", "CASH");
        assertThat(decimal(body, "$.data.paymentMethods[0].amount")).isEqualByComparingTo("6899.70");
        assertThat((Integer) JsonPath.read(body, "$.data.paymentMethods[0].count")).isEqualTo(2);

        // 8% of ₹5,000 twice and 10% of ₹500; the reversed receipt and the 0% invoice earn nothing.
        assertThat(decimal(body, "$.data.payouts.commissionEarned")).isEqualByComparingTo("850.00");

        // Today's receivables gained inv2 (₹590 left, partly paid) and the April invoice (₹2,360), both overdue;
        // inv4's ₹0.30 is a tolerance residual, not a receivable and not a collection.
        assertThat(decimal(body, "$.data.invoices.outstanding").subtract(decimal(before, "$.data.invoices.outstanding")))
                .isEqualByComparingTo("2950.00");
        assertThat(delta(body, before, "$.data.invoices.overdueCount")).isEqualTo(2);
        assertThat(decimal(body, "$.data.invoices.overdue").subtract(decimal(before, "$.data.invoices.overdue")))
                .isEqualByComparingTo("2950.00");
        assertThat(delta(body, before, "$.data.invoices.toleranceCount")).isEqualTo(1);
        assertThat(decimal(body, "$.data.invoices.toleranceResidual")
                .subtract(decimal(before, "$.data.invoices.toleranceResidual"))).isEqualByComparingTo("0.30");
        assertThat(delta(body, before, "$.data.payments.partlyPaidInvoices")).isEqualTo(1);

        // The only partner with collections in the period has all of it.
        assertThat((String) JsonPath.read(body, "$.data.topPartners[0].partnerId")).isEqualTo(p.toString());
        assertThat(decimal(body, "$.data.topPartners[0].collectedExGst")).isEqualByComparingTo("11347.21");
        assertThat(decimal(body, "$.data.topPartners[0].share")).isEqualByComparingTo("100.00");
        assertThat(decimal(body, "$.data.topPartners[0].commission")).isEqualByComparingTo("850.00");
        assertThat(decimal(body, "$.data.topPartners[0].invoicedTaxable")).isEqualByComparingTo("11847.46");

        // A 31-day period is bucketed by day; the reversed receipt's day shows nothing collected.
        assertThat((String) JsonPath.read(body, "$.data.revenueTrend.granularity")).isEqualTo("DAY");
        assertThat((List<?>) JsonPath.read(body, "$.data.revenueTrend.buckets")).hasSize(31);
        List<Double> collected = JsonPath.read(body, "$.data.revenueTrend.series[?(@.key == 'collectedExGst')].values[*]");
        assertThat(collected.stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(11347.21, org.assertj.core.data.Offset.offset(0.001));
        assertThat(collected.get(14)).isZero();
        assertThat(collected.get(9)).isEqualTo(5000.0);

        // The finance view agrees and adds collection efficiency: collected gross ÷ invoiced gross.
        String finance = content(get("/api/v1/dashboard/finance").param("from", "2003-05-01").param("to", "2003-05-31"), sa);
        assertThat(decimal(finance, "$.data.collections.collectedExGst")).isEqualByComparingTo("11347.21");
        assertThat(decimal(finance, "$.data.collectionEfficiency")).isEqualByComparingTo("95.78");
    }

    @Test
    void periodBoundariesUseIndianMidnights() throws Exception {
        UUID p = partner("Boundary", null);
        fx.partnerCreatedAt(p, LocalDateTime.of(2002, 3, 31, 23, 59));
        UUID late = partner("Boundary late", null);
        fx.partnerCreatedAt(late, LocalDateTime.of(2002, 4, 1, 0, 0));
        UUID inv = fx.issued(p, LocalDate.of(2002, 3, 31), LocalDate.of(2002, 4, 15), "100.00", "118.00", "0");
        fx.receipt(inv, LocalDate.of(2002, 3, 31), "118.00", "100.00", "UPI");

        String march = overview(sa, "2002-03-01", "2002-03-31");
        assertThat((Integer) JsonPath.read(march, "$.data.partners.newInPeriod")).isEqualTo(1);
        assertThat(decimal(march, "$.data.payments.collected")).isEqualByComparingTo("118.00");
        String april = overview(sa, "2002-04-01", "2002-04-30");
        assertThat((Integer) JsonPath.read(april, "$.data.partners.newInPeriod")).isEqualTo(1);
        assertThat(decimal(april, "$.data.payments.collected")).isEqualByComparingTo("0.00");
        assertThat((Integer) JsonPath.read(april, "$.data.invoices.issued")).isZero();
    }

    // ------------------------------------------------------------------------------------------------ sales

    @Test
    void wonAndLostCountEachLeadOnceThroughReopening() throws Exception {
        UUID p = partner("Pipeline", null);
        LocalDateTime june = LocalDateTime.of(2004, 6, 1, 10, 0);
        // Won, reopened, won again: one win, dated at the latest move into Won.
        UUID l1 = fx.lead(p, june.plusDays(1), "WON", "500000");
        fx.history(l1, "STAGE_CHANGED", "NEW", "WON", june.plusDays(4));
        fx.history(l1, "STAGE_CHANGED", "WON", "NEGOTIATION", june.plusDays(6));
        fx.history(l1, "STAGE_CHANGED", "NEGOTIATION", "WON", june.plusDays(9));
        // Lost, then reopened and still open: no loss.
        UUID l2 = fx.lead(p, june.plusDays(2), "NEW", "200000");
        fx.history(l2, "STAGE_CHANGED", "NEW", "LOST", june.plusDays(3));
        fx.history(l2, "STAGE_CHANGED", "LOST", "NEW", june.plusDays(5));
        UUID l3 = fx.lead(p, june.plusDays(2), "LOST", null);
        fx.history(l3, "STAGE_CHANGED", "NEW", "LOST", june.plusDays(7));
        // Created in May, won in June: counts as a June win but not as a June lead.
        UUID l4 = fx.lead(p, LocalDateTime.of(2004, 5, 20, 10, 0), "WON", "100000");
        fx.history(l4, "STAGE_CHANGED", "NEW", "WON", june.plusDays(8));
        // Last minute of 30 June in India counts; the first minute of 1 July doesn't.
        fx.lead(p, LocalDateTime.of(2004, 6, 30, 23, 59), "NEW", null);
        fx.lead(p, LocalDateTime.of(2004, 7, 1, 0, 0), "NEW", null);
        // Deleted leads never count.
        UUID deleted = fx.lead(p, june.plusDays(3), "WON", null);
        jdbc.update("update leads set deleted_at = now(), delete_reason = 'Removed by the test' where id = ?", deleted);
        fx.estimate(p, l1, LocalDate.of(2004, 6, 3), "SENT", "1180.00");

        String body = content(get("/api/v1/dashboard/sales").param("from", "2004-06-01").param("to", "2004-06-30"), sa);
        assertThat((Integer) JsonPath.read(body, "$.data.kpis.newInPeriod")).isEqualTo(4);
        assertThat((Integer) JsonPath.read(body, "$.data.kpis.wonInPeriod")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(body, "$.data.kpis.lostInPeriod")).isEqualTo(1);
        assertThat(decimal(body, "$.data.kpis.winRate")).isEqualByComparingTo("66.67");
        assertThat((Integer) JsonPath.read(body, "$.data.conversion.leadsCreated")).isEqualTo(4);
        assertThat((Integer) JsonPath.read(body, "$.data.conversion.leadsWithEstimate")).isEqualTo(1);
        assertThat(decimal(body, "$.data.conversion.leadToEstimateRate")).isEqualByComparingTo("25.00");

        List<Integer> won = JsonPath.read(body, "$.data.trend.series[?(@.key == 'won')].values[*]");
        assertThat(won.get(9)).isEqualTo(1);    // 10 June: l1's latest win
        assertThat(won.get(8)).isEqualTo(1);    // 9 June: l4
        assertThat(won.get(4)).isZero();        // 5 June: l1's first win was superseded
        List<Integer> lost = JsonPath.read(body, "$.data.trend.series[?(@.key == 'lost')].values[*]");
        assertThat(lost.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);

        Map<String, Object> row = partnerRow(body, p);
        assertThat(row.get("created")).isEqualTo(4);
        assertThat(row.get("won")).isEqualTo(2);
        assertThat(row.get("lost")).isEqualTo(1);
        assertThat(row.get("open")).isEqualTo(3);
        assertThat(new BigDecimal(row.get("pipeline").toString())).isEqualByComparingTo("200000.00");
        assertThat((List<String>) JsonPath.read(body, "$.data.funnel[*].key"))
                .containsExactly("NEW", "REGISTERED", "PRESALES", "SITE_VISIT", "ESTIMATE_SENT", "NEGOTIATION", "WON", "LOST");
        assertThat((List<String>) JsonPath.read(body, "$.data.aging[*].key"))
                .containsExactly("D0_7", "D8_30", "D31_60", "D61_90", "D90_PLUS");
    }

    @Test
    void estimateConversionCountsLiveIssuedInvoicesOnly() throws Exception {
        UUID p = partner("Estimates", null);
        LocalDate july = LocalDate.of(2005, 7, 1);
        fx.estimate(p, null, july.plusDays(1), "SENT", "1000.00");
        fx.estimate(p, null, july.plusDays(2), "DRAFT", "9999.00");
        UUID invoiced = fx.estimate(p, null, july.plusDays(3), "INVOICED", "2000.00");
        fx.invoiceFrom(invoiced, p, july.plusDays(4));
        fx.estimate(p, null, july.plusDays(5), "ACCEPTED", "3000.00");
        UUID deleted = fx.estimate(p, null, july.plusDays(6), "SENT", "500.00");
        jdbc.update("update estimates set deleted_at = now(), delete_reason = 'Removed by the test' where id = ?", deleted);

        String body = overview(sa, "2005-07-01", "2005-07-31");
        assertThat((Integer) JsonPath.read(body, "$.data.estimates.total")).isEqualTo(4);
        assertThat((Integer) JsonPath.read(body, "$.data.estimates.draft")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(body, "$.data.estimates.sent")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(body, "$.data.estimates.accepted")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(body, "$.data.estimates.invoiced")).isEqualTo(1);
        assertThat(decimal(body, "$.data.estimates.value")).isEqualByComparingTo("6000.00");
        assertThat((Integer) JsonPath.read(body, "$.data.estimates.converted")).isEqualTo(1);
        assertThat(decimal(body, "$.data.estimates.invoiceConversionRate")).isEqualByComparingTo("33.33");
    }

    // ------------------------------------------------------------------------------------------------ payouts

    @Test
    void payoutFiguresSeparatePaidTdsLiabilitiesAndNegativeAdjustments() throws Exception {
        String before = overview(sa, "2011-06-01", "2011-06-30");
        UUID paid = partner("Paid", null);
        UUID approved = partner("Approved", null);
        UUID pending = partner("Pending", null);
        payout(paid, "PAID", "1000.00", "100.00", LocalDate.of(2011, 6, 15));
        payout(approved, "APPROVED", "500.00", "50.00", null);
        payout(pending, "PENDING_APPROVAL", "300.00", "0.00", null);
        adjustment(paid, "-250.00", "APPROVED");
        adjustment(paid, "75.00", "PENDING_APPROVAL");

        String body = overview(sa, "2011-06-01", "2011-06-30");
        // Paid in the period: gross commission, TDS deducted and net transferred.
        assertThat((Integer) JsonPath.read(body, "$.data.payouts.paidCount")).isEqualTo(1);
        assertThat(decimal(body, "$.data.payouts.paidCommission")).isEqualByComparingTo("1000.00");
        assertThat(decimal(body, "$.data.payouts.paidTds")).isEqualByComparingTo("100.00");
        assertThat(decimal(body, "$.data.payouts.paidNet")).isEqualByComparingTo("900.00");
        // Liabilities today: approved (or processing) and still in approval, at gross commission.
        assertThat(decimal(body, "$.data.payouts.approvedLiability")
                .subtract(decimal(before, "$.data.payouts.approvedLiability"))).isEqualByComparingTo("500.00");
        assertThat(decimal(body, "$.data.payouts.pendingLiability")
                .subtract(decimal(before, "$.data.payouts.pendingLiability"))).isEqualByComparingTo("300.00");
        // An approved negative adjustment waits to be recovered; a pending one isn't counted as recoverable.
        assertThat(delta(body, before, "$.data.payouts.negativeAdjustments")).isEqualTo(1);
        assertThat(decimal(body, "$.data.payouts.negativeAdjustmentBalance")
                .subtract(decimal(before, "$.data.payouts.negativeAdjustmentBalance"))).isEqualByComparingTo("-250.00");
        assertThat(delta(body, before, "$.data.payouts.adjustmentsAwaitingApproval")).isEqualTo(1);
        List<Integer> paidCounts = JsonPath.read(body, "$.data.payouts.byStatus[?(@.status == 'PAID')].count");
        List<Integer> paidBefore = JsonPath.read(before, "$.data.payouts.byStatus[?(@.status == 'PAID')].count");
        assertThat(paidCounts.get(0) - paidBefore.get(0)).isEqualTo(1);

        String partners = content(get("/api/v1/dashboard/partners").param("from", "2011-06-01").param("to", "2011-06-30"), sa);
        List<Double> carried = JsonPath.read(partners, "$.data.negativeBalances[?(@.partnerId == '" + paid + "')].carriedAdjustments");
        assertThat(carried).containsExactly(-250.0);
    }

    // ------------------------------------------------------------------------------------------------ products

    @Test
    void productAnalyticsUseInvoiceLineSnapshotsAndNeverB2cData() throws Exception {
        UUID p = partner("Products", null);
        UUID product = UUID.randomUUID();
        String sku = "TANP-" + tag;
        jdbc.update("insert into products (id, sku, category, b2c_name, partner_name, b2c_price, partner_price, cost_price) "
                + "values (?, ?, 'SMART_GLIDE', 'B2C-SECRET-NAME', 'Analytics glide', 77777.77, 900.00, 33333.33)", product, sku);
        fx.issuedWithLine(p, LocalDate.of(2006, 8, 10), product, "3", "3000.00", "18", "55555.55", "B2C-SECRET-LINE");
        // The price sheet changes later; sales keep the invoiced snapshot.
        jdbc.update("update products set partner_price = 1500.00 where id = ?", product);

        String body = content(get("/api/v1/dashboard/products").param("from", "2006-08-01").param("to", "2006-08-31"), sa);
        assertThat((String) JsonPath.read(body, "$.data.topByValue[0].sku")).isEqualTo(sku);
        assertThat(decimal(body, "$.data.topByValue[0].taxable")).isEqualByComparingTo("3000.00");
        assertThat(decimal(body, "$.data.topByValue[0].quantity")).isEqualByComparingTo("3");
        assertThat(decimal(body, "$.data.topByValue[0].share")).isEqualByComparingTo("100.00");
        assertThat(decimal(body, "$.data.totalTaxable")).isEqualByComparingTo("3000.00");
        assertThat((String) JsonPath.read(body, "$.data.categoryRevenue[0].key")).isEqualTo("SMART_GLIDE");
        assertThat((Object) JsonPath.read(body, "$.data.kpis.missingB2cPrice")).isNotNull();
        assertThat(body).doesNotContain("55555.55", "77777.77", "33333.33", "B2C-SECRET");

        // Without internal B2C access even the count of products missing a B2C price is withheld.
        Session admin = login(createUser(RoleCode.ADMIN));
        String adminBody = content(get("/api/v1/dashboard/products").param("from", "2006-08-01").param("to", "2006-08-31"), admin);
        assertThat((Object) JsonPath.read(adminBody, "$.data.kpis.missingB2cPrice")).isNull();
        assertThat(adminBody).doesNotContain("55555.55", "77777.77", "B2C-SECRET");
    }

    // ------------------------------------------------------------------------------------------------ empty data

    @Test
    void anEmptyPeriodGivesZerosAndNoRates() throws Exception {
        String body = overview(sa, "2001-01-01", "2001-01-31");
        assertThat((Integer) JsonPath.read(body, "$.data.invoices.issued")).isZero();
        assertThat(decimal(body, "$.data.invoices.gross")).isEqualByComparingTo("0.00");
        assertThat(decimal(body, "$.data.payments.collected")).isEqualByComparingTo("0.00");
        assertThat((Object) JsonPath.read(body, "$.data.leads.winRate")).isNull();
        assertThat((Object) JsonPath.read(body, "$.data.estimates.invoiceConversionRate")).isNull();
        assertThat((List<?>) JsonPath.read(body, "$.data.topPartners")).isEmpty();
        assertThat((List<?>) JsonPath.read(body, "$.data.paymentMethods")).isEmpty();
        List<Double> values = JsonPath.read(body, "$.data.revenueTrend.series[0].values[*]");
        assertThat(values).hasSize(31).allMatch(v -> v == 0.0);
        String sales = content(get("/api/v1/dashboard/sales").param("from", "2001-01-01").param("to", "2001-01-31"), sa);
        assertThat((Object) JsonPath.read(sales, "$.data.conversion.leadToEstimateRate")).isNull();
        String products = content(get("/api/v1/dashboard/products").param("from", "2001-01-01").param("to", "2001-01-31"), sa);
        assertThat((List<?>) JsonPath.read(products, "$.data.topByValue")).isEmpty();
    }

    @Test
    void invalidPeriodsAreRefused() throws Exception {
        mvc.perform(get("/api/v1/dashboard/overview").param("from", "2003-02-01").param("to", "2003-01-01").with(bearer(sa)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors[0].field").value("to"));
        mvc.perform(get("/api/v1/dashboard/overview").param("from", "2010-01-01").param("to", "2016-01-01").with(bearer(sa)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/dashboard/overview").param("from", "2010-01-01").with(bearer(sa)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/dashboard/overview").param("preset", "NEXT_YEAR").with(bearer(sa)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/dashboard/overview").param("preset", "THIS_FY").with(bearer(sa)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.period.preset").value("THIS_FY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    // ------------------------------------------------------------------------------------------------ access

    @Test
    void sectionsNeedTheirModuleWithTheAllScope() throws Exception {
        // Dashboard alone: the page opens but shows no business figure.
        Session bare = login(user(List.of(module("DASHBOARD", null))));
        String body = overview(bare, "2003-05-01", "2003-05-31");
        for (String section : List.of("partners", "leads", "estimates", "invoices", "payments", "payouts", "revenueTrend",
                "topPartners", "leadFunnel")) {
            assertThat((Object) JsonPath.read(body, "$.data." + section)).as(section).isNull();
        }
        assertThat((List<String>) JsonPath.read(body, "$.data.restricted[*].module")).hasSize(7);

        // Leads with the SELF scope can't be evaluated yet (D-30): nothing organisation-wide is shown.
        Session scoped = login(user(List.of(module("DASHBOARD", null), module("LEADS", "SELF"),
                module("INVOICES", "ALL"))));
        String scopedBody = overview(scoped, "2003-05-01", "2003-05-31");
        assertThat((Object) JsonPath.read(scopedBody, "$.data.leads")).isNull();
        assertThat((Object) JsonPath.read(scopedBody, "$.data.leadFunnel")).isNull();
        assertThat((Object) JsonPath.read(scopedBody, "$.data.invoices")).isNotNull();
        assertThat((Object) JsonPath.read(scopedBody, "$.data.payments")).isNull();
        assertThat((List<String>) JsonPath.read(scopedBody, "$.data.restricted[?(@.module == 'LEADS')].reason"))
                .containsExactly("SCOPE_SELF");
        String sales = content(get("/api/v1/dashboard/sales"), scoped);
        assertThat((Object) JsonPath.read(sales, "$.data.kpis")).isNull();
        assertThat((Object) JsonPath.read(sales, "$.data.byPartner")).isNull();
        String alerts = content(get("/api/v1/dashboard/alerts"), scoped);
        assertThat((List<String>) JsonPath.read(alerts, "$.data.groups[*].module")).doesNotContain("LEADS", "PAYOUTS");
        // The partner ranking needs both partners and payments.
        assertThat((Object) JsonPath.read(scopedBody, "$.data.topPartners")).isNull();

        // Without the dashboard permission nothing opens, whatever else the user has.
        Session noDashboard = login(user(List.of(module("INVOICES", "ALL"))));
        for (String path : List.of("overview", "sales", "partners", "products", "finance", "alerts")) {
            mvc.perform(get("/api/v1/dashboard/" + path).with(bearer(noDashboard))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/dashboard/overview")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------------------------------------ alerts

    @Test
    void alertsAreDerivedFromCurrentData() throws Exception {
        UUID p = partner("Alerts", null);
        LocalDate today = LocalDate.now(AnalyticsFixtures.ZONE);
        fx.issued(p, today.minusDays(40), today.minusDays(10), "1000.00", "1180.00", "0");
        jdbc.update("update partners set status = 'ACTIVE', agreement_status = 'SENT' where id = ?", p);

        String body = content(get("/api/v1/dashboard/alerts"), sa);
        long overdue = count("""
                select count(*) from invoices i where i.status = 'ISSUED' and i.deleted_at is null and i.due_date < ?
                   and i.grand_total - coalesce((select sum(amount) from payments p where p.invoice_id = i.id), 0) > 0.50""",
                today);
        List<Integer> counts = JsonPath.read(body, "$.data.groups[?(@.code == 'INVOICES_OVERDUE')].count");
        assertThat(counts).containsExactly((int) overdue);
        assertThat((List<String>) JsonPath.read(body, "$.data.groups[?(@.code == 'INVOICES_OVERDUE')].severity"))
                .containsExactly("danger");
        List<?> items = JsonPath.read(body, "$.data.groups[?(@.code == 'INVOICES_OVERDUE')].items[*]");
        assertThat(items).hasSizeLessThanOrEqualTo(5).isNotEmpty();
        assertThat(body).contains("PARTNERS_AGREEMENT_UNSIGNED");

        long missing = count("select count(*) from products where active and (partner_price = 0 or hsn is null or btrim(hsn) = '')");
        if (missing > 0) {
            assertThat(body).contains("PRODUCTS_MISSING_DATA");
        }
        // No confirmed TDS configuration in this database: the TDS group says so even with no open payout.
        if (count("select count(*) from tds_configurations where status = 'CONFIRMED' and effective_from <= ?", today) == 0) {
            assertThat((List<String>) JsonPath.read(body, "$.data.groups[?(@.code == 'TDS_NOT_CONFIRMED')].detail"))
                    .singleElement().asString().contains("No confirmed TDS configuration");
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private String overview(Session session, String from, String to) throws Exception {
        return content(get("/api/v1/dashboard/overview").param("from", from).param("to", to), session);
    }

    private String content(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                           Session session) throws Exception {
        return mvc.perform(request.with(bearer(session))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** A non-legacy payout as the workflow leaves it (TDS calculated, approved by someone else, paid with a reference). */
    private void payout(UUID partner, String status, String commission, String tds, LocalDate paidDate) {
        boolean approvedOrLater = !"PENDING_APPROVAL".equals(status);
        jdbc.update("insert into payouts (id, payout_number, partner_id, status, as_of_date, fy_start, commission_total, "
                        + "tds_status, tds_section, tds_rate, tds_base, tds_amount, net_amount, approved_at, approved_by, "
                        + "paid_date, payment_reference, paid_at, paid_by, notes) values (?, ?, ?, ?, '2011-06-05', "
                        + "'2011-04-01', ?, 'CALCULATED', 'TEST', 10, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Analytics dashboard test')",
                UUID.randomUUID(), AnalyticsFixtures.number("TPO-"), partner, status, new BigDecimal(commission),
                new BigDecimal(commission), new BigDecimal(tds), new BigDecimal(commission).subtract(new BigDecimal(tds)),
                approvedOrLater ? AnalyticsFixtures.at(LocalDate.of(2011, 6, 6)) : null,
                approvedOrLater ? saUser.id() : null, paidDate, paidDate == null ? null : "UTR" + tag,
                paidDate == null ? null : AnalyticsFixtures.at(paidDate), paidDate == null ? null : saUser.id());
    }

    private void adjustment(UUID partner, String amount, String status) {
        boolean decided = "APPROVED".equals(status);
        jdbc.update("insert into partner_adjustments (id, adjustment_number, partner_id, kind, amount, reason, status, "
                        + "decided_at, decided_by) values (?, ?, ?, 'MANUAL', ?, 'Analytics dashboard adjustment', ?, ?, ?)",
                UUID.randomUUID(), AnalyticsFixtures.number("TAD-"), partner, new BigDecimal(amount), status,
                decided ? AnalyticsFixtures.at(LocalDate.of(2011, 6, 7)) : null, decided ? saUser.id() : null);
    }

    private UUID partner(String name, String pan) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("name", "Analytics " + name + " " + tag + " " + (++counter),
                "category", "INTERIOR_DESIGNER"));
        if (pan != null) {
            body.put("pan", pan);
        }
        String created = mvc.perform(withJson(post("/api/v1/partners").with(bearer(sa)), body(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(JsonPath.read(created, "$.data.id"));
    }

    private static Map<String, Object> partnerRow(String body, UUID partner) {
        List<Map<String, Object>> rows = JsonPath.read(body, "$.data.byPartner[?(@.partnerId == '" + partner + "')]");
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private static BigDecimal decimal(String body, String path) {
        Object value = JsonPath.read(body, path);
        return new BigDecimal(value.toString());
    }

    private static int delta(String after, String before, String path) {
        return ((Number) JsonPath.read(after, path)).intValue() - ((Number) JsonPath.read(before, path)).intValue();
    }

    private static Map<String, Object> module(String code, String scope) {
        Map<String, Object> map = new HashMap<>();
        map.put("moduleCode", code);
        map.put("read", true);
        if (scope != null) {
            map.put("dataScope", scope);
        }
        return map;
    }

    private TestUser user(List<Map<String, Object>> modules) throws Exception {
        TestUser user = createUser(RoleCode.ADMIN);
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", user.id()).with(bearer(sa)),
                        body(Map.of("modules", modules, "actions", List.of()))))
                .andExpect(status().isOk());
        return user;
    }
}
