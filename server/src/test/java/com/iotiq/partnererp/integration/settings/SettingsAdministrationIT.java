package com.iotiq.partnererp.integration.settings;

import com.iotiq.partnererp.config.SecurityProperties;
import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Settings and Administration (Phase 13): company profile and defaults, commercial policy, partner tiers and the
 * read-only numbering, security and controls views. Company settings and tiers are shared rows, so every test that
 * changes them restores the original values afterwards (other tests rely on the seeded defaults).
 */
class SettingsAdministrationIT extends ApiIntegrationTest {

    @Autowired
    private SecurityProperties securityProperties;

    private Map<String, Object> savedCompany;
    private List<Map<String, Object>> savedTiers;

    @BeforeEach
    void saveShared() {
        savedCompany = jdbc.queryForMap("select phone, email, terms, estimate_validity_days, invoice_due_days, "
                + "default_gst_percent, acknowledgement_hours, estimate_sla_days, lead_protection_days from company_settings");
        savedTiers = jdbc.queryForList("select tier_level, name, commission_percent, minimum_revenue, payout_business_days "
                + "from partner_tiers order by tier_level");
    }

    @AfterEach
    void restoreShared() {
        jdbc.update("update company_settings set phone = ?, email = ?, terms = ?, estimate_validity_days = ?, "
                        + "invoice_due_days = ?, default_gst_percent = ?, acknowledgement_hours = ?, estimate_sla_days = ?, "
                        + "lead_protection_days = ?",
                savedCompany.get("phone"), savedCompany.get("email"), savedCompany.get("terms"),
                savedCompany.get("estimate_validity_days"), savedCompany.get("invoice_due_days"),
                savedCompany.get("default_gst_percent"), savedCompany.get("acknowledgement_hours"),
                savedCompany.get("estimate_sla_days"), savedCompany.get("lead_protection_days"));
        for (Map<String, Object> t : savedTiers) {
            jdbc.update("update partner_tiers set name = ?, commission_percent = ?, minimum_revenue = ?, "
                            + "payout_business_days = ? where tier_level = ?", t.get("name"), t.get("commission_percent"),
                    t.get("minimum_revenue"), t.get("payout_business_days"), t.get("tier_level"));
        }
    }

    @Test
    void companyProfileIsReadWithSettingsReadAndChangedWithSettingsWrite() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser reader = createUser(RoleCode.ADMIN);
        grant(sa, reader, List.of(module("SETTINGS", false)), List.of());
        Session readerSession = login(reader);
        Session plainAdmin = login(createUser(RoleCode.ADMIN));

