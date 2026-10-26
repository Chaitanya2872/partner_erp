package com.iotiq.partnererp.integration.analytics;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
 * Phase 12 reports over HTTP: filters, search, sorting, pagination and totals over all matching rows; ledger rules
 * (reversals cancel out, the tolerance stays visible, commission at the frozen rate); receivables ageing; PAN masking;
 * column and report access by module permission and scope; CSV export permissions, byte-order mark and formula guard.
 */
@SuppressWarnings("unchecked")
class ReportApiIT extends ApiIntegrationTest {

    private Session sa;
    private AnalyticsFixtures fx;
    private String tag;
    private int counter;

    @BeforeEach
    void setUp() throws Exception {
        sa = login(createUser(RoleCode.SUPER_ADMIN));
        fx = new AnalyticsFixtures(jdbc);
        tag = UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void cleanUp() {
        // Payout rows written here would otherwise stay; payout tables are append-only, so guards are off for this only.
        tx.executeWithoutResult(s -> {
            jdbc.execute("ALTER TABLE payouts DISABLE TRIGGER USER");
            jdbc.execute("ALTER TABLE partner_adjustments DISABLE TRIGGER USER");
            jdbc.update("delete from partner_adjustments where reason like 'Analytics report%'");
            jdbc.update("delete from payouts where notes = 'Analytics report test'");
            jdbc.execute("ALTER TABLE payouts ENABLE TRIGGER USER");
            jdbc.execute("ALTER TABLE partner_adjustments ENABLE TRIGGER USER");
        });
    }

    @Test
    void invoiceReportFiltersSortsPagesAndTotalsAllMatchingRows() throws Exception {
        UUID p = partner("Invoices", null);
        UUID other = partner("Other", null);
        LocalDate march = LocalDate.of(2007, 3, 1);
        UUID paid = fx.issued(p, march.plusDays(1), march.plusDays(10), "1000.00", "1180.00", "8");
        fx.receipt(paid, march.plusDays(5), "1180.00", "1000.00", "UPI");
        UUID partly = fx.issued(p, march.plusDays(2), march.plusDays(10), "2000.00", "2360.00", "8");
        fx.receipt(partly, march.plusDays(6), "1000.00", "847.46", "UPI");
        UUID tolerance = fx.issued(p, march.plusDays(3), march.plusDays(10), "847.46", "1000.00", "0");
        fx.receipt(tolerance, march.plusDays(7), "999.70", "847.21", "UPI");
        fx.cancelled(p, march.plusDays(4), "500.00", "590.00");
        fx.issued(other, march.plusDays(4), march.plusDays(10), "9999.00", "11798.82", "0");

        String all = run("invoices", Map.of("from", "2007-03-01", "to", "2007-03-31", "partnerId", p.toString(),
                "size", "2"));
        assertThat((Integer) JsonPath.read(all, "$.data.totalElements")).isEqualTo(4);
        assertThat((Integer) JsonPath.read(all, "$.data.totalPages")).isEqualTo(2);
        assertThat((List<?>) JsonPath.read(all, "$.data.rows")).hasSize(2);
        // Newest invoice date first by default; totals cover all four rows, cancelled ones at zero.
        assertThat((String) JsonPath.read(all, "$.data.sort")).isEqualTo("invoice_date,desc");
        assertThat(decimal(all, "$.data.totals.total")).isEqualByComparingTo("4540.00");
        assertThat(decimal(all, "$.data.totals.received")).isEqualByComparingTo("3179.70");
        assertThat(decimal(all, "$.data.totals.balance")).isEqualByComparingTo("1360.30");
        assertThat((String) JsonPath.read(all, "$.data.rows[0].link")).startsWith("/invoices/");

        String statuses = run("invoices", Map.of("from", "2007-03-01", "to", "2007-03-31", "partnerId", p.toString(),
                "sort", "invoice_date,asc", "size", "10"));
        // ₹0.30 short shows as Paid, with the residual kept in the balance column.
        assertThat((List<String>) JsonPath.read(statuses, "$.data.rows[*].status"))
                .containsExactly("PAID", "PARTLY_PAID_OVERDUE", "PAID", "CANCELLED");
        assertThat(decimal(statuses, "$.data.rows[2].balance")).isEqualByComparingTo("0.30");

        String onlyPartly = run("invoices", Map.of("from", "2007-03-01", "to", "2007-03-31", "status", "PARTLY_PAID_OVERDUE"));
        assertThat((List<String>) JsonPath.read(onlyPartly, "$.data.rows[*].id")).containsExactly(partly.toString());

        String number = jdbc.queryForObject("select invoice_number from invoices where id = ?", String.class, paid);
        String searched = run("invoices", Map.of("from", "2007-03-01", "to", "2007-03-31", "q", number.toLowerCase()));
        assertThat((List<String>) JsonPath.read(searched, "$.data.rows[*].id")).containsExactly(paid.toString());
        // LIKE wildcards in the search are literal.
        String wildcard = run("invoices", Map.of("from", "2007-03-01", "to", "2007-03-31", "q", "%"));
        assertThat((Integer) JsonPath.read(wildcard, "$.data.totalElements")).isZero();
    }

    @Test
    void paymentReportListsReversalsThatCancelOutAndCommissionsKeepTheirRate() throws Exception {
        UUID p = partner("Ledger", null);
        LocalDate april = LocalDate.of(2007, 4, 1);
        UUID inv = fx.issued(p, april.plusDays(1), april.plusDays(20), "10000.00", "11800.00", "8");
        fx.receipt(inv, april.plusDays(2), "5900.00", "5000.00", "UPI");
        UUID bounced = fx.receipt(inv, april.plusDays(3), "1180.00", "1000.00", "CHEQUE");
        fx.reversal(bounced, april.plusDays(4));
        jdbc.update("update partners set tier_id = (select id from partner_tiers where tier_level = 4) where id = ?", p);

        Map<String, String> filters = Map.of("from", "2007-04-01", "to", "2007-04-30", "partnerId", p.toString());
        String payments = run("payments", filters);
        assertThat((Integer) JsonPath.read(payments, "$.data.totalElements")).isEqualTo(3);
        assertThat(decimal(payments, "$.data.totals.amount")).isEqualByComparingTo("5900.00");
        assertThat(decimal(payments, "$.data.totals.amount_ex_gst")).isEqualByComparingTo("5000.00");
        assertThat((List<String>) JsonPath.read(payments, "$.data.rows[*].state"))
                .containsExactlyInAnyOrder("STANDING", "REVERSED", "REVERSAL");
        String standing = run("payments", Map.of("from", "2007-04-01", "to", "2007-04-30", "partnerId", p.toString(),
                "status", "STANDING"));
        assertThat((Integer) JsonPath.read(standing, "$.data.totalElements")).isEqualTo(1);

        String commissions = run("commissions", filters);
        assertThat((Integer) JsonPath.read(commissions, "$.data.totalElements")).isEqualTo(1);
        assertThat(decimal(commissions, "$.data.rows[0].commission_percent")).isEqualByComparingTo("8");
        assertThat(decimal(commissions, "$.data.totals.commission")).isEqualByComparingTo("400.00");
        assertThat((String) JsonPath.read(commissions, "$.data.rows[0].state")).isEqualTo("UNBILLED");
    }

    @Test
    void receivablesAreAgedByDaysPastDue() throws Exception {
        UUID p = partner("Ageing", null);
        LocalDate today = LocalDate.now(AnalyticsFixtures.ZONE);
        LocalDate issued = LocalDate.of(2008, 1, 10);
        Map<String, Integer> dueInDays = Map.of("NOT_DUE", 5, "D1_30", -10, "D31_60", -45, "D61_90", -75, "D90_PLUS", -120);
        dueInDays.forEach((bucket, days) -> fx.issued(p, issued, today.plusDays(days), "100.00", "118.00", "0"));
        UUID settled = fx.issued(p, issued, today.minusDays(10), "100.00", "118.00", "0");
        fx.receipt(settled, issued.plusDays(1), "117.60", "99.66", "UPI");

        String body = run("receivables", Map.of("from", "2008-01-01", "to", "2008-01-31", "partnerId", p.toString(),
                "size", "50"));
        assertThat((Integer) JsonPath.read(body, "$.data.totalElements")).isEqualTo(5);
        assertThat((List<String>) JsonPath.read(body, "$.data.rows[*].bucket"))
                .containsExactly("D90_PLUS", "D61_90", "D31_60", "D1_30", "NOT_DUE");
        assertThat(decimal(body, "$.data.totals.balance")).isEqualByComparingTo("590.00");
        String old = run("receivables", Map.of("from", "2008-01-01", "to", "2008-01-31", "partnerId", p.toString(),
                "status", "D90_PLUS"));
        assertThat((Integer) JsonPath.read(old, "$.data.rows[0].days_past_due")).isEqualTo(120);
    }

    @Test
    void panIsMaskedEverywhereAndSensitiveColumnsAreNeverSelected() throws Exception {
        String pan = "ABCPZ" + (1000 + (int) (Math.random() * 8999)) + "K";
        UUID p = partner("Tax", pan);
        jdbc.update("insert into payouts (id, payout_number, partner_id, status, as_of_date, fy_start, commission_total, "
                        + "tds_status, tds_section, tds_rate, tds_base, tds_amount, net_amount, notes) values (?, ?, ?, "
                        + "'PENDING_APPROVAL', '2009-05-10', '2009-04-01', 1200.00, 'CALCULATED', 'TEST', 10, 1200.00, "
                        + "120.00, 1080.00, 'Analytics report test')",
                UUID.randomUUID(), AnalyticsFixtures.number("TPO-"), p);
        jdbc.update("insert into partner_adjustments (id, adjustment_number, partner_id, kind, amount, reason, status, "
                        + "created_at) values (?, ?, ?, 'MANUAL', -250.00, 'Analytics report recovery', 'PENDING_APPROVAL', ?)",
                UUID.randomUUID(), AnalyticsFixtures.number("TAD-"), p, AnalyticsFixtures.at(LocalDate.of(2009, 5, 12)));

        String body = run("tds-adjustments", Map.of("from", "2009-05-01", "to", "2009-05-31", "partnerId", p.toString()));
        assertThat((Integer) JsonPath.read(body, "$.data.totalElements")).isEqualTo(2);
        assertThat(decimal(body, "$.data.totals.tds_amount")).isEqualByComparingTo("120.00");
        assertThat(decimal(body, "$.data.totals.adjustment_amount")).isEqualByComparingTo("-250.00");
        assertThat((List<String>) JsonPath.read(body, "$.data.rows[*].pan")).containsOnly("••" + pan.substring(6));
        assertThat(body).doesNotContain(pan);
        // PAN can't be searched or sorted on.
        assertThat((Integer) JsonPath.read(run("tds-adjustments", Map.of("from", "2009-05-01", "to", "2009-05-31",
                "q", pan)), "$.data.totalElements")).isZero();
        mvc.perform(get("/api/v1/reports/tds-adjustments").param("sort", "pan").with(bearer(sa)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors[0].field").value("sort"));

        String csv = export(sa, "tds-adjustments", Map.of("from", "2009-05-01", "to", "2009-05-31",
                "partnerId", p.toString())).getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain(pan).contains("••" + pan.substring(6));

        // The partners report says whether a PAN is on file, never what it is, and has no bank columns.
        String partners = run("partners", Map.of("partnerId", p.toString()));
        assertThat((Boolean) JsonPath.read(partners, "$.data.rows[0].pan_on_file")).isTrue();
        assertThat(partners).doesNotContain(pan).doesNotContain("bank");
    }

    @Test
    void reportsAndColumnsFollowModulePermissionsAndScopes() throws Exception {
        partner("Visible", null);
        // No reports permission: nothing opens.
        Session none = login(user(List.of(module("PARTNERS", "ALL", false))));
        mvc.perform(get("/api/v1/reports").with(bearer(none))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/partners").with(bearer(none))).andExpect(status().isForbidden());

        // Reports with partners only: the partners report without other modules' columns; other reports refused.
        Session partnersOnly = login(user(List.of(module("REPORTS", null, false), module("PARTNERS", "ALL", false))));
        String catalog = mvc.perform(get("/api/v1/reports").with(bearer(partnersOnly))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((List<String>) JsonPath.read(catalog, "$.data[?(@.available == true)].code")).containsExactly("partners");
        assertThat((List<String>) JsonPath.read(catalog, "$.data[?(@.code == 'invoices')].reason")).containsExactly("NO_ACCESS");
        String partners = run(partnersOnly, "partners", Map.of());
        List<String> keys = JsonPath.read(partners, "$.data.columns[*].key");
        assertThat(keys).contains("name", "status").doesNotContain("collected_ex_gst", "invoiced_taxable",
                "commission_earned", "leads_created");
        assertThat((Map<String, Object>) JsonPath.read(partners, "$.data.rows[0]"))
                .doesNotContainKeys("collected_ex_gst", "invoiced_taxable", "commission_earned");
        assertThat((Map<String, Object>) JsonPath.read(partners, "$.data.totals")).isEmpty();
        mvc.perform(get("/api/v1/reports/invoices").with(bearer(partnersOnly))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/partners").param("sort", "collected_ex_gst").with(bearer(partnersOnly)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/partner-options").param("q", "analytics").with(bearer(partnersOnly)))
                .andExpect(status().isOk());

        // A SELF or TEAM scope can't be evaluated yet (D-30): the report is refused rather than widened.
        Session scoped = login(user(List.of(module("REPORTS", null, false), module("PARTNERS", "TEAM", false))));
        mvc.perform(get("/api/v1/reports/partners").with(bearer(scoped))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/partner-options").with(bearer(scoped))).andExpect(status().isForbidden());

        // Export needs Reports export and the module's own export.
        Session readOnly = login(createUser(RoleCode.ADMIN));
        String adminCatalog = mvc.perform(get("/api/v1/reports").with(bearer(readOnly))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((List<Boolean>) JsonPath.read(adminCatalog, "$.data[*].canExport")).containsOnly(false);
        mvc.perform(get("/api/v1/reports/payments/export").with(bearer(readOnly))).andExpect(status().isForbidden());
        Session reportsExport = login(user(List.of(module("REPORTS", null, true), module("PAYMENTS", "ALL", false))));
        mvc.perform(get("/api/v1/reports/payments/export").with(bearer(reportsExport))).andExpect(status().isForbidden());
        Session both = login(user(List.of(module("REPORTS", null, true), module("PAYMENTS", "ALL", true))));
        mvc.perform(get("/api/v1/reports/payments/export").with(bearer(both))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/invoices/export").with(bearer(both))).andExpect(status().isForbidden());
    }

    @Test
    void exportsAreCsvWithAByteOrderMarkAndFormulaGuard() throws Exception {
        UUID p = partner("Export", null);
        fx.issued(p, LocalDate.of(2010, 2, 3), LocalDate.of(2010, 2, 18), "100.00", "118.00", "0",
                "=HYPERLINK(\"http://x\")");

        MvcResult result = export(sa, "invoices", Map.of("from", "2010-02-01", "to", "2010-02-28", "partnerId", p.toString()));
        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("iotiq-report-invoices-2010-02-01-to-2010-02-28.csv");
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv.lines().findFirst().orElseThrow()).contains("Invoice,Date,Due,Partner,Billed to,Status");
        assertThat(csv).contains("'=HYPERLINK").contains("Overdue").contains("118.00");
        assertThat(csv.lines().count()).isEqualTo(2);
    }

    @Test
    void invalidRequestsAreRefusedWithoutDetail() throws Exception {
        mvc.perform(get("/api/v1/reports/unknown").with(bearer(sa))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/invoices").param("sort", "grand_total; drop table x").with(bearer(sa)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/invoices").param("status", "WHATEVER").with(bearer(sa)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors[0].field").value("status"));
        mvc.perform(get("/api/v1/reports/products").param("partnerId", UUID.randomUUID().toString()).with(bearer(sa)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/invoices").param("size", "0").with(bearer(sa)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/invoices").param("size", "201").with(bearer(sa)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/invoices").param("from", "2010-01-01").param("to", "2009-01-01").with(bearer(sa)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/invoices").with(bearer(sa))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period.preset").value("THIS_FY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        // Every report runs on an empty period.
        for (String code : List.of("partners", "leads", "products", "estimates", "invoices", "payments", "receivables",
                "commissions", "payouts", "tds-adjustments")) {
            String body = run(code, Map.of("from", "2001-01-01", "to", "2001-01-02"));
            assertThat((String) JsonPath.read(body, "$.data.code")).isEqualTo(code);
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private String run(String code, Map<String, String> params) throws Exception {
        return run(sa, code, params);
    }

    private String run(Session session, String code, Map<String, String> params) throws Exception {
        var request = get("/api/v1/reports/{code}", code).with(bearer(session));
        params.forEach(request::param);
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private MvcResult export(Session session, String code, Map<String, String> params) throws Exception {
        var request = get("/api/v1/reports/{code}/export", code).with(bearer(session));
        params.forEach(request::param);
        return mvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private UUID partner(String name, String pan) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("name", "Analytics report " + name + " " + tag + " " + (++counter),
                "category", "INTERIOR_DESIGNER"));
        if (pan != null) {
            body.put("pan", pan);
        }
        String created = mvc.perform(withJson(post("/api/v1/partners").with(bearer(sa)), body(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(JsonPath.read(created, "$.data.id"));
    }

    private static BigDecimal decimal(String body, String path) {
        Object value = JsonPath.read(body, path);
        return new BigDecimal(value.toString());
    }

    private static Map<String, Object> module(String code, String scope, boolean export) {
        Map<String, Object> map = new HashMap<>();
        map.put("moduleCode", code);
        map.put("read", true);
        map.put("export", export);
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
