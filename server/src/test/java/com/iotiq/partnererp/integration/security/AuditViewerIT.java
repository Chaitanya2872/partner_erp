package com.iotiq.partnererp.integration.security;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit viewer (Phase 13): module filter, metadata-only search, authorised export with masking, and the
 * append-only guard in the database.
 */
class AuditViewerIT extends ApiIntegrationTest {

    @Test
    void filtersByModuleAndSearchesMetadataButNeverRecordedValues() throws Exception {
        TestUser saUser = createUser(RoleCode.SUPER_ADMIN);
        Session sa = login(saUser);
        String tag = "unsearchable" + UUID.randomUUID().toString().substring(0, 8);
        UUID partner = createPartner(sa, "Audit " + tag, null);

        mvc.perform(get("/api/v1/audit-logs").with(bearer(sa)).param("module", "PARTNERS")
                        .param("entityId", partner.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].action").value("PARTNER_CREATED"));
        mvc.perform(get("/api/v1/audit-logs").with(bearer(sa)).param("module", "PAYMENTS")
                        .param("entityId", partner.toString()))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/audit-logs").with(bearer(sa)).param("module", "NOPE"))
                .andExpect(status().isBadRequest());

        // The partner's name is only inside the recorded values, so a search for it finds nothing.
        mvc.perform(get("/api/v1/audit-logs").with(bearer(sa)).param("q", tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        // Actor email, action and a full entity id are searchable.
        mvc.perform(get("/api/v1/audit-logs").with(bearer(sa)).param("q", saUser.email()).param("module", "PARTNERS"))
                .andExpect(jsonPath("$.data[0].entityId").value(partner.toString()));
        mvc.perform(get("/api/v1/audit-logs").with(bearer(sa)).param("q", partner.toString()))
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/audit-logs").with(bearer(sa)).param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/audit-logs/catalog").with(bearer(sa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'TAX')].actions[0]").value("GST_REGISTRATION_UPDATED"));
        mvc.perform(get("/api/v1/audit-logs/actors").with(bearer(sa)).param("q", saUser.email()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
    }

    @Test
    void exportNeedsAuditExportMasksSensitiveValuesAndIsAudited() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        String pan = "ZYXWV" + ThreadLocalRandom.current().nextInt(1000, 9999) + "K";
        UUID partner = createPartner(sa, "Masked " + UUID.randomUUID(), pan);
        UUID product = UUID.randomUUID();
        jdbc.update("insert into audit_logs (entity_type, entity_id, action, new_values) values ('PRODUCT', ?, "
                + "'PRODUCT_UPDATED', '{\"partnerPrice\": 100, \"b2cPrice\": 140, \"costPrice\": 90}'::jsonb)", product);

        TestUser reader = createUser(RoleCode.ADMIN);
        grant(sa, reader, Map.of("moduleCode", "AUDIT_LOGS", "read", true));
        Session readerSession = login(reader);
        mvc.perform(get("/api/v1/audit-logs").with(bearer(readerSession)).param("entityId", product.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].newValues.partnerPrice").value(100))
                .andExpect(jsonPath("$.data[0].newValues.b2cPrice").doesNotExist());
        mvc.perform(get("/api/v1/audit-logs/export").with(bearer(readerSession))).andExpect(status().isForbidden());

        TestUser exporter = createUser(RoleCode.ADMIN);
        grant(sa, exporter, Map.of("moduleCode", "AUDIT_LOGS", "read", true, "export", true));
        Session exporterSession = login(exporter);
        MockHttpServletResponse partnerCsv = mvc.perform(get("/api/v1/audit-logs/export").with(bearer(exporterSession))
                        .param("entityId", partner.toString()))
                .andExpect(status().isOk()).andReturn().getResponse();
        byte[] bytes = partnerCsv.getContentAsByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(partnerCsv.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(partnerCsv.getHeader("Content-Disposition")).contains("iotiq-audit-log-");
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertThat(text).contains("PARTNER_CREATED").contains("Partners").doesNotContain(pan)
                .contains(pan.substring(pan.length() - 4));
        String productCsv = mvc.perform(get("/api/v1/audit-logs/export").with(bearer(exporterSession))
                        .param("entityId", product.toString()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(productCsv).contains("partnerPrice").doesNotContain("b2cPrice").doesNotContain("costPrice");

        // The export itself is recorded: filters and row count, never the rows.
        String audit = jdbc.queryForObject("select new_values::text from audit_logs where action = 'AUDIT_LOG_EXPORTED' "
                + "and user_id = ? order by created_at limit 1", String.class, exporter.id());
        assertThat(audit).contains(partner.toString()).contains("\"rows\": 1").doesNotContain("PARTNER_CREATED");
    }

    @Test
    void auditEntriesCannotBeChangedOrDeleted() {
        UUID entity = UUID.randomUUID();
        UUID actor = createUser(RoleCode.USER).id();
        jdbc.update("insert into audit_logs (user_id, entity_type, entity_id, action) values (?, 'USER', ?, 'LOGOUT')",
                actor, entity);
        assertThatThrownBy(() -> jdbc.update("update audit_logs set action = 'LOGIN_SUCCEEDED' where entity_id = ?", entity))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("update audit_logs set new_values = '{}'::jsonb where entity_id = ?", entity))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_logs where entity_id = ?", entity))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("truncate audit_logs")).hasMessageContaining("append-only");
        // Only the users foreign key's ON DELETE SET NULL may touch a row.
        assertThat(jdbc.update("update audit_logs set user_id = null where entity_id = ?", entity)).isEqualTo(1);
        assertThat(count("select count(*) from audit_logs where entity_id = ? and action = 'LOGOUT'", entity)).isEqualTo(1);
    }

    @Test
    void auditViewerNeedsAuditRead() throws Exception {
        Session admin = login(createUser(RoleCode.ADMIN));
        mvc.perform(get("/api/v1/audit-logs").with(bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-logs/catalog").with(bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-logs/actors").with(bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-logs/export").with(bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-logs")).andExpect(status().isUnauthorized());
    }

    private UUID createPartner(Session sa, String name, String pan) throws Exception {
        Map<String, Object> request = pan == null ? Map.of("name", name, "category", "INTERIOR_DESIGNER")
                : Map.of("name", name, "category", "INTERIOR_DESIGNER", "pan", pan);
        String body = mvc.perform(withJson(post("/api/v1/partners").with(bearer(sa)), body(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private void grant(Session sa, TestUser target, Map<String, Object> module) throws Exception {
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", target.id()).with(bearer(sa)),
                        body(Map.of("modules", List.of(module), "actions", List.of()))))
                .andExpect(status().isOk());
    }
}
