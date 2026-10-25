package com.iotiq.partnererp.integration.partners;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partner master over HTTP (BR-P1…P13): defaults, validation, permissions, masking of PAN/bank, audit,
 * optimistic locking, search, paid revenue window and tier review.
 */
class PartnerApiIT extends ApiIntegrationTest {

    private static final String PAN = "ABCDE1234F";
    private static final String ACCOUNT = "123456789012";
    /** A real-shaped GSTIN: state 36 + {@link #PAN} + entity "1" + "Z" + check "5". */
    private static final String GSTIN = "36" + PAN + "1Z5";

    private Session sa;
    private Session admin;
    /** Unique per test, so searches and bulk reviews only see this test's partners. */
    private String tag;

    @BeforeEach
    void sessions() throws Exception {
        sa = login(createUser(RoleCode.SUPER_ADMIN));
        admin = login(createUser(RoleCode.ADMIN));
        tag = "T" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ------------------------------------------------------------------------------------------------ CRUD

    @Test
    void createAppliesSourceDefaultsAndNormalisesInput() throws Exception {
        String body = mvc.perform(withJson(post("/api/v1/partners").with(bearer(admin)), body(partner(
                        "name", "  Asha   Interiors " + tag, "email", "ASHA@Example.Test", "pan", "abcde1234f",
                        "bankAccount", "1234 5678 9012", "ifsc", "hdfc0001234", "gstin", GSTIN.toLowerCase()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Asha Interiors " + tag))
                .andExpect(jsonPath("$.data.status").value("PROSPECT"))
                .andExpect(jsonPath("$.data.statusLabel").value("Prospect"))
                .andExpect(jsonPath("$.data.categoryLabel").value("Interior designer"))
                .andExpect(jsonPath("$.data.agreementStatus").value("NOT_SENT"))
                .andExpect(jsonPath("$.data.agreementVersion").value("v2.1"))
                .andExpect(jsonPath("$.data.tier.level").value(1))
                .andExpect(jsonPath("$.data.tier.name").value("Registered"))
                .andExpect(jsonPath("$.data.calculatedTier.level").value(1))
                .andExpect(jsonPath("$.data.email").value("asha@example.test"))
                .andExpect(jsonPath("$.data.ifsc").value("HDFC0001234"))
                // An Admin without VIEW_PARTNER_SENSITIVE gets masked values (BR-P9).
                .andExpect(jsonPath("$.data.sensitiveVisible").value(false))
                .andExpect(jsonPath("$.data.pan").value("••234F"))
                .andExpect(jsonPath("$.data.panOnFile").value(true))
                .andExpect(jsonPath("$.data.bankAccount").value("••9012"))
                .andExpect(jsonPath("$.data.gstin").value("••1Z5"))
                .andExpect(jsonPath("$.data.gstinOnFile").value(true))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(PAN, ACCOUNT, GSTIN);
        UUID id = UUID.fromString(JsonPath.read(body, "$.data.id"));

        // The Super Admin holds the sensitive action (computed) and sees the full values.
        mvc.perform(get("/api/v1/partners/{id}", id).with(bearer(sa)))
                .andExpect(jsonPath("$.data.sensitiveVisible").value(true))
                .andExpect(jsonPath("$.data.pan").value(PAN))
                .andExpect(jsonPath("$.data.gstin").value(GSTIN))
                .andExpect(jsonPath("$.data.bankAccount").value(ACCOUNT));

        // Audit: created, with masked sensitive values only.
        assertThat(count("select count(*) from audit_logs where action = 'PARTNER_CREATED' and entity_id = ?", id)).isOne();
        assertThat(count("select count(*) from audit_logs where entity_id = ? and (new_values::text like ? or new_values::text like ?)",
                id, "%" + PAN + "%", "%" + ACCOUNT + "%")).isZero();
    }

    /**
     * P5-02 regression: the GSTIN embeds the PAN (characters 3–12). Without VIEW_PARTNER_SENSITIVE no response,
     * audit row or error may expose more of the PAN than its own mask (last 4) does.
     */
    @Test
    void maskedGstinCannotBeUsedToReconstructThePan() throws Exception {
        UUID id = create(sa, partner("name", "Gstin " + tag, "pan", PAN, "gstin", GSTIN, "bankAccount", ACCOUNT));
        String hiddenPanPart = PAN.substring(0, PAN.length() - 4); // "ABCDE1": never shown without the action

        String detail = mvc.perform(get("/api/v1/partners/{id}", id).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pan").value("••234F"))
                .andExpect(jsonPath("$.data.gstin").value("••1Z5"))
                .andReturn().getResponse().getContentAsString();
        String gstinShown = JsonPath.read(detail, "$.data.gstin");
        String panShown = JsonPath.read(detail, "$.data.pan");
        // The GSTIN mask shows only the 3 characters after the embedded PAN, so together the two masks reveal
        // nothing beyond the PAN's last 4.
        assertThat(gstinShown.replace("••", "")).isEqualTo(GSTIN.substring(12));
        assertThat((panShown + gstinShown).replace("••", "")).doesNotContain(PAN.substring(0, 6));
        assertThat(detail).doesNotContain(PAN, GSTIN, hiddenPanPart, GSTIN.substring(0, 7));

        // List and search: no GSTIN at all, and searching by PAN or GSTIN finds nothing (not searchable).
        String list = mvc.perform(get("/api/v1/partners").param("q", tag).with(bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("gstin", hiddenPanPart);
        mvc.perform(get("/api/v1/partners").param("q", GSTIN).with(bearer(sa)))
                .andExpect(jsonPath("$.data.length()").value(0));

        // Sending the masked value back is rejected without echoing it, and nothing changes.
        String rejected = mvc.perform(withJson(put("/api/v1/partners/{id}", id).with(bearer(admin)),
                        body(partner("name", "Gstin " + tag, "gstin", gstinShown, "version", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("gstin"))
                .andReturn().getResponse().getContentAsString();
        assertThat(rejected).doesNotContain(gstinShown, GSTIN, hiddenPanPart);

        // null keeps the stored GSTIN; the update response is masked too.
        String kept = mvc.perform(withJson(put("/api/v1/partners/{id}", id).with(bearer(admin)),
                        body(partner("name", "Gstin " + tag, "city", "Pune", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gstinOnFile").value(true))
                .andExpect(jsonPath("$.data.gstin").value("••1Z5"))
                .andReturn().getResponse().getContentAsString();
        assertThat(kept).doesNotContain(hiddenPanPart);
        assertThat(jdbc.queryForObject("select gstin from partners where id = ?", String.class, id)).isEqualTo(GSTIN);

        // A new GSTIN whose last 3 characters match is still detected, audited and masked.
        String other = "29PQRST6789Z1Z5";
        mvc.perform(withJson(put("/api/v1/partners/{id}", id).with(bearer(admin)),
                        body(partner("name", "Gstin " + tag, "city", "Pune", "gstin", other, "version", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.gstin").value("••1Z5"));
        assertThat(count("select count(*) from audit_logs where action = 'PARTNER_UPDATED' and entity_id = ?"
                + " and new_values ->> 'gstin' = '••1Z5' and old_values ->> 'gstin' = '••1Z5'", id)).isOne();

        // "" removes it.
        mvc.perform(withJson(put("/api/v1/partners/{id}", id).with(bearer(admin)),
                        body(partner("name", "Gstin " + tag, "city", "Pune", "gstin", "", "version", 2))))
                .andExpect(jsonPath("$.data.gstinOnFile").value(false))
                .andExpect(jsonPath("$.data.gstin").doesNotExist());

        // No audit row holds a full PAN, GSTIN or the hidden part of either PAN.
        assertThat(count("select count(*) from audit_logs where entity_id = ? and (coalesce(old_values::text, '') like ?"
                        + " or coalesce(new_values::text, '') like ? or coalesce(new_values::text, '') like ?"
                        + " or coalesce(old_values::text, '') like ?)",
                id, "%" + hiddenPanPart + "%", "%" + hiddenPanPart + "%", "%PQRST6%", "%PQRST6%")).isZero();
    }

    @Test
    void sensitiveValuesAreVisibleOnlyWithTheGrantedAction() throws Exception {
        UUID id = create(sa, partner("name", "Sensitive " + tag, "pan", PAN, "bankAccount", ACCOUNT));
        TestUser viewer = createUser(RoleCode.ADMIN);
        grant(viewer, List.of(Map.of("moduleCode", "PARTNERS", "read", true)), List.of("VIEW_PARTNER_SENSITIVE"));
        mvc.perform(get("/api/v1/partners/{id}", id).with(bearer(login(viewer))))
                .andExpect(jsonPath("$.data.pan").value(PAN))
                .andExpect(jsonPath("$.data.bankAccount").value(ACCOUNT));
        // The list never carries PAN or bank details at all.
        String list = mvc.perform(get("/api/v1/partners").param("q", tag).with(bearer(sa)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(PAN, ACCOUNT, "\"pan\"", "bankAccount");
    }

    @Test
    void validationReportsEveryInvalidField() throws Exception {
        mvc.perform(withJson(post("/api/v1/partners").with(bearer(admin)), body(Map.of(
                        "name", "   ", "pan", "ABC123", "ifsc", "HDFC1234567", "bankAccount", "12AB",
                        "email", "not-an-email", "phone", "12", "tierLevel", 5,
                        "agreementDate", LocalDate.now(AppConstants.BUSINESS_ZONE).plusDays(1).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'name')].message").value("Name is required."))
                .andExpect(jsonPath("$.error.fieldErrors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "name", "category", "pan", "ifsc", "bankAccount", "email", "phone", "tierLevel", "agreementDate")));
    }

    @Test
    void updateKeepsRemovesOrReplacesSensitiveFieldsAndChecksTheVersion() throws Exception {
        UUID id = create(sa, partner("name", "Update " + tag, "pan", PAN, "bankAccount", ACCOUNT));

        // Stale version → 409.
        mvc.perform(withJson(put("/api/v1/partners/{id}", id).with(bearer(admin)),
                        body(partner("name", "Update " + tag, "version", 7))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));

        // null PAN/bank (omitted) keeps them; other fields are replaced.
        mvc.perform(withJson(put("/api/v1/partners/{id}", id).with(bearer(admin)),
                        body(partner("name", "Updated " + tag, "city", "Hyderabad", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated " + tag))
                .andExpect(jsonPath("$.data.panOnFile").value(true))
                .andExpect(jsonPath("$.data.bankAccountOnFile").value(true))
                .andExpect(jsonPath("$.data.version").value(1));

        // "" removes the bank account; a new PAN replaces the old one.
        mvc.perform(withJson(put("/api/v1/partners/{id}", id).with(bearer(admin)),
                        body(partner("name", "Updated " + tag, "city", "Hyderabad", "pan", "PQRST6789Z", "bankAccount", "", "version", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pan").value("••789Z"))
                .andExpect(jsonPath("$.data.bankAccountOnFile").value(false));

        // Audit holds only changed keys, masked.
        assertThat(count("select count(*) from audit_logs where action = 'PARTNER_UPDATED' and entity_id = ?", id)).isEqualTo(2);
        assertThat(count("select count(*) from audit_logs where entity_id = ? and (old_values::text like ? or new_values::text like ?"
                + " or new_values::text like '%PQRST6789Z%')", id, "%" + PAN + "%", "%" + ACCOUNT + "%")).isZero();
        assertThat(count("select count(*) from audit_logs where action = 'PARTNER_UPDATED' and entity_id = ?"
                + " and (new_values -> 'city') is not null and (new_values -> 'category') is null", id)).isOne();
    }

    @Test
    void statusChangesAreVersionedAndAudited() throws Exception {
        UUID id = create(admin, partner("name", "Status " + tag));
        mvc.perform(withJson(patch("/api/v1/partners/{id}/status", id).with(bearer(admin)),
                        body(Map.of("status", "ACTIVE", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(withJson(patch("/api/v1/partners/{id}/status", id).with(bearer(admin)),
                        body(Map.of("status", "INACTIVE", "version", 0))))
                .andExpect(status().isConflict());
        assertThat(count("select count(*) from audit_logs where action = 'PARTNER_STATUS_CHANGED' and entity_id = ?"
                + " and old_values ->> 'status' = 'PROSPECT' and new_values ->> 'status' = 'ACTIVE'", id)).isOne();
    }

    @Test
    void partnersCannotBeDeleted() throws Exception {
        UUID id = create(sa, partner("name", "Keep " + tag));
        mvc.perform(delete("/api/v1/partners/{id}", id).with(bearer(sa))).andExpect(status().isMethodNotAllowed());
    }

    // ------------------------------------------------------------------------------------------------ permissions

    @Test
    void moduleAndApprovePermissionsAreEnforced() throws Exception {
        Session user = login(createUser(RoleCode.USER));
        mvc.perform(get("/api/v1/partners").with(bearer(user))).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/v1/partners").with(bearer(user)), body(partner("name", "No " + tag))))
                .andExpect(status().isForbidden());

        // Admin template: Partners read/write, no approve → may not set a tier or the programme.
        mvc.perform(withJson(post("/api/v1/partners").with(bearer(admin)), body(partner("name", "Tier " + tag, "tierLevel", 3))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TIER_CHANGE_NOT_PERMITTED"));
        mvc.perform(withJson(post("/api/v1/partners").with(bearer(admin)),
                        body(partner("name", "Programme " + tag, "strategicProgramme", true))))
                .andExpect(status().isForbidden());
        UUID id = create(admin, partner("name", "Plain " + tag));
        mvc.perform(withJson(put("/api/v1/partners/{id}", id).with(bearer(admin)),
                        body(partner("name", "Plain " + tag, "tierLevel", 2, "version", 0))))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/v1/partners/tier-review/preview").with(bearer(admin)), "{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theProgrammeForcesStrategicOnSave() throws Exception {
        mvc.perform(withJson(post("/api/v1/partners").with(bearer(sa)),
                        body(partner("name", "Builder " + tag, "category", "BUILDER", "tierLevel", 2, "strategicProgramme", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tier.level").value(4))
                .andExpect(jsonPath("$.data.calculatedTier.level").value(4));
    }

    @Test
    void dataScopeSelfHidesPartnersUntilOwnershipExists() throws Exception {
        UUID id = create(sa, partner("name", "Scoped " + tag));
        TestUser scoped = createUser(RoleCode.ADMIN);
        grant(scoped, List.of(Map.of("moduleCode", "PARTNERS", "read", true, "write", true, "dataScope", "SELF")), List.of());
        Session session = login(scoped);
        mvc.perform(get("/api/v1/partners").param("q", tag).with(bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/partners/{id}", id).with(bearer(session))).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------------------ search

    @Test
    void searchCoversNameCityCategoryAndContactAndFiltersCombine() throws Exception {
        UUID architect = create(admin, partner("name", "Alpha " + tag, "category", "ARCHITECT", "city", "Pune"));
        UUID designer = create(admin, partner("name", "Beta " + tag, "contactName", "Ravi " + tag, "city", "Hyderabad"));
        mvc.perform(withJson(patch("/api/v1/partners/{id}/status", designer).with(bearer(admin)),
                body(Map.of("status", "ACTIVE", "version", 0)))).andExpect(status().isOk());

        assertIds(search("q", tag), architect, designer);                   // name, sorted by name
        assertIds(search("q", "ravi " + tag.toLowerCase()), designer);     // contact, case-insensitive
        assertIds(search("q", tag, "status", "ACTIVE"), designer);
        assertIds(search("q", tag, "category", "ARCHITECT"), architect);
        assertIds(search("q", tag, "tier", "1", "agreementStatus", "NOT_SENT"), architect, designer);
        // Category is searchable by its label; city too.
        mvc.perform(get("/api/v1/partners").param("q", "architect").param("size", "100").with(bearer(admin)))
                .andExpect(jsonPath("$.data[*].id").value(hasItem(architect.toString())))
                .andExpect(jsonPath("$.data[*].id").value(not(hasItem(designer.toString()))));
        mvc.perform(get("/api/v1/partners").param("q", tag).param("sort", "name,desc").with(bearer(admin)))
                .andExpect(jsonPath("$.data[0].id").value(designer.toString()));
        mvc.perform(get("/api/v1/partners").param("sort", "pan").with(bearer(admin))).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------------------------------------ revenue

    @Test
    void paidRevenueUsesNetPaymentsInTheTrailing365DaysIncludingRefundsAndFutureDates() throws Exception {
        UUID id = create(sa, partner("name", "Revenue " + tag));
        LocalDate today = LocalDate.now(AppConstants.BUSINESS_ZONE);
        UUID invoice = invoice(id);
        payment(id, invoice, today.minusDays(10), "708000.00", "600000.00");
        payment(id, invoice, today.minusDays(5), "-118000.00", "-100000.00");   // refund reduces revenue
        payment(id, invoice, today.minusDays(365), "118000.00", "100000.00");   // first day of the window counts
        payment(id, invoice, today.minusDays(366), "5900000.00", "5000000.00"); // outside the window
        payment(id, invoice, today.plusDays(3), "590000.00", "500000.00");      // future-dated still counts

        BigDecimal expected = new BigDecimal("1100000.00");
        String detail = mvc.perform(get("/api/v1/partners/{id}/performance", id).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calculatedTier.level").value(2))
                .andExpect(jsonPath("$.data.tier.level").value(1))
                .andExpect(jsonPath("$.data.nextTier.level").value(2))
                .andExpect(jsonPath("$.data.progressPercent").value(100))
                .andExpect(jsonPath("$.data.revenueSince").value(today.minusDays(365).toString()))
                .andExpect(jsonPath("$.data.payoutReady").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(detail, "$.data.revenue12m")).isEqualByComparingTo(expected);
        assertThat(decimal(detail, "$.data.remainingToNextTier")).isEqualByComparingTo("0");

        String list = mvc.perform(get("/api/v1/partners").param("q", "Revenue " + tag).with(bearer(admin)))
                .andExpect(jsonPath("$.data[0].calculatedTier.level").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(list, "$.data[0].revenue12m")).isEqualByComparingTo(expected);
    }

    // ------------------------------------------------------------------------------------------------ tier review

    @Test
    void tierReviewPreviewsThenAppliesAndRecordsHistory() throws Exception {
        LocalDate today = LocalDate.now(AppConstants.BUSINESS_ZONE);
        UUID climber = create(sa, partner("name", "Climber " + tag));
        payment(climber, invoice(climber), today.minusDays(1), "4130000.00", "3500000.00"); // → Elite (3)
        UUID faller = create(sa, partner("name", "Faller " + tag, "tierLevel", 3));         // no revenue
        UUID programme = create(sa, partner("name", "Programme " + tag, "strategicProgramme", true));
        UUID inactive = create(sa, partner("name", "Inactive " + tag, "tierLevel", 2, "status", "INACTIVE"));

        // Bulk preview covers non-Inactive partners only.
        String bulk = mvc.perform(withJson(post("/api/v1/partners/tier-review/preview").with(bearer(sa)), "{}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> bulkIds = JsonPath.read(bulk, "$.data.changes[*].partnerId");
        assertThat(bulkIds).contains(climber.toString(), faller.toString())
                .doesNotContain(inactive.toString(), programme.toString());

        String preview = mvc.perform(withJson(post("/api/v1/partners/tier-review/preview").with(bearer(sa)),
                        body(Map.of("partnerIds", List.of(climber, faller, programme)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewed").value(3))
                .andExpect(jsonPath("$.data.changes.length()").value(2))
                .andExpect(jsonPath("$.data.changes[?(@.partnerName == 'Climber " + tag + "')].action").value("UPGRADE"))
                .andExpect(jsonPath("$.data.changes[?(@.partnerName == 'Climber " + tag + "')].toLevel").value(3))
                .andExpect(jsonPath("$.data.changes[?(@.partnerName == 'Faller " + tag + "')].action").value("GRACE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(preview).doesNotContain(programme.toString());

        apply(List.of(item(climber, "UPGRADE", 3), item(faller, "GRACE", 3))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied.length()").value(2));
        mvc.perform(get("/api/v1/partners/{id}", climber).with(bearer(sa)))
                .andExpect(jsonPath("$.data.tier.level").value(3))
                .andExpect(jsonPath("$.data.lastTierReviewOn").value(today.toString()));
        mvc.perform(get("/api/v1/partners/{id}", faller).with(bearer(sa)))
                .andExpect(jsonPath("$.data.tier.level").value(3))
                .andExpect(jsonPath("$.data.downgradeGrace").value(true));

        // Second review below threshold: a stale confirmation is refused, the current one steps down one tier.
        apply(List.of(item(faller, "GRACE", 3))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TIER_REVIEW_STALE"));
        apply(List.of(item(faller, "DOWNGRADE", 2))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/partners/{id}", faller).with(bearer(sa)))
                .andExpect(jsonPath("$.data.tier.level").value(2))
                .andExpect(jsonPath("$.data.downgradeGrace").value(false));

        mvc.perform(get("/api/v1/partners/{id}/tier-reviews", faller).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].action").value("DOWNGRADE"))
                .andExpect(jsonPath("$.data[0].fromLevel").value(3))
                .andExpect(jsonPath("$.data[0].toLevel").value(2))
                .andExpect(jsonPath("$.data[0].actorName").value("Test SUPER_ADMIN"))
                .andExpect(jsonPath("$.data[1].action").value("GRACE"));
        assertThat(count("select count(*) from audit_logs where action = 'PARTNER_TIER_REVIEWED' and entity_id = ?", faller))
                .isEqualTo(2);
    }

    @Test
    void manualTierChangeEndsTheGracePeriod() throws Exception {
        UUID faller = create(sa, partner("name", "Grace " + tag, "tierLevel", 3)); // no revenue
        apply(List.of(item(faller, "GRACE", 3))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/partners/{id}", faller).with(bearer(sa)))
                .andExpect(jsonPath("$.data.downgradeGrace").value(true));

        // Re-assigning the tier by hand starts afresh: the next review grants a new grace period instead of
        // stepping down at once.
        mvc.perform(withJson(put("/api/v1/partners/{id}", faller).with(bearer(sa)),
                        body(partner("name", "Grace " + tag, "tierLevel", 4, "version", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier.level").value(4))
                .andExpect(jsonPath("$.data.downgradeGrace").value(false));
        mvc.perform(withJson(post("/api/v1/partners/tier-review/preview").with(bearer(sa)),
                        body(Map.of("partnerIds", List.of(faller)))))
                .andExpect(jsonPath("$.data.changes[0].action").value("GRACE"))
                .andExpect(jsonPath("$.data.changes[0].toLevel").value(4));
        assertThat(count("select count(*) from audit_logs where action = 'PARTNER_UPDATED' and entity_id = ?"
                + " and new_values ->> 'downgradeGrace' = 'false' and new_values ->> 'tierLevel' = '4'", faller)).isOne();

        // Edits that leave the tier alone keep an existing grace period.
        apply(List.of(item(faller, "GRACE", 4))).andExpect(status().isOk());
        mvc.perform(withJson(put("/api/v1/partners/{id}", faller).with(bearer(sa)),
                        body(partner("name", "Grace " + tag, "tierLevel", 4, "city", "Pune", "version", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downgradeGrace").value(true));
    }

    @Test
    void tierReviewApplyRejectsUnchangedPartnersAndBadRequests() throws Exception {
        UUID steady = create(sa, partner("name", "Steady " + tag));
        apply(List.of(item(steady, "UPGRADE", 2))).andExpect(status().isConflict());
        mvc.perform(withJson(post("/api/v1/partners/tier-review/apply").with(bearer(sa)), body(Map.of("items", List.of()))))
                .andExpect(status().isBadRequest());
        apply(List.of(item(UUID.randomUUID(), "UPGRADE", 2))).andExpect(status().isNotFound());
    }

    @Test
    void metaListsChoicesWithSourceLabels() throws Exception {
        mvc.perform(get("/api/v1/partners/meta").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories.length()").value(9))
                .andExpect(jsonPath("$.data.categories[4].label").value("Contractor (civil/electrical/MEP)"))
                .andExpect(jsonPath("$.data.tiers.length()").value(4))
                .andExpect(jsonPath("$.data.tiers[3].name").value("Strategic"))
                .andExpect(jsonPath("$.data.defaultAgreementVersion").value("v2.1"));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** A valid request; key/value overrides replace or add fields. */
    private Map<String, Object> partner(Object... overrides) {
        Map<String, Object> map = new HashMap<>();
        map.put("category", "INTERIOR_DESIGNER");
        for (int i = 0; i + 1 < overrides.length; i += 2) {
            map.put((String) overrides[i], overrides[i + 1]);
        }
        return map;
    }

    private UUID create(Session session, Map<String, Object> request) throws Exception {
        String body = mvc.perform(withJson(post("/api/v1/partners").with(bearer(session)), body(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private void grant(TestUser user, List<Map<String, Object>> modules, List<String> actions) throws Exception {
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", user.id()).with(bearer(sa)),
                        body(Map.of("modules", modules, "actions", actions))))
                .andExpect(status().isOk());
    }

    private String search(String... params) throws Exception {
        var request = get("/api/v1/partners").param("size", "100").with(bearer(admin));
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static void assertIds(String response, UUID... expected) {
        List<String> ids = JsonPath.read(response, "$.data[*].id");
        assertThat(ids).containsExactly(java.util.Arrays.stream(expected).map(UUID::toString).toArray(String[]::new));
    }

    private ResultActions apply(List<Map<String, Object>> items) throws Exception {
        return mvc.perform(withJson(post("/api/v1/partners/tier-review/apply").with(bearer(sa)), body(Map.of("items", items))));
    }

    private static Map<String, Object> item(UUID partnerId, String action, int toLevel) {
        return Map.of("partnerId", partnerId, "action", action, "toLevel", toLevel);
    }

    private static BigDecimal decimal(String json, String path) {
        Object value = JsonPath.read(json, path);
        return new BigDecimal(String.valueOf(value));
    }

    /** Minimal open invoice to attach test payments to (invoices arrive in Phase 9). */
    private UUID invoice(UUID partnerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into invoices (id, invoice_number, partner_id, invoice_date) values (?, ?, ?, ?)",
                id, "TST-" + id.toString().substring(0, 12), partnerId, LocalDate.now(AppConstants.BUSINESS_ZONE));
        return id;
    }

    private void payment(UUID partnerId, UUID invoiceId, LocalDate date, String amount, String net) {
        jdbc.update("insert into payments (invoice_id, partner_id, payment_date, amount, net_amount, is_refund, payment_method)"
                        + " values (?, ?, ?, ?, ?, ?, 'NEFT_RTGS')",
                invoiceId, partnerId, date, new BigDecimal(amount), new BigDecimal(net), amount.startsWith("-"));
    }
}
