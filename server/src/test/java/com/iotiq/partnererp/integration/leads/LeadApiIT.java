package com.iotiq.partnererp.integration.leads;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Leads over HTTP (BUSINESS_RULES §4): validation, conflict check, acknowledge, stage rules (D-04), soft delete
 * (D-26), SLA flags and filters, permissions and data scope.
 */
class LeadApiIT extends ApiIntegrationTest {

    private Session sa;
    private Session admin;
    /** Unique per test, so searches only see this test's records. */
    private String tag;
    private UUID partnerA;
    private UUID partnerB;

    @BeforeEach
    void setUp() throws Exception {
        sa = login(createUser(RoleCode.SUPER_ADMIN));
        admin = login(createUser(RoleCode.ADMIN));
        tag = "T" + UUID.randomUUID().toString().substring(0, 8);
        partnerA = partner("Alpha " + tag);
        partnerB = partner("Beta " + tag);
    }

    // ------------------------------------------------------------------------------------------------ D-16

    @Test
    void everyLeadGetsAYearlyNumberAndAnOptionalValidatedEmail() throws Exception {
        String year = String.valueOf(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).getYear());
        String first = mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)),
                        body(lead(partnerA, "clientEmail", "  Rao.Family@Example.COM "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.clientEmail").value("rao.family@example.com"))
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)), body(lead(partnerA))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.clientEmail").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String a = JsonPath.read(first, "$.data.leadNumber");
        String b = JsonPath.read(second, "$.data.leadNumber");
        assertThat(a).matches("LEAD-" + year + "-\\d{6}");
        assertThat(Integer.parseInt(b.substring(10))).isEqualTo(Integer.parseInt(a.substring(10)) + 1);

        mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)), body(lead(partnerA, "clientEmail", "rao@"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("clientEmail"));

        // The number is found by search, can't be changed and is assigned even to rows written outside the API.
        String found = search("q", a.toLowerCase(java.util.Locale.ROOT));
        assertIds(found, UUID.fromString(JsonPath.read(first, "$.data.id")));
        UUID id = UUID.fromString(JsonPath.read(first, "$.data.id"));
        mvc.perform(withJson(put("/api/v1/leads/{id}", id).with(bearer(admin)),
                        body(lead(partnerA, "leadNumber", "LEAD-1999-000001", "clientEmail", "", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leadNumber").value(a))
                .andExpect(jsonPath("$.data.clientEmail").doesNotExist());
        UUID raw = UUID.randomUUID();
        jdbc.update("insert into leads (id, partner_id, client_name, project_type) values (?, ?, ?, 'VILLA')",
                raw, partnerA, "Raw " + tag);
        assertThat(jdbc.queryForObject("select lead_number from leads where id = ?", String.class, raw))
                .matches("LEAD-" + year + "-\\d{6}").isNotIn(a, b);
    }

    // ------------------------------------------------------------------------------------------------ CRUD

    @Test
    void createDefaultsToNewAndRecordsHistoryAndAudit() throws Exception {
        String body = mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)), body(lead(partnerA,
                        "clientName", "  Rao   Residence " + tag, "units", 3, "budget", 450000, "city", "Hyderabad"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.clientName").value("Rao Residence " + tag))
                .andExpect(jsonPath("$.data.stage").value("NEW"))
                .andExpect(jsonPath("$.data.stageLabel").value("New"))
                .andExpect(jsonPath("$.data.projectTypeLabel").value("Villa"))
                .andExpect(jsonPath("$.data.partner.name").value("Alpha " + tag))
                .andExpect(jsonPath("$.data.acknowledgedAt").doesNotExist())
                .andExpect(jsonPath("$.data.conflict").value(false))
                .andExpect(jsonPath("$.data.sla.ackHours").value(0))
                .andExpect(jsonPath("$.data.sla.ackBreach").value(false))
                .andExpect(jsonPath("$.data.sla.ackTargetHours").value(48))
                .andExpect(jsonPath("$.data.sla.estimateTargetDays").value(5))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.data.id"));

        mvc.perform(get("/api/v1/leads/{id}/history", id).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].event").value("CREATED"))
                .andExpect(jsonPath("$.data[0].toStage").value("NEW"))
                .andExpect(jsonPath("$.data[0].actorName").value("Test ADMIN"));
        assertThat(count("select count(*) from audit_logs where action = 'LEAD_CREATED' and entity_id = ?", id)).isEqualTo(1);
    }

    @Test
    void otherProjectTypeNeedsADescriptionAndOtherTypesDropIt() throws Exception {
        mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)), body(lead(partnerA, "projectType", "OTHER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'projectTypeOther')].message")
                        .value("Say what kind of project it is."));
        mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)),
                        body(lead(partnerA, "projectType", "OTHER", "projectTypeOther", "  Cold   storage "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectTypeOther").value("Cold storage"))
                .andExpect(jsonPath("$.data.projectTypeLabel").value("Other: Cold storage"));
        // A description sent with a normal type is discarded rather than stored.
        mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)),
                        body(lead(partnerA, "projectType", "VILLA", "projectTypeOther", "ignored"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectTypeOther").doesNotExist())
                .andExpect(jsonPath("$.data.projectTypeLabel").value("Villa"));
    }

    @Test
    void validationNamesTheMissingFields() throws Exception {
        mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)),
                        body(Map.of("clientName", " ", "units", -1, "budget", -5, "clientPhone", "abc"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'clientName')].message").value("Client name is required."))
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'partnerId')].message").value("Choose the referring partner."))
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'projectType')]").exists())
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'units')]").exists())
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'budget')]").exists())
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'clientPhone')]").exists());
        mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)), body(lead(UUID.randomUUID()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("partnerId"));
    }

    @Test
    void updateNeedsTheCurrentVersionAndAuditsOnlyChangedFields() throws Exception {
        UUID id = create(lead(partnerA, "city", "Pune"));
        mvc.perform(withJson(put("/api/v1/leads/{id}", id).with(bearer(admin)), body(lead(partnerA, "city", "Pune"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("version"));
        mvc.perform(withJson(put("/api/v1/leads/{id}", id).with(bearer(admin)),
                        body(lead(partnerA, "city", "Pune", "budget", 90000, "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budget").value(90000))
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(withJson(put("/api/v1/leads/{id}", id).with(bearer(admin)),
                        body(lead(partnerA, "city", "Pune", "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));
        String changed = jdbc.queryForObject(
                "select new_values::text from audit_logs where action = 'LEAD_UPDATED' and entity_id = ?", String.class, id);
        assertThat(changed).contains("budget").doesNotContain("city");
    }

    // ------------------------------------------------------------------------------------------------ conflict (BR-L6)

    @Test
    void samePhoneProtectedForAnotherPartnerSavesWithAWarning() throws Exception {
        UUID protectedLead = create(lead(partnerA, "clientPhone", "+91 98765 43210"));
        acknowledge(protectedLead).andExpect(status().isOk());

        // Different formatting, same last ten digits, other partner: saved, flagged, protecting partner named.
        String body = mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)),
                        body(lead(partnerB, "clientPhone", "098765-43210"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conflict").value(true))
                .andExpect(jsonPath("$.data.conflictPartnerName").value("Alpha " + tag))
                .andReturn().getResponse().getContentAsString();
        UUID duplicate = UUID.fromString(JsonPath.read(body, "$.data.id"));

        // The same partner registering the phone again is not a conflict.
        mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)), body(lead(partnerA, "clientPhone", "9876543210"))))
                .andExpect(jsonPath("$.data.conflict").value(false));

        assertIds(search("conflict", "true"), duplicate);
    }

    @Test
    void protectionCountsThroughItsLastDayAndNotAfter() throws Exception {
        UUID protectedLead = create(lead(partnerA, "clientPhone", "9000000001"));
        acknowledge(protectedLead).andExpect(status().isOk());
        LocalDate today = LocalDate.now(AppConstants.BUSINESS_ZONE);

        jdbc.update("update leads set protection_until = ? where id = ?", today, protectedLead);
        mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)), body(lead(partnerB, "clientPhone", "9000000001"))))
                .andExpect(jsonPath("$.data.conflict").value(true));
        mvc.perform(get("/api/v1/leads/{id}", protectedLead).with(bearer(admin)))
                .andExpect(jsonPath("$.data.protectionActive").value(true))
                .andExpect(jsonPath("$.data.sla.protectionDaysLeft").value(0));

        jdbc.update("update leads set protection_until = ? where id = ?", today.minusDays(1), protectedLead);
        mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)), body(lead(partnerB, "clientPhone", "9000000001"))))
                .andExpect(jsonPath("$.data.conflict").value(false));
        mvc.perform(get("/api/v1/leads/{id}", protectedLead).with(bearer(admin)))
                .andExpect(jsonPath("$.data.protectionActive").value(false))
                .andExpect(jsonPath("$.data.sla.protectionDaysLeft").doesNotExist());
    }

    @Test
    void unacknowledgedOrDeletedLeadsProtectNothing() throws Exception {
        UUID unacknowledged = create(lead(partnerA, "clientPhone", "9000000002"));
        mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)), body(lead(partnerB, "clientPhone", "9000000002"))))
                .andExpect(jsonPath("$.data.conflict").value(false));

        acknowledge(unacknowledged).andExpect(status().isOk());
        mvc.perform(archive("/api/v1/leads/{id}", unacknowledged).with(bearer(sa))).andExpect(status().isNoContent());
        mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)), body(lead(partnerB, "clientPhone", "9000000002"))))
                .andExpect(jsonPath("$.data.conflict").value(false));

        // Leads without a phone never conflict.
        mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)), body(lead(partnerB))))
                .andExpect(jsonPath("$.data.conflict").value(false));
    }

    @Test
    void theCheckRunsAgainOnEverySave() throws Exception {
        UUID protectedLead = create(lead(partnerA, "clientPhone", "9000000003"));
        acknowledge(protectedLead).andExpect(status().isOk());
        UUID other = create(lead(partnerB, "clientPhone", "9000000004"));
        mvc.perform(withJson(put("/api/v1/leads/{id}", other).with(bearer(admin)),
                        body(lead(partnerB, "clientPhone", "9000000003", "version", 0))))
                .andExpect(jsonPath("$.data.conflict").value(true))
                .andExpect(jsonPath("$.data.conflictPartnerName").value("Alpha " + tag));
        mvc.perform(withJson(put("/api/v1/leads/{id}", other).with(bearer(admin)),
                        body(lead(partnerB, "clientPhone", "9000000004", "version", 1))))
                .andExpect(jsonPath("$.data.conflict").value(false))
                .andExpect(jsonPath("$.data.conflictPartnerName").doesNotExist());
    }

    // ------------------------------------------------------------------------------------------------ acknowledge (BR-L5)

    @Test
    void acknowledgeLocksTheTierFeeAndStartsProtection() throws Exception {
        UUID id = create(lead(partnerA));
        acknowledge(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("REGISTERED"))
                .andExpect(jsonPath("$.data.lockedFeePercent").value(8.0))
                .andExpect(jsonPath("$.data.protectionUntil").value(LocalDate.now(AppConstants.BUSINESS_ZONE).plusDays(90).toString()))
                .andExpect(jsonPath("$.data.protectionActive").value(true))
                .andExpect(jsonPath("$.data.acknowledgedByName").value("Test SUPER_ADMIN"));
        acknowledge(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LEAD_ALREADY_ACKNOWLEDGED"));

        mvc.perform(get("/api/v1/leads/{id}/history", id).with(bearer(admin)))
                .andExpect(jsonPath("$.data[1].event").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.data[1].fromStage").value("NEW"))
                .andExpect(jsonPath("$.data[1].toStage").value("REGISTERED"));
        assertThat(count("select count(*) from audit_logs where action = 'LEAD_ACKNOWLEDGED' and entity_id = ?", id)).isEqualTo(1);

        // The fee stays locked when the tier's rate changes later.
        jdbc.update("update partner_tiers set commission_percent = 9 where tier_level = 1");
        try {
            mvc.perform(get("/api/v1/leads/{id}", id).with(bearer(admin))).andExpect(jsonPath("$.data.lockedFeePercent").value(8.0));
        } finally {
            jdbc.update("update partner_tiers set commission_percent = 8 where tier_level = 1");
        }
    }

    @Test
    void acknowledgeKeepsStagesOtherThanNewAndNeedsApprove() throws Exception {
        UUID id = create(lead(partnerA, "stage", "SITE_VISIT"));
        mvc.perform(post("/api/v1/leads/{id}/acknowledge", id).with(bearer(admin))).andExpect(status().isForbidden());
        acknowledge(id).andExpect(jsonPath("$.data.stage").value("SITE_VISIT"));
    }

    @Test
    void wonAndLostLeadsMustBeReopenedBeforeAcknowledgement() throws Exception {
        for (String closed : List.of("WON", "LOST")) {
            UUID id = create(lead(partnerA, "stage", closed));
            acknowledge(id)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.code").value("LEAD_CLOSED"));
            assertThat(count("select count(*) from leads where id = ? and acknowledged_at is null and locked_fee_percent is null"
                    + " and protection_until is null and stage = ?", id, closed)).isEqualTo(1);
            assertThat(count("select count(*) from lead_stage_history where lead_id = ? and event = 'ACKNOWLEDGED'", id)).isZero();
            assertThat(count("select count(*) from audit_logs where action = 'LEAD_ACKNOWLEDGED' and entity_id = ?", id)).isZero();

            // The existing reopen workflow (Leads approve) makes it acknowledgeable again.
            stage(sa, id, "NEGOTIATION", "Reopened to register the deal", 0).andExpect(status().isOk());
            acknowledge(id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.stage").value("NEGOTIATION"))
                    .andExpect(jsonPath("$.data.lockedFeePercent").value(8.0));
        }
    }

    @Test
    void thePartnerCanChangeBeforeAcknowledgement() throws Exception {
        UUID id = create(lead(partnerA));
        mvc.perform(withJson(put("/api/v1/leads/{id}", id).with(bearer(admin)), body(lead(partnerB, "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partner.id").value(partnerB.toString()));
    }

    @Test
    void thePartnerCannotChangeOnceAcknowledged() throws Exception {
        UUID id = create(lead(partnerA));
        acknowledge(id).andExpect(status().isOk());
        mvc.perform(withJson(put("/api/v1/leads/{id}", id).with(bearer(admin)), body(lead(partnerB, "version", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("partnerId"));
    }

    // ------------------------------------------------------------------------------------------------ stages (D-04)

    @Test
    void openStagesMoveFreelyAndReopeningNeedsApprove() throws Exception {
        UUID id = create(lead(partnerA));
        stage(admin, id, "NEGOTIATION", null, 0).andExpect(status().isOk());
        stage(admin, id, "PRESALES", "Client asked for a new layout", 1).andExpect(status().isOk());
        stage(admin, id, "WON", null, 2).andExpect(status().isOk());

        stage(admin, id, "NEGOTIATION", null, 3)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("STAGE_REOPEN_NOT_PERMITTED"));
        stage(admin, id, "LOST", null, 3).andExpect(status().isForbidden());
        // The same restriction applies through the edit form.
        mvc.perform(withJson(put("/api/v1/leads/{id}", id).with(bearer(admin)), body(lead(partnerA, "stage", "PRESALES", "version", 3))))
                .andExpect(status().isForbidden());

        stage(sa, id, "NEGOTIATION", "Deal reopened after budget revision", 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("NEGOTIATION"));

        mvc.perform(get("/api/v1/leads/{id}/history", id).with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[2].fromStage").value("NEGOTIATION"))
                .andExpect(jsonPath("$.data[2].toStage").value("PRESALES"))
                .andExpect(jsonPath("$.data[2].reason").value("Client asked for a new layout"))
                .andExpect(jsonPath("$.data[4].fromStage").value("WON"))
                .andExpect(jsonPath("$.data[4].reason").value("Deal reopened after budget revision"));
        assertThat(count("select count(*) from audit_logs where action = 'LEAD_STAGE_CHANGED' and entity_id = ?", id)).isEqualTo(4);
    }

    @Test
    void stageChangesNeedTheCurrentVersion() throws Exception {
        UUID id = create(lead(partnerA));
        stage(admin, id, "PRESALES", null, 0).andExpect(status().isOk());
        stage(admin, id, "SITE_VISIT", null, 0).andExpect(status().isConflict());
        // Choosing the current stage changes nothing.
        stage(admin, id, "PRESALES", null, 1).andExpect(jsonPath("$.data.version").value(1));
    }

    // ------------------------------------------------------------------------------------------------ delete (D-26)

    @Test
    void deleteIsSoftAndNeedsTheDeletePermission() throws Exception {
        UUID id = create(lead(partnerA, "clientName", "Gone " + tag));
        mvc.perform(archive("/api/v1/leads/{id}", id).with(bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(archive("/api/v1/leads/{id}", id).with(bearer(sa))).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/leads/{id}", id).with(bearer(admin))).andExpect(status().isNotFound());
        mvc.perform(archive("/api/v1/leads/{id}", id).with(bearer(sa))).andExpect(status().isNotFound());
        assertThat(JsonPath.<List<String>>read(search("q", tag), "$.data[*].id")).doesNotContain(id.toString());
        assertThat(count("select count(*) from leads where id = ? and deleted_at is not null", id)).isEqualTo(1);
        assertThat(count("select count(*) from lead_stage_history where lead_id = ?", id)).isEqualTo(2);
        assertThat(count("select count(*) from audit_logs where action = 'LEAD_DELETED' and entity_id = ?", id)).isEqualTo(1);
        // Partner lead counts leave deleted leads out.
        mvc.perform(get("/api/v1/partners/{id}/performance", partnerA).with(bearer(sa)))
                .andExpect(jsonPath("$.data.leadCount").value(0));
    }

    @Test
    void leadsWithDocumentsCannotBeDeleted() throws Exception {
        UUID id = create(lead(partnerA));
        estimate(id, LocalDate.now(AppConstants.BUSINESS_ZONE));
        mvc.perform(archive("/api/v1/leads/{id}", id).with(bearer(sa)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LEAD_HAS_DOCUMENTS"))
                .andExpect(jsonPath("$.error.detail").value(org.hamcrest.Matchers.containsString("Mark it Lost instead")));
        assertThat(count("select count(*) from leads where id = ? and deleted_at is null", id)).isEqualTo(1);
    }

    @Test
    void archivingNeedsAReasonOf10To500CharactersAndNothingIsArchivedWithout() throws Exception {
        UUID id = create(lead(partnerA, "clientName", "Needs reason " + tag));
        mvc.perform(delete("/api/v1/leads/{id}", id).with(bearer(sa)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("reason"));
        for (String bad : new String[]{"", "          ", "too short", "x".repeat(501)}) {
            mvc.perform(archive("/api/v1/leads/{id}", bad, id).with(bearer(sa)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fieldErrors[0].field").value("reason"));
        }
        assertThat(count("select count(*) from leads where id = ? and deleted_at is null", id)).isEqualTo(1);
        mvc.perform(archive("/api/v1/leads/{id}", "x".repeat(500), id).with(bearer(sa))).andExpect(status().isNoContent());
    }

    @Test
    void anArchivedLeadLeavesTheListsShowsInTheArchiveIsReadOnlyAndCanBeRestored() throws Exception {
        UUID id = create(lead(partnerA, "clientName", "Gone " + tag));
        mvc.perform(archive("/api/v1/leads/{id}", "  Duplicate of another lead  ", id).with(bearer(sa)))
                .andExpect(status().isNoContent());

        assertThat(JsonPath.<List<String>>read(search("q", tag), "$.data[*].id")).doesNotContain(id.toString());
        String archived = mvc.perform(get("/api/v1/leads/archive").param("q", tag).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data[0].clientName").value("Gone " + tag))
                .andExpect(jsonPath("$.data[0].deleteReason").value("Duplicate of another lead"))
                .andExpect(jsonPath("$.data[0].deletedByName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].deletedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(archived).doesNotContain("\"deletedBy\"");
        mvc.perform(get("/api/v1/leads/archive/{id}", id).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lead.clientName").value("Gone " + tag))
                .andExpect(jsonPath("$.data.deleteReason").value("Duplicate of another lead"))
                .andExpect(jsonPath("$.data.deletedByName").isNotEmpty());
        // Search in the archive works like the list; other text finds nothing.
        mvc.perform(get("/api/v1/leads/archive").param("q", "nothing-" + tag).with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(0));
        // Archive reads need Leads read.
        Session blind = login(createUser(RoleCode.USER));
        mvc.perform(get("/api/v1/leads/archive").with(bearer(blind))).andExpect(status().isForbidden());

        // Read-only: no edits, stage changes, acknowledgement, or live reads.
        mvc.perform(withJson(put("/api/v1/leads/{id}", id).with(bearer(sa)), body(lead(partnerA, "version", 0))))
                .andExpect(status().isNotFound());
        stage(sa, id, "PRESALES", null, 0).andExpect(status().isNotFound());
        acknowledge(id).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/leads/{id}", id).with(bearer(sa))).andExpect(status().isNotFound());

        // The reason is in the audit trail and the lead history.
        assertThat(jdbc.queryForObject("select new_values::text from audit_logs where action = 'LEAD_DELETED' "
                + "and entity_id = ?", String.class, id)).contains("Duplicate of another lead");
        assertThat(jdbc.queryForObject("select reason from lead_stage_history where lead_id = ? and event = 'ARCHIVED'",
                String.class, id)).isEqualTo("Duplicate of another lead");

        // Restoring needs Leads approve or delete.
        mvc.perform(post("/api/v1/leads/{id}/restore", id).with(bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/leads/{id}/restore", id).with(bearer(sa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
        assertThat(JsonPath.<List<String>>read(search("q", tag), "$.data[*].id")).contains(id.toString());
        mvc.perform(get("/api/v1/leads/archive").param("q", tag).with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(0));
        assertThat(count("select count(*) from leads where id = ? and deleted_at is null and delete_reason is null "
                + "and deleted_by is null", id)).isEqualTo(1);
        assertThat(count("select count(*) from audit_logs where action = 'LEAD_RESTORED' and entity_id = ?", id)).isEqualTo(1);
        assertThat(count("select count(*) from lead_stage_history where lead_id = ? and event = 'RESTORED'", id)).isEqualTo(1);
        mvc.perform(post("/api/v1/leads/{id}/restore", id).with(bearer(sa))).andExpect(status().isNotFound());
        // Archive it again: a second reason, a second archive entry.
        mvc.perform(archive("/api/v1/leads/{id}", "Archived a second time", id).with(bearer(sa)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/leads/archive/{id}", id).with(bearer(sa)))
                .andExpect(jsonPath("$.data.deleteReason").value("Archived a second time"));
    }

    @Test
    void theDatabaseRefusesAnArchivedRowWithoutAReasonAndOldRowsWereBackfilled() throws Exception {
        UUID id = create(lead(partnerA, "clientName", "Raw " + tag));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                        "update leads set deleted_at = now() where id = ?", id))
                .hasMessageContaining("ck_leads_delete_reason");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                        "update leads set deleted_at = now(), delete_reason = '   ' where id = ?", id))
                .hasMessageContaining("ck_leads_delete_reason");
        assertThat(count("select count(*) from leads where deleted_at is not null and "
                + "(delete_reason is null or btrim(delete_reason) = '')")).isZero();
    }

    // ------------------------------------------------------------------------------------------------ SLA (§4.1)

    @Test
    void slaFlagsAndTheBreachFilterAgree() throws Exception {
        Instant now = Instant.now();
        UUID late = create(lead(partnerA, "clientName", "Late " + tag));
        UUID onTime = create(lead(partnerA, "clientName", "OnTime " + tag));
        UUID estimateLate = create(lead(partnerA, "clientName", "EstLate " + tag));
        UUID estimateOk = create(lead(partnerA, "clientName", "EstOk " + tag));
        createdAt(late, now.minus(Duration.ofHours(49)));
        createdAt(onTime, now.minus(Duration.ofHours(47)));

        // Acknowledged 7 days ago, within the 48 hours; one estimate on day 3 (and a later one), one without.
        for (UUID id : List.of(estimateLate, estimateOk)) {
            acknowledge(id).andExpect(status().isOk());
            Instant ackAt = now.minus(Duration.ofDays(7));
            jdbc.update("update leads set created_at = ?, acknowledged_at = ? where id = ?",
                    Timestamp.from(ackAt.minus(Duration.ofHours(1))), Timestamp.from(ackAt), id);
        }
        LocalDate ackDate = LocalDate.ofInstant(now.minus(Duration.ofDays(7)), AppConstants.BUSINESS_ZONE);
        estimate(estimateOk, ackDate.plusDays(9));
        estimate(estimateOk, ackDate.plusDays(3));

        String all = search("q", tag);
        assertThat(flag(all, late, "ackBreach")).isTrue();
        assertThat(flag(all, onTime, "ackBreach")).isFalse();
        assertThat(flag(all, estimateLate, "estimateBreach")).isTrue();
        assertThat(flag(all, estimateOk, "estimateBreach")).isFalse();
        assertThat(JsonPath.<List<Integer>>read(all, "$.data[?(@.id == '" + estimateOk + "')].sla.estimateDays")).containsExactly(3);
        assertThat(JsonPath.<List<Integer>>read(all, "$.data[?(@.id == '" + late + "')].sla.ackHours")).containsExactly(49);

        List<String> breached = JsonPath.read(search("q", tag, "slaBreach", "true"), "$.data[*].id");
        assertThat(breached).containsExactlyInAnyOrder(late.toString(), estimateLate.toString());
        List<String> fine = JsonPath.read(search("q", tag, "slaBreach", "false"), "$.data[*].id");
        assertThat(fine).containsExactlyInAnyOrder(onTime.toString(), estimateOk.toString());
    }

    @Test
    void closedUnacknowledgedLeadsAreNeverLate() throws Exception {
        UUID id = create(lead(partnerA, "stage", "LOST"));
        createdAt(id, Instant.now().minus(Duration.ofDays(10)));
        mvc.perform(get("/api/v1/leads/{id}", id).with(bearer(admin)))
                .andExpect(jsonPath("$.data.sla.ackHours").doesNotExist())
                .andExpect(jsonPath("$.data.sla.ackBreach").value(false));
        assertThat(JsonPath.<List<String>>read(search("q", tag, "slaBreach", "true"), "$.data[*].id")).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------ search, permissions

    @Test
    void searchCoversClientCityAndPartnerNewestFirst() throws Exception {
        UUID first = create(lead(partnerA, "clientName", "Mehta " + tag, "city", "Nagpur"));
        UUID second = create(lead(partnerB, "clientName", "Iyer " + tag, "city", "Chennai", "stage", "PRESALES"));
        createdAt(first, Instant.now().minus(Duration.ofHours(2)));

        assertIds(search("q", tag), second, first);
        assertIds(search("q", "nagpur"), first);
        assertIds(search("q", "beta " + tag), second);
        assertIds(search("q", tag, "stage", "PRESALES"), second);
        assertIds(search("q", tag, "partnerId", partnerA.toString()), first);
        assertIds(search("q", tag, "sort", "clientName,asc"), second, first);
    }

    @Test
    void permissionsAndScopeAreEnforced() throws Exception {
        UUID id = create(lead(partnerA));
        Session user = login(createUser(RoleCode.USER));
        mvc.perform(get("/api/v1/leads").with(bearer(user))).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/v1/leads").with(bearer(user)), body(lead(partnerA)))).andExpect(status().isForbidden());

        TestUser scoped = createUser(RoleCode.ADMIN);
        grant(scoped, List.of(Map.of("moduleCode", "LEADS", "read", true, "write", true, "dataScope", "SELF")));
        Session scopedSession = login(scoped);
        mvc.perform(get("/api/v1/leads").param("q", tag).with(bearer(scopedSession)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/leads/{id}", id).with(bearer(scopedSession))).andExpect(status().isNotFound());
        stage(scopedSession, id, "PRESALES", null, 0).andExpect(status().isNotFound());

        // An Admin granted Approve can acknowledge; Delete needs its own grant.
        TestUser approver = createUser(RoleCode.ADMIN);
        grant(approver, List.of(Map.of("moduleCode", "LEADS", "read", true, "write", true, "approve", true)));
        Session approverSession = login(approver);
        mvc.perform(post("/api/v1/leads/{id}/acknowledge", id).with(bearer(approverSession))).andExpect(status().isOk());
        mvc.perform(archive("/api/v1/leads/{id}", id).with(bearer(approverSession))).andExpect(status().isForbidden());
    }

    @Test
    void metaListsStagesTypesPartnersAndTargets() throws Exception {
        mvc.perform(get("/api/v1/leads/meta").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stages.length()").value(8))
                .andExpect(jsonPath("$.data.stages[3].label").value("Site visit"))
                .andExpect(jsonPath("$.data.projectTypes[4].label").value("Retrofit / renovation"))
                .andExpect(jsonPath("$.data.partners[?(@.name == 'Alpha " + tag + "')].feePercent").value(8.0))
                .andExpect(jsonPath("$.data.ackTargetHours").value(48))
                .andExpect(jsonPath("$.data.estimateTargetDays").value(5))
                .andExpect(jsonPath("$.data.protectionDays").value(90));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** A valid request for {@code partnerId}; key/value overrides replace or add fields. */
    private Map<String, Object> lead(UUID partnerId, Object... overrides) {
        Map<String, Object> map = new HashMap<>();
        map.put("clientName", "Client " + tag);
        map.put("partnerId", partnerId);
        map.put("projectType", "VILLA");
        for (int i = 0; i + 1 < overrides.length; i += 2) {
            map.put((String) overrides[i], overrides[i + 1]);
        }
        return map;
    }

    private UUID create(Map<String, Object> request) throws Exception {
        String body = mvc.perform(withJson(post("/api/v1/leads").with(bearer(admin)), body(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private UUID partner(String name) throws Exception {
        String body = mvc.perform(withJson(post("/api/v1/partners").with(bearer(sa)),
                        body(Map.of("name", name, "category", "INTERIOR_DESIGNER"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private ResultActions acknowledge(UUID id) throws Exception {
        return mvc.perform(post("/api/v1/leads/{id}/acknowledge", id).with(bearer(sa)));
    }

    private ResultActions stage(Session session, UUID id, String stage, String reason, long version) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("stage", stage);
        request.put("reason", reason);
        request.put("version", version);
        return mvc.perform(withJson(patch("/api/v1/leads/{id}/stage", id).with(bearer(session)), body(request)));
    }

    private void grant(TestUser user, List<Map<String, Object>> modules) throws Exception {
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", user.id()).with(bearer(sa)),
                        body(Map.of("modules", modules, "actions", List.of()))))
                .andExpect(status().isOk());
    }

    private String search(String... params) throws Exception {
        var request = get("/api/v1/leads").param("size", "100").with(bearer(admin));
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static void assertIds(String response, UUID... expected) {
        List<String> ids = JsonPath.read(response, "$.data[*].id");
        assertThat(ids).containsExactly(java.util.Arrays.stream(expected).map(UUID::toString).toArray(String[]::new));
    }

    private static boolean flag(String response, UUID id, String name) {
        List<Boolean> values = JsonPath.read(response, "$.data[?(@.id == '" + id + "')].sla." + name);
        assertThat(values).hasSize(1);
        return values.get(0);
    }

    private void createdAt(UUID id, Instant at) {
        jdbc.update("update leads set created_at = ? where id = ?", Timestamp.from(at), id);
    }

    /** Minimal estimate pointing at the lead (estimates arrive in Phase 8). */
    private void estimate(UUID leadId, LocalDate date) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into estimates (id, estimate_number, partner_id, lead_id, estimate_date)"
                        + " select ?, ?, partner_id, id, ? from leads where id = ?",
                id, "TST-" + id.toString().substring(0, 12), date, leadId);
    }
}