        mvc.perform(get("/api/v1/settings/company").with(bearer(plainAdmin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/settings/company").with(bearer(readerSession)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canEdit").value(false))
                .andExpect(jsonPath("$.data.canManageCommercial").value(false))
                .andExpect(jsonPath("$.data.limits.maxDueDays").value(365));
        mvc.perform(withJson(put("/api/v1/settings/company").with(bearer(readerSession)), body(company(companyVersion()))))
                .andExpect(status().isForbidden());

        TestUser writer = createUser(RoleCode.ADMIN);
        grant(sa, writer, List.of(module("SETTINGS", true)), List.of());
        Session writerSession = login(writer);
        Map<String, Object> request = company(companyVersion());
        request.put("email", "  Accounts@Example.Test ");
        request.put("invoiceDueDays", 30);
        mvc.perform(withJson(put("/api/v1/settings/company").with(bearer(writerSession)), body(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("accounts@example.test"))
                .andExpect(jsonPath("$.data.invoiceDueDays").value(30))
                .andExpect(jsonPath("$.data.updatedBy").value("Test ADMIN"))
                .andExpect(jsonPath("$.data.canEdit").value(true));
        String audit = jdbc.queryForObject("select old_values::text || new_values::text from audit_logs "
                + "where action = 'COMPANY_SETTINGS_UPDATED' and user_id = ?", String.class, writer.id());
        // Changed fields only.
        assertThat(audit).contains("invoiceDueDays").contains("accounts@example.test").doesNotContain("estimateSlaDays");

        // The same request again changes nothing and isn't audited again.
        Map<String, Object> again = company(companyVersion());
        again.put("email", "accounts@example.test");
        again.put("invoiceDueDays", 30);
        mvc.perform(withJson(put("/api/v1/settings/company").with(bearer(writerSession)), body(again)))
                .andExpect(status().isOk());
        assertThat(count("select count(*) from audit_logs where action = 'COMPANY_SETTINGS_UPDATED' and user_id = ?",
                writer.id())).isEqualTo(1);

        // Stale version.
        mvc.perform(withJson(put("/api/v1/settings/company").with(bearer(writerSession)), body(company(companyVersion() - 1))))
                .andExpect(status().isConflict());
    }

    @Test
    void companyValuesAreValidated() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        Map<String, Object> bad = company(companyVersion());
        bad.put("email", "not-an-email");
        bad.put("phone", "12ab");
        bad.put("terms", "x".repeat(5001));
        bad.put("estimateValidityDays", 0);
        bad.put("invoiceDueDays", 366);
        bad.put("defaultGstPercent", 18.0005);
        bad.put("acknowledgementHours", null);
        bad.put("estimateSlaDays", -1);
        String response = mvc.perform(withJson(put("/api/v1/settings/company").with(bearer(sa)), body(bad)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        List<String> fields = JsonPath.read(response, "$.error.fieldErrors[*].field");
        assertThat(fields).containsExactlyInAnyOrder("email", "phone", "terms", "estimateValidityDays", "invoiceDueDays",
                "defaultGstPercent", "acknowledgementHours", "estimateSlaDays");

        Map<String, Object> gst = company(companyVersion());
        gst.put("defaultGstPercent", 101);
        mvc.perform(withJson(put("/api/v1/settings/company").with(bearer(sa)), body(gst)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("defaultGstPercent"));
        mvc.perform(withJson(put("/api/v1/settings/company").with(bearer(sa)), body(Map.of("phone", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("version"));
    }

    @Test
    void commercialSettingsAndTiersNeedManageSettings() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser writer = createUser(RoleCode.ADMIN);
        grant(sa, writer, List.of(module("SETTINGS", true)), List.of());
        Session writerSession = login(writer);
        mvc.perform(withJson(put("/api/v1/settings/commercial").with(bearer(writerSession)),
                        body(Map.of("leadProtectionDays", 60, "version", companyVersion()))))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(put("/api/v1/settings/tiers").with(bearer(writerSession)), body(tiers(currentTiers(sa)))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/settings/tiers").with(bearer(writerSession)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canEdit").value(false))
                .andExpect(jsonPath("$.data.tiers.length()").value(4));

        TestUser manager = createUser(RoleCode.ADMIN);
        grant(sa, manager, List.of(module("SETTINGS", true)), List.of("MANAGE_SETTINGS"));
        Session managerSession = login(manager);
        mvc.perform(withJson(put("/api/v1/settings/commercial").with(bearer(managerSession)),
                        body(Map.of("leadProtectionDays", 0, "version", companyVersion()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("leadProtectionDays"));
        mvc.perform(withJson(put("/api/v1/settings/commercial").with(bearer(managerSession)),
                        body(Map.of("leadProtectionDays", 60, "version", companyVersion()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leadProtectionDays").value(60))
                .andExpect(jsonPath("$.data.canManageCommercial").value(true));
        assertThat(jdbc.queryForObject("select new_values::text from audit_logs where action = 'COMMERCIAL_SETTINGS_UPDATED' "
                + "and user_id = ?", String.class, manager.id())).contains("\"leadProtectionDays\": 60");

        // Partners readers see the tiers (the partner form shows them) but can't change them.
        Session partnersReader = login(createUser(RoleCode.ADMIN));
        mvc.perform(get("/api/v1/settings/tiers").with(bearer(partnersReader))).andExpect(status().isOk());
        Session user = login(createUser(RoleCode.USER));
        mvc.perform(get("/api/v1/settings/tiers").with(bearer(user))).andExpect(status().isForbidden());
    }

    @Test
    void tierChangesApplyForwardOnlyAndNeverRecalculateSnapshots() throws Exception {
        TestUser admin = createUser(RoleCode.SUPER_ADMIN);
        Session sa = login(admin);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        UUID partner = createPartner(sa, "Tier snapshot " + tag);
        UUID before = createLead(sa, partner, "Before " + tag);
        mvc.perform(post("/api/v1/leads/{id}/acknowledge", before).with(bearer(sa))).andExpect(status().isOk());
        String invoiceFees = jdbc.queryForObject("select coalesce(md5(string_agg(id::text || ':' || fee_percent::text, ',' "
                + "order by id)), '') from invoices", String.class);
        String payoutPercents = jdbc.queryForObject("select coalesce(md5(string_agg(id::text || ':' || coalesce(commission_percent::text, ''), ',' "
                + "order by id)), '') from payout_lines", String.class);
        String partnerTiers = jdbc.queryForObject("select coalesce(md5(string_agg(id::text || ':' || tier_id::text, ',' "
                + "order by id)), '') from partners", String.class);

        List<Map<String, Object>> tiers = currentTiers(sa);
        tiers.get(0).put("commissionPercent", 9.5);
        tiers.get(1).put("minimumRevenue", 1500000);
        tiers.get(1).put("name", "Preferred Plus");
        mvc.perform(withJson(put("/api/v1/settings/tiers").with(bearer(sa)), body(tiers(tiers))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tiers[0].commissionPercent").value(9.5))
                .andExpect(jsonPath("$.data.tiers[1].name").value("Preferred Plus"))
                .andExpect(jsonPath("$.data.tiers[1].minimumRevenue").value(1500000.0));

        // The lead acknowledged before keeps 8%; a lead acknowledged afterwards locks the new rate.
        assertThat(jdbc.queryForObject("select locked_fee_percent from leads where id = ?", BigDecimal.class, before))
                .isEqualByComparingTo("8");
        UUID after = createLead(sa, partner, "After " + tag);
        mvc.perform(post("/api/v1/leads/{id}/acknowledge", after).with(bearer(sa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockedFeePercent").value(9.5));
        // Issued invoices, payout lines and partner tiers are untouched (no recalculation, no automatic upgrade).
        assertThat(jdbc.queryForObject("select coalesce(md5(string_agg(id::text || ':' || fee_percent::text, ',' "
                + "order by id)), '') from invoices", String.class)).isEqualTo(invoiceFees);
        assertThat(jdbc.queryForObject("select coalesce(md5(string_agg(id::text || ':' || coalesce(commission_percent::text, ''), ',' "
                + "order by id)), '') from payout_lines", String.class)).isEqualTo(payoutPercents);
        assertThat(jdbc.queryForObject("select coalesce(md5(string_agg(id::text || ':' || tier_id::text, ',' "
                + "order by id)), '') from partners", String.class)).isEqualTo(partnerTiers);

        // One audit entry per changed tier, with the changed fields only.
        List<String> audits = jdbc.queryForList("select new_values::text from audit_logs where action = 'PARTNER_TIER_UPDATED' "
                + "and user_id = ? order by new_values->>'level'", String.class, admin.id());
        assertThat(audits).hasSize(2);
        assertThat(audits.get(0)).contains("\"commissionPercent\": \"9.5\"").doesNotContain("minimumRevenue");
        assertThat(audits.get(1)).contains("Preferred Plus").contains("1500000");
    }

    @Test
    void tierValuesAreValidatedAgainstEachOther() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        List<Map<String, Object>> tiers = currentTiers(sa);
        tiers.get(0).put("minimumRevenue", 100);
        tiers.get(2).put("minimumRevenue", 500000);
        tiers.get(3).put("name", tiers.get(1).get("name").toString().toUpperCase());
        tiers.get(1).put("commissionPercent", 100.5);
        tiers.get(2).put("payoutBusinessDays", 0);
        String response = mvc.perform(withJson(put("/api/v1/settings/tiers").with(bearer(sa)), body(tiers(tiers))))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        List<String> fields = JsonPath.read(response, "$.error.fieldErrors[*].field");
        assertThat(fields).containsExactlyInAnyOrder("tiers[1].minimumRevenue", "tiers[3].minimumRevenue",
                "tiers[4].name", "tiers[2].commissionPercent", "tiers[3].payoutBusinessDays");

        mvc.perform(withJson(put("/api/v1/settings/tiers").with(bearer(sa)), body(tiers(currentTiers(sa).subList(0, 3)))))
                .andExpect(status().isBadRequest());
        List<Map<String, Object>> duplicate = currentTiers(sa);
        duplicate.get(3).put("level", 1);
        mvc.perform(withJson(put("/api/v1/settings/tiers").with(bearer(sa)), body(tiers(duplicate))))
                .andExpect(status().isBadRequest());
        // Nothing was saved.
        assertThat(jdbc.queryForObject("select minimum_revenue from partner_tiers where tier_level = 1", BigDecimal.class))
                .isEqualByComparingTo("0");
    }

    @Test
    void concurrentTierEditsDontOverwriteEachOther() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        Session other = login(createUser(RoleCode.SUPER_ADMIN));
        List<Map<String, Object>> first = currentTiers(sa);
        first.get(3).put("payoutBusinessDays", 6);
        List<Map<String, Object>> second = currentTiers(sa);
        second.get(3).put("payoutBusinessDays", 5);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> {
                        start.await();
                        return mvc.perform(withJson(put("/api/v1/settings/tiers").with(bearer(sa)), body(tiers(first))))
                                .andReturn().getResponse().getStatus();
                    },
                    () -> {
                        start.await();
                        return mvc.perform(withJson(put("/api/v1/settings/tiers").with(bearer(other)), body(tiers(second))))
                                .andReturn().getResponse().getStatus();
                    });
            List<Future<Integer>> futures = new ArrayList<>();
            calls.forEach(c -> futures.add(pool.submit(c)));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select payout_business_days from partner_tiers where tier_level = 4")).isIn(5L, 6L);
    }

    @Test
    void numberingSecurityAndControlsAreReadOnlyAndHoldNoSecrets() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        mvc.perform(get("/api/v1/settings/numbering").with(bearer(sa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.prefix == 'INV')].format").value("INV-YYYY-NNNNNN"))
                .andExpect(jsonPath("$.data[?(@.prefix == 'INV')].capacity").value(999999))
                .andExpect(jsonPath("$.data.length()").value(6));
        String security = mvc.perform(get("/api/v1/settings/security").with(bearer(sa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password.minLength").value(securityProperties.password().minLength()))
                .andExpect(jsonPath("$.data.lockout.maxFailedAttempts").value(securityProperties.login().maxFailedAttempts()))
                .andExpect(jsonPath("$.data.sessions.accessTokenMinutes")
                        .value((int) securityProperties.jwt().accessTokenTtl().toMinutes()))
                .andReturn().getResponse().getContentAsString();
        assertThat(security).doesNotContain(securityProperties.jwt().secret()).doesNotContainIgnoringCase("passwordHash");
        mvc.perform(get("/api/v1/settings/controls").with(bearer(sa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.area == 'Payouts')]").isNotEmpty());
        // There is no endpoint to change any of them.
        mvc.perform(withJson(put("/api/v1/settings/numbering").with(bearer(sa)), "{}"))
                .andExpect(status().is4xxClientError());

        Session user = login(createUser(RoleCode.USER));
        mvc.perform(get("/api/v1/settings/numbering").with(bearer(user))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/settings/security").with(bearer(user))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/settings/controls").with(bearer(user))).andExpect(status().isForbidden());
    }

    private Map<String, Object> company(long version) {
        Map<String, Object> m = new HashMap<>();
        m.put("phone", savedCompany.get("phone"));
        m.put("email", savedCompany.get("email"));
        m.put("terms", savedCompany.get("terms"));
        m.put("estimateValidityDays", savedCompany.get("estimate_validity_days"));
        m.put("invoiceDueDays", savedCompany.get("invoice_due_days"));
        m.put("defaultGstPercent", savedCompany.get("default_gst_percent"));
        m.put("acknowledgementHours", savedCompany.get("acknowledgement_hours"));
        m.put("estimateSlaDays", savedCompany.get("estimate_sla_days"));
        m.put("version", version);
        return m;
    }

    private long companyVersion() {
        return count("select version from company_settings");
    }

    private List<Map<String, Object>> currentTiers(Session session) throws Exception {
        String response = mvc.perform(get("/api/v1/settings/tiers").with(bearer(session)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> tiers = JsonPath.read(response, "$.data.tiers");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> t : tiers) {
            Map<String, Object> m = new HashMap<>();
            m.put("level", t.get("level"));
            m.put("name", t.get("name"));
            m.put("commissionPercent", t.get("commissionPercent"));
            m.put("minimumRevenue", t.get("minimumRevenue"));
            m.put("payoutBusinessDays", t.get("payoutBusinessDays"));
            m.put("version", t.get("version"));
            out.add(m);
        }
        return out;
    }

    private static Map<String, Object> tiers(List<Map<String, Object>> tiers) {
        return Map.of("tiers", tiers);
    }

    private static Map<String, Object> module(String code, boolean write) {
        return Map.of("moduleCode", code, "read", true, "write", write);
    }

    private void grant(Session sa, TestUser target, List<Map<String, Object>> modules, List<String> actions)
            throws Exception {
        List<Map<String, Object>> all = new ArrayList<>(modules);
        all.add(Map.of("moduleCode", "PARTNERS", "read", true));
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", target.id()).with(bearer(sa)),
                        body(Map.of("modules", all, "actions", actions))))
                .andExpect(status().isOk());
    }

    private UUID createPartner(Session sa, String name) throws Exception {
        String body = mvc.perform(withJson(post("/api/v1/partners").with(bearer(sa)),
                        body(Map.of("name", name, "category", "INTERIOR_DESIGNER"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private UUID createLead(Session sa, UUID partner, String client) throws Exception {
        String body = mvc.perform(withJson(post("/api/v1/leads").with(bearer(sa)),
                        body(Map.of("clientName", client, "partnerId", partner, "projectType", "VILLA"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }
}
