package com.iotiq.partnererp.integration.estimates;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 8 estimates over HTTP: totals and snapshots, the B2C twin, numbering, status actions with lead side effects,
 * fee rules, permissions (picker, internal copy, data scope) and the separation of partner-safe and internal data.
 */
class EstimateApiIT extends ApiIntegrationTest {

    private static final String B2C_PRICE = "1543.21";

    private Session sa;
    private Session admin;
    private String tag;
    private String secretB2cName;
    private UUID partnerId;
    private UUID leadId;
    private UUID productA;
    private UUID productB;

    @BeforeEach
    void setUp() throws Exception {
        sa = login(createUser(RoleCode.SUPER_ADMIN));
        admin = login(createUser(RoleCode.ADMIN));
        // Digits only, so the tag can never contain "b2c" and trip the partner-safe text checks.
        tag = "E" + String.format("%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000L));
        secretB2cName = "B2C secret " + tag;
        partnerId = partner("Partner " + tag);
        leadId = lead("Client " + tag);
        mvc.perform(post("/api/v1/leads/{id}/acknowledge", leadId).with(bearer(sa))).andExpect(status().isOk());
        productA = product("-A", "1000", B2C_PRICE, secretB2cName, 18);
        productB = product("-B", "250.50", null, "B2C plain " + tag, 5);
    }

    @AfterEach
    void cleanUp() {
        deleteTestProducts(tag + "%");
    }

    // ------------------------------------------------------------------------------------------------ create

    @Test
    void createComputesTotalsFromSnapshotsAndWritesTheTwinInTheSameSave() throws Exception {
        String body = create(admin, estimate(leadId, "discountPercent", 7.5,
                "lines", List.of(line(productA, 2), line(productB, 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.lead.leadNumber").exists())
                .andExpect(jsonPath("$.data.partner.name").value("Partner " + tag))
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].description").value("Partner A " + tag))
                .andExpect(jsonPath("$.data.lines[0].sku").value(tag + "-A"))
                .andExpect(jsonPath("$.data.lines[1].unitPrice").value(250.5))
                .andReturn().getResponse().getContentAsString();
        int year = LocalDate.now(ZoneId.of("Asia/Kolkata")).getYear();
        String number = JsonPath.read(body, "$.data.estimateNumber");
        assertThat(number).matches("EST-" + year + "-\\d{6}");
        assertThat(decimal(body, "$.data.feePercent")).isEqualByComparingTo("8");
        // sub 2751.50; tax 333 + 34.756875; discount 206.3625; taxable 2545.1375; total 2912.894375
        assertThat(decimal(body, "$.data.totals.subtotal")).isEqualByComparingTo("2751.50");
        assertThat(decimal(body, "$.data.totals.discountAmount")).isEqualByComparingTo("206.36");
        assertThat(decimal(body, "$.data.totals.taxableAmount")).isEqualByComparingTo("2545.14");
        assertThat(decimal(body, "$.data.totals.gstAmount")).isEqualByComparingTo("367.76");
        assertThat(decimal(body, "$.data.totals.cgstAmount")).isEqualByComparingTo("183.88");
        assertThat(decimal(body, "$.data.totals.sgstAmount")).isEqualByComparingTo("183.88");
        assertThat(decimal(body, "$.data.totals.grandTotal")).isEqualByComparingTo("2912.89");
        assertThat((String) JsonPath.read(body, "$.data.validUntil"))
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(15).toString());
        assertPartnerSafe(body);

        UUID id = id(body);
        Map<String, Object> twin = jdbc.queryForMap("select * from b2c_documents where estimate_id = ?", id);
        assertThat(twin.get("internal_number")).isEqualTo("INT-" + number);
        assertThat(twin.get("parent_number")).isEqualTo(number);
        assertThat((BigDecimal) twin.get("b2c_subtotal")).isEqualByComparingTo("3086.42");
        assertThat((BigDecimal) twin.get("b2c_taxable_amount")).isEqualByComparingTo("2854.94");
        assertThat((BigDecimal) twin.get("partner_taxable_amount")).isEqualByComparingTo("2545.14");
        assertThat((BigDecimal) twin.get("uplift")).isEqualByComparingTo("309.80");
        assertThat(twin.get("b2c_incomplete")).isEqualTo(true);
        assertThat(jdbc.queryForList("select description from b2c_document_items where b2c_document_id = ? order by line_no",
                String.class, twin.get("id"))).containsExactly(secretB2cName, "B2C plain " + tag);
        assertThat(count("select count(*) from audit_logs where action = 'ESTIMATE_CREATED' and entity_id = ?", id))
                .isEqualTo(1);
    }

    @Test
    void partnerSafeResponsesDocumentsExportsAndAuditEntriesNeverCarryB2cData() throws Exception {
        UUID id = id(create(admin, estimate(leadId, "discountPercent", 7.5,
                "lines", List.of(line(productA, 2), line(productB, 3)))).andReturn());
        mvc.perform(withJson(put("/api/v1/estimates/{id}", id).with(bearer(admin)), body(estimate(leadId,
                "discountPercent", 5, "version", 0, "lines", List.of(line(productA, 1)))))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/estimates/{id}/send", id).with(bearer(admin))).andExpect(status().isOk());

        // Even a Super Admin, who may see everything, gets partner-safe data from these endpoints.
        for (Session session : List.of(admin, sa)) {
            assertPartnerSafe(content(get("/api/v1/estimates/{id}", id), session));
            assertPartnerSafe(content(get("/api/v1/estimates").param("q", tag), session));
            assertPartnerSafe(content(get("/api/v1/estimates/{id}/history", id), session));
            assertPartnerSafe(content(get("/api/v1/estimates/{id}/document", id), session));
        }
        String csv = content(get("/api/v1/estimates/export").param("q", tag), sa);
        assertThat(csv).contains("estimate,date,valid_until");
        assertPartnerSafe(csv);
        for (Map<String, Object> row : jdbc.queryForList(
                "select coalesce(old_values::text, '') || coalesce(new_values::text, '') as v from audit_logs "
                        + "where entity_type = 'ESTIMATE' and entity_id = ?", id)) {
            assertPartnerSafe((String) row.get("v"));
        }
        assertThat(count("select count(*) from audit_logs where entity_type = 'ESTIMATE' and entity_id = ?", id))
                .isEqualTo(3);
    }

    @Test
    void thePreviewShowsB2cFiguresOnlyToHoldersOfTheInternalAction() throws Exception {
        Map<String, Object> request = estimate(leadId, "discountPercent", 7.5,
                "lines", List.of(line(productA, 2), line(productB, 3)));
        String plain = mvc.perform(withJson(post("/api/v1/estimates/preview").with(bearer(admin)), body(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internal").doesNotExist())
                .andExpect(jsonPath("$.data.defaultFeePercent").value(8.0))
                .andExpect(jsonPath("$.data.feeOverride").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(plain, "$.data.totals.grandTotal")).isEqualByComparingTo("2912.89");
        assertPartnerSafe(plain);

        String internal = mvc.perform(withJson(post("/api/v1/estimates/preview").with(bearer(sa)), body(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internal.missingB2cPrices").value(1))
                .andExpect(jsonPath("$.data.internal.lines[0].b2cName").value(secretB2cName))
                .andExpect(jsonPath("$.data.internal.lines[1].b2cPriceMissing").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(internal, "$.data.internal.totals.taxableAmount")).isEqualByComparingTo("2854.94");
        assertThat(decimal(internal, "$.data.internal.uplift")).isEqualByComparingTo("309.80");
        assertThat(count("select count(*) from estimates where lead_id = ?", leadId)).isZero();
    }

    @Test
    void theInternalCopyNeedsTheInternalB2cPermission() throws Exception {
        UUID id = id(create(admin, estimate(leadId, "lines", List.of(line(productA, 2), line(productB, 3)))).andReturn());

        mvc.perform(get("/api/v1/internal/estimates/{id}", id).with(bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/internal/estimates/{id}/document", id).with(bearer(admin)))
                .andExpect(status().isForbidden());

        String number = JsonPath.read(content(get("/api/v1/estimates/{id}", id), sa), "$.data.estimateNumber");
        String twin = mvc.perform(get("/api/v1/internal/estimates/{id}", id).with(bearer(sa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internalNumber").value("INT-" + number))
                .andExpect(jsonPath("$.data.lines[0].b2cName").value(secretB2cName))
                .andExpect(jsonPath("$.data.lines[0].partnerName").value("Partner A " + tag))
                .andExpect(jsonPath("$.data.lines[1].b2cPriceMissing").value(true))
                .andExpect(jsonPath("$.data.b2cIncomplete").value(true))
                .andExpect(jsonPath("$.data.missingB2cPrices").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(twin, "$.data.b2cTotals.subtotal")).isEqualByComparingTo("3086.42");
        assertThat(content(get("/api/v1/internal/estimates/{id}/document", id), sa))
                .contains("INTERNAL ONLY", secretB2cName, "INT-" + number, "Partner document: " + number);

        TestUser reader = createUser(RoleCode.ADMIN);
        grant(reader, List.of(module("ESTIMATES", true), module("INTERNAL_B2C", false)), List.of());
        Session readerSession = login(reader);
        mvc.perform(get("/api/v1/internal/estimates/{id}", id).with(bearer(readerSession))).andExpect(status().isOk());
        // Internal B2C read covers the twin documents; B2C rates in the editor need the products action (P3-06).
        mvc.perform(withJson(post("/api/v1/estimates/preview").with(bearer(readerSession)),
                        body(estimate(leadId, "lines", List.of(line(productA, 1))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internal").doesNotExist());
    }

    // ------------------------------------------------------------------------------------------------ picker

    @Test
    void estimateWritersPickActiveProductsWithoutProductsReadAndWithoutB2cFields() throws Exception {
        TestUser writer = createUser(RoleCode.USER);
        grant(writer, List.of(module("ESTIMATES", true), module("LEADS", false)), List.of());
        Session session = login(writer);

        String options = mvc.perform(get("/api/v1/products/options").param("q", tag).with(bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sku").value(tag + "-A"))
                .andExpect(jsonPath("$.data[0].name").value("Partner A " + tag))
                .andExpect(jsonPath("$.data[0].rate").value(1000.0))
                .andExpect(jsonPath("$.data[0].gstPercent").value(18.0))
                .andReturn().getResponse().getContentAsString();
        assertThat(options.toLowerCase(Locale.ROOT)).doesNotContain("b2c", "cost", "alias");
        assertThat(options).doesNotContain(secretB2cName, B2C_PRICE);
        mvc.perform(get("/api/v1/products").with(bearer(session))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/products/{id}", productA).with(bearer(session))).andExpect(status().isForbidden());

        create(session, estimate(leadId, "lines", List.of(line(productA, 1)))).andExpect(status().isCreated());

        // Deactivated products leave the picker and can't be added.
        setActive(productB, false);
        mvc.perform(get("/api/v1/products/options").param("q", tag).with(bearer(session)))
                .andExpect(jsonPath("$.data.length()").value(1));

        TestUser outsider = createUser(RoleCode.USER);
        grant(outsider, List.of(module("ESTIMATES", false)), List.of());
        mvc.perform(get("/api/v1/products/options").with(bearer(login(outsider)))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------------------ snapshots

    @Test
    void savedLinesKeepTheirSnapshotWhenProductsChangeAndNewLinesCopyTheCurrentProduct() throws Exception {
        String created = create(admin, estimate(leadId, "lines", List.of(line(productA, 1))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = id(created);
        String lineId = JsonPath.read(created, "$.data.lines[0].id");

        Map<String, Object> edit = productBody("-A", "1100", "1600", "Renamed B2C " + tag, 12);
        edit.put("partnerName", "Renamed A " + tag);
        edit.put("version", 0);
        mvc.perform(withJson(put("/api/v1/products/{id}", productA).with(bearer(sa)), body(edit)))
                .andExpect(status().isOk());

        String unchanged = content(get("/api/v1/estimates/{id}", id), admin);
        assertThat((String) JsonPath.read(unchanged, "$.data.lines[0].description")).isEqualTo("Partner A " + tag);
        assertThat(decimal(unchanged, "$.data.lines[0].unitPrice")).isEqualByComparingTo("1000");
        assertThat(decimal(unchanged, "$.data.totals.grandTotal")).isEqualByComparingTo("1180.00");
        assertThat(decimal(unchanged, "$.data.version")).isEqualByComparingTo("0");

        Map<String, Object> kept = line(productA, 2);
        kept.put("id", lineId);
        String updated = mvc.perform(withJson(put("/api/v1/estimates/{id}", id).with(bearer(admin)),
                        body(estimate(leadId, "version", 0, "lines", List.of(kept, line(productA, 1))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[0].description").value("Partner A " + tag))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(1000.0))
                .andExpect(jsonPath("$.data.lines[0].gstPercent").value(18.0))
                .andExpect(jsonPath("$.data.lines[1].description").value("Renamed A " + tag))
                .andExpect(jsonPath("$.data.lines[1].unitPrice").value(1100.0))
                .andExpect(jsonPath("$.data.lines[1].gstPercent").value(12.0))
                .andReturn().getResponse().getContentAsString();
        // 2 × 1000 × 1.18 + 1100 × 1.12 = 2360 + 1232
        assertThat(decimal(updated, "$.data.totals.grandTotal")).isEqualByComparingTo("3592.00");
        assertThat(jdbc.queryForList("select i.unit_price from b2c_document_items i join b2c_documents d "
                        + "on d.id = i.b2c_document_id where d.estimate_id = ? order by i.line_no", BigDecimal.class, id))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal(B2C_PRICE), new BigDecimal("1600"));

        // Saving again without changes leaves the estimate (and its version) alone.
        String again = content(get("/api/v1/estimates/{id}", id), admin);
        List<Map<String, Object>> sameLines = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> l = new HashMap<>();
            l.put("id", JsonPath.read(again, "$.data.lines[" + i + "].id"));
            l.put("productId", productA);
            l.put("quantity", JsonPath.read(again, "$.data.lines[" + i + "].quantity"));
            sameLines.add(l);
        }
        int version = JsonPath.read(again, "$.data.version");
        mvc.perform(withJson(put("/api/v1/estimates/{id}", id).with(bearer(admin)),
                        body(estimate(leadId, "version", version, "lines", sameLines,
                                "validUntil", JsonPath.read(again, "$.data.validUntil"),
                                "notes", JsonPath.read(again, "$.data.notes")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(version));
    }

    @Test
    void aProductUsedOnceCanNeverBeDeletedEvenAfterItsDocumentsAreGone() throws Exception {
        UUID id = id(create(admin, estimate(leadId, "lines", List.of(line(productB, 1)))).andReturn());
        UUID unused = product("-U", "10", null, "B2C unused " + tag, 18);

        mvc.perform(delete("/api/v1/estimates/{id}", id).with(bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/estimates/{id}", id).with(bearer(sa))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/estimates/{id}", id).with(bearer(sa))).andExpect(status().isNotFound());
        assertThat(count("select count(*) from estimates where id = ? and deleted_at is not null", id)).isEqualTo(1);
        assertThat(count("select count(*) from audit_logs where action = 'ESTIMATE_DELETED' and entity_id = ?", id))
                .isEqualTo(1);

        mvc.perform(delete("/api/v1/products/{id}", productB).with(bearer(sa)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_IN_USE"));
        assertThat(count("select count(*) from products where id = ? and first_referenced_at is not null", productB))
                .isEqualTo(1);

        // Even with every line gone, the marker stays and the database refuses the delete.
        jdbc.update("delete from estimates where id = ?", id);
        assertThat(count("select count(*) from b2c_document_items where product_id = ?", productB)).isZero();
        mvc.perform(delete("/api/v1/products/{id}", productB).with(bearer(sa))).andExpect(status().isConflict());
        assertThatThrownBy(() -> jdbc.update("delete from products where id = ?", productB))
                .isInstanceOf(DataIntegrityViolationException.class);

        mvc.perform(delete("/api/v1/products/{id}", unused).with(bearer(sa))).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------------------------------------ numbering

    @Test
    void numbersStayUniqueAndGaplessUnderConcurrentSaves() throws Exception {
        int threads = 8;
        String request = body(estimate(leadId, "lines", List.of(line(productA, 1))));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<String>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<String> call = () -> mvc.perform(withJson(post("/api/v1/estimates").with(bearer(admin)), request))
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
                results.add(pool.submit(call));
            }
            List<Integer> sequence = new ArrayList<>();
            for (Future<String> result : results) {
                String number = JsonPath.read(result.get(), "$.data.estimateNumber");
                sequence.add(Integer.parseInt(number.substring(number.lastIndexOf('-') + 1)));
            }
            assertThat(sequence).doesNotHaveDuplicates().hasSize(threads);
            int min = sequence.stream().min(Integer::compare).orElseThrow();
            assertThat(sequence).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.IntStream.range(min, min + threads).boxed().toList());
        } finally {
            pool.shutdownNow();
        }

        long before = sequenceValue();
        create(admin, estimate(leadId, "lines", List.of(line(productA, 0)))).andExpect(status().isBadRequest());
        assertThat(sequenceValue()).isEqualTo(before);
    }

    @Test
    void aTwinFailureRollsBackTheEstimateAndItsNumber() throws Exception {
        String first = content(post("/api/v1/estimates"), admin, estimate(leadId, "lines", List.of(line(productA, 1))));
        UUID firstId = id(first);
        long last = sequenceValue();
        String next = String.format("EST-%d-%06d", LocalDate.now(ZoneId.of("Asia/Kolkata")).getYear(), last + 1);
        jdbc.update("update b2c_documents set internal_number = ? where estimate_id = ?", "INT-" + next, firstId);
        try {
            create(admin, estimate(leadId, "lines", List.of(line(productA, 1))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("DATA_CONFLICT"));
            assertThat(count("select count(*) from estimates where estimate_number = ?", next)).isZero();
            assertThat(sequenceValue()).isEqualTo(last);
        } finally {
            jdbc.update("update b2c_documents set internal_number = 'INT-' || parent_number where estimate_id = ?", firstId);
        }
        create(admin, estimate(leadId, "lines", List.of(line(productA, 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.estimateNumber").value(next));
    }

    // ------------------------------------------------------------------------------------------------ status

    @Test
    void statusActionsFollowTheSourceAndMoveTheLead() throws Exception {
        UUID id = id(create(admin, estimate(leadId, "lines", List.of(line(productA, 1)))).andReturn());
        assertThat(leadStage(leadId)).isEqualTo("REGISTERED");

        mvc.perform(post("/api/v1/estimates/{id}/accept", id).with(bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
        String sent = mvc.perform(withJson(post("/api/v1/estimates/{id}/send", id).with(bearer(admin)),
                        body(Map.of("version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.sentOn").value(LocalDate.now(ZoneId.of("Asia/Kolkata")).toString()))
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(sent, "$.data.estimateNumber");
        assertThat(leadStage(leadId)).isEqualTo("ESTIMATE_SENT");
        assertThat(jdbc.queryForObject("select reason from lead_stage_history where lead_id = ? and event = 'AUTO_STAGE'",
                String.class, leadId)).isEqualTo("Estimate " + number + " marked sent");
        mvc.perform(post("/api/v1/estimates/{id}/send", id).with(bearer(admin))).andExpect(status().isConflict());

        mvc.perform(post("/api/v1/estimates/{id}/accept", id).with(bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        assertThat(leadStage(leadId)).isEqualTo("WON");
        mvc.perform(post("/api/v1/estimates/{id}/reject", id).with(bearer(admin))).andExpect(status().isConflict());

        // A Won lead stays Won when another estimate is sent or rejected.
        UUID other = id(create(admin, estimate(leadId, "lines", List.of(line(productA, 1)))).andReturn());
        mvc.perform(post("/api/v1/estimates/{id}/send", other).with(bearer(admin))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/estimates/{id}/reject", other).with(bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertThat(leadStage(leadId)).isEqualTo("WON");

        UUID lead2 = lead("Second " + tag);
        UUID rejected = id(create(admin, estimate(lead2, "lines", List.of(line(productA, 1)))).andReturn());
        mvc.perform(post("/api/v1/estimates/{id}/send", rejected).with(bearer(admin))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/estimates/{id}/reject", rejected).with(bearer(admin))).andExpect(status().isOk());
        assertThat(leadStage(lead2)).isEqualTo("LOST");
        assertThat(count("select count(*) from audit_logs where action = 'ESTIMATE_STATUS_CHANGED' and entity_id = ?",
                id)).isEqualTo(2);
    }

    @Test
    void acceptingAnEstimateOfALostLeadNeedsTheLeadsApprovePermission() throws Exception {
        UUID lead = lead("Lost " + tag);
        UUID id = id(create(admin, estimate(lead, "lines", List.of(line(productA, 1)))).andReturn());
        mvc.perform(post("/api/v1/estimates/{id}/send", id).with(bearer(admin))).andExpect(status().isOk());
        mvc.perform(withJson(patch("/api/v1/leads/{id}/stage", lead).with(bearer(admin)),
                body(Map.of("stage", "LOST", "version", leadVersion(lead))))).andExpect(status().isOk());

        mvc.perform(post("/api/v1/estimates/{id}/accept", id).with(bearer(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("STAGE_REOPEN_NOT_PERMITTED"));
        assertThat(jdbc.queryForObject("select status from estimates where id = ?", String.class, id)).isEqualTo("SENT");

        mvc.perform(post("/api/v1/estimates/{id}/accept", id).with(bearer(sa))).andExpect(status().isOk());
        assertThat(leadStage(lead)).isEqualTo("WON");
    }

    // ------------------------------------------------------------------------------------------------ fee

    @Test
    void theReferralFeeDefaultsFromTheLeadAndOverridesNeedPartnersApproveAndAReason() throws Exception {
        create(admin, estimate(leadId, "feePercent", 12, "lines", List.of(line(productA, 1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FEE_OVERRIDE_NOT_PERMITTED"));
        assertThat(count("select count(*) from estimates where lead_id = ?", leadId)).isZero();

        create(admin, estimate(leadId, "feePercent", 8, "lines", List.of(line(productA, 1))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.feePercent").value(8.0));
        create(admin, estimate(leadId, "billTo", "PARTNER", "feePercent", 5, "lines", List.of(line(productA, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("feePercent"));
        create(admin, estimate(leadId, "billTo", "PARTNER", "lines", List.of(line(productA, 1))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.feePercent").value(0.0));

        create(sa, estimate(leadId, "feePercent", 12, "lines", List.of(line(productA, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("feeOverrideReason"));
        TestUser approver = createUser(RoleCode.SUPER_ADMIN);
        Session approverSession = login(approver);
        String body = content(post("/api/v1/estimates"), approverSession, estimate(leadId, "feePercent", 12,
                "feeOverrideReason", "Strategic account " + tag, "lines", List.of(line(productA, 1))));
        UUID id = id(body);
        assertThat(decimal(body, "$.data.feePercent")).isEqualByComparingTo("12");
        // D-13 audit: the default it replaced, the new rate, the reason, who and when.
        Map<String, Object> created = jdbc.queryForMap("select user_id, created_at, new_values::text as nv "
                + "from audit_logs where action = 'ESTIMATE_CREATED' and entity_id = ?", id);
        assertThat(created.get("user_id")).isEqualTo(approver.id());
        assertThat(created.get("created_at")).isNotNull();
        assertThat((String) created.get("nv")).contains("Strategic account " + tag, "\"defaultFeePercent\"",
                "\"feePercent\"");

        // Keeping an approved fee needs nothing more; changing it again does.
        mvc.perform(withJson(put("/api/v1/estimates/{id}", id).with(bearer(admin)), body(estimate(leadId,
                        "feePercent", 12, "version", 0, "lines", List.of(line(productA, 3))))))
                .andExpect(status().isOk());
        mvc.perform(withJson(put("/api/v1/estimates/{id}", id).with(bearer(admin)), body(estimate(leadId,
                        "feePercent", 11, "version", 1, "lines", List.of(line(productA, 3))))))
                .andExpect(status().isForbidden());

        // A changed override records the previous rate, the new rate, the default and the reason.
        mvc.perform(withJson(put("/api/v1/estimates/{id}", id).with(bearer(approverSession)), body(estimate(leadId,
                        "feePercent", 15, "feeOverrideReason", "Volume commitment " + tag, "version", 1,
                        "lines", List.of(line(productA, 3))))))
                .andExpect(status().isOk());
        Map<String, Object> updated = jdbc.queryForMap("select user_id, old_values::text as ov, new_values::text as nv "
                + "from audit_logs where action = 'ESTIMATE_UPDATED' and entity_id = ? order by created_at desc limit 1", id);
        assertThat(updated.get("user_id")).isEqualTo(approver.id());
        assertThat(decimalIn((String) updated.get("ov"), "$.feePercent")).isEqualByComparingTo("12");
        assertThat(decimalIn((String) updated.get("nv"), "$.feePercent")).isEqualByComparingTo("15");
        assertThat(decimalIn((String) updated.get("nv"), "$.defaultFeePercent")).isPositive();
        assertThat((String) updated.get("nv")).contains("Volume commitment " + tag);
    }

    // ------------------------------------------------------------------------------------------------ edit/delete

    @Test
    void sentAcceptedAndRejectedEstimatesCanBeEditedAndDeletedWithoutChangingTheirStatus() throws Exception {
        for (String status : List.of("SENT", "ACCEPTED", "REJECTED")) {
            UUID lead = lead("Status " + status + " " + tag);
            UUID id = id(create(admin, estimate(lead, "lines", List.of(line(productA, 1)))).andReturn());
            mvc.perform(post("/api/v1/estimates/{id}/send", id).with(bearer(admin))).andExpect(status().isOk());
            if (!status.equals("SENT")) {
                mvc.perform(post("/api/v1/estimates/{id}/" + (status.equals("ACCEPTED") ? "accept" : "reject"), id)
                        .with(bearer(admin))).andExpect(status().isOk());
            }
            long version = count("select version from estimates where id = ?", id);
            mvc.perform(withJson(put("/api/v1/estimates/{id}", id).with(bearer(admin)),
                            body(estimate(lead, "version", version, "lines", List.of(line(productA, 4))))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(status))
                    .andExpect(jsonPath("$.data.lines[0].quantity").value(4.0));
            assertThat(jdbc.queryForObject("select new_values::text from audit_logs where action = 'ESTIMATE_UPDATED' "
                    + "and entity_id = ? order by created_at desc limit 1", String.class, id)).contains("\"lines\"")
                    .doesNotContain("\"status\"");

            mvc.perform(delete("/api/v1/estimates/{id}", id).with(bearer(sa))).andExpect(status().isNoContent());
            assertThat(jdbc.queryForObject("select status from estimates where id = ?", String.class, id))
                    .isEqualTo(status);
            assertThat(jdbc.queryForObject("select old_values::text from audit_logs where action = 'ESTIMATE_DELETED' "
                    + "and entity_id = ?", String.class, id)).contains("\"" + status + "\"");
            mvc.perform(get("/api/v1/estimates/{id}", id).with(bearer(admin))).andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------------------------------------ tax type

    @Test
    void theTaxTypeIsNeverAssumedForANewEstimate() throws Exception {
        Map<String, Object> request = estimate(leadId, "lines", List.of(line(productA, 1)));
        request.remove("igst");
        create(admin, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("igst"));
        mvc.perform(withJson(post("/api/v1/estimates/preview").with(bearer(admin)), body(request)))
                .andExpect(status().isOk());

        UUID id = id(create(admin, estimate(leadId, "igst", true, "lines", List.of(line(productA, 1)))).andReturn());
        Map<String, Object> edit = estimate(leadId, "version", 0, "lines", List.of(line(productA, 2)));
        edit.remove("igst");
        mvc.perform(withJson(put("/api/v1/estimates/{id}", id).with(bearer(admin)), body(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.igst").value(true));
    }

    // ------------------------------------------------------------------------------------------------ lead picker

    @Test
    void estimateWritersPickEligibleLeadsWithoutLeadsReadAndSeeOnlyTheMinimumFields() throws Exception {
        String phone = "99" + tag.substring(1);
        UUID detailed = id(content(post("/api/v1/leads"), admin, Map.of("clientName", "Picker " + tag,
                "partnerId", partnerId, "projectType", "VILLA", "clientPhone", phone,
                "clientEmail", "picker." + tag.toLowerCase(Locale.ROOT) + "@example.test", "budget", 987654)));
        UUID lost = lead("Lost pick " + tag);
        mvc.perform(withJson(patch("/api/v1/leads/{id}/stage", lost).with(bearer(admin)),
                body(Map.of("stage", "LOST", "version", 0)))).andExpect(status().isOk());
        UUID gone = lead("Deleted pick " + tag);
        mvc.perform(delete("/api/v1/leads/{id}", gone).with(bearer(sa))).andExpect(status().isNoContent());

        TestUser writer = createUser(RoleCode.USER);
        grant(writer, List.of(module("ESTIMATES", true)), List.of());
        Session session = login(writer);
        mvc.perform(get("/api/v1/leads").with(bearer(session))).andExpect(status().isForbidden());

        String options = mvc.perform(get("/api/v1/estimates/lead-options").param("q", tag).with(bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(options, "$.data[*].clientName");
        assertThat(names).containsExactlyInAnyOrder("Client " + tag, "Picker " + tag);
        Map<String, Object> first = JsonPath.read(options, "$.data[0]");
        assertThat(first.keySet()).containsExactlyInAnyOrder("id", "leadNumber", "clientName", "partner");
        assertThat(options).doesNotContain(phone, "picker." + tag.toLowerCase(Locale.ROOT), "987654", "Hyderabad",
                "stage", "protection", "acknowledged");

        // Protected details aren't searchable either.
        mvc.perform(get("/api/v1/estimates/lead-options").param("q", phone).with(bearer(session)))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/estimates/lead-options").param("q", "example.test").with(bearer(session)))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/estimates/lead-options").param("q", "Partner " + tag).with(bearer(session)))
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/v1/estimates/lead-options").param("leadId", detailed.toString()).with(bearer(session)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(detailed.toString()));
        mvc.perform(get("/api/v1/estimates/lead-options").param("leadId", lost.toString()).with(bearer(session)))
                .andExpect(jsonPath("$.data.length()").value(0));

        create(session, estimate(detailed, "lines", List.of(line(productA, 1)))).andExpect(status().isCreated());

        // Readers can't use the picker; restricted scopes see no leads and can't use one by id.
        TestUser reader = createUser(RoleCode.USER);
        grant(reader, List.of(module("ESTIMATES", false)), List.of());
        mvc.perform(get("/api/v1/estimates/lead-options").with(bearer(login(reader)))).andExpect(status().isForbidden());

        TestUser selfScoped = createUser(RoleCode.USER);
        Map<String, Object> scopedEstimates = module("ESTIMATES", true);
        scopedEstimates.put("dataScope", "SELF");
        grant(selfScoped, List.of(scopedEstimates), List.of());
        Session selfSession = login(selfScoped);
        mvc.perform(get("/api/v1/estimates/lead-options").param("q", tag).with(bearer(selfSession)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        create(selfSession, estimate(detailed, "lines", List.of(line(productA, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("leadId"));

        TestUser leadScoped = createUser(RoleCode.USER);
        Map<String, Object> scopedLeads = module("LEADS", false);
        scopedLeads.put("dataScope", "TEAM");
        grant(leadScoped, List.of(module("ESTIMATES", true), scopedLeads), List.of());
        mvc.perform(get("/api/v1/estimates/lead-options").param("q", tag).with(bearer(login(leadScoped))))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ------------------------------------------------------------------------------------------------ rules

    @Test
    void invalidInputIsRejectedWithFieldErrors() throws Exception {
        create(admin, estimate(null, "lines", List.of(line(productA, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("leadId"));
        create(admin, estimate(leadId, "lines", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("lines"));
        create(admin, estimate(leadId, "lines", List.of(line(productA, 0))))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("lines[0].quantity"));
        create(admin, estimate(leadId, "discountPercent", 101, "lines", List.of(line(productA, 1))))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("discountPercent"));
        create(admin, estimate(leadId, "estimateDate", "2026-10-20", "validUntil", "2026-10-19",
                "lines", List.of(line(productA, 1))))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("validUntil"));
        Map<String, Object> badRate = line(productA, 1);
        badRate.put("unitPrice", "10.005");
        create(admin, estimate(leadId, "lines", List.of(badRate)))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("lines[0].unitPrice"));

        setActive(productB, false);
        create(admin, estimate(leadId, "lines", List.of(line(productB, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("lines[0].productId"));

        UUID lost = lead("Gone " + tag);
        mvc.perform(withJson(patch("/api/v1/leads/{id}/stage", lost).with(bearer(admin)),
                body(Map.of("stage", "LOST", "version", 0)))).andExpect(status().isOk());
        create(admin, estimate(lost, "lines", List.of(line(productA, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("leadId"));
        assertThat(count("select count(*) from estimates e join leads l on l.id = e.lead_id where l.client_name like ?",
                "%" + tag)).isZero();
    }

    @Test
    void editsNeedTheCurrentVersionAndInvoicedEstimatesAreLocked() throws Exception {
        UUID id = id(create(admin, estimate(leadId, "lines", List.of(line(productA, 1)))).andReturn());
        mvc.perform(withJson(put("/api/v1/estimates/{id}", id).with(bearer(admin)),
                        body(estimate(leadId, "version", 5, "lines", List.of(line(productA, 2))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));

        jdbc.update("update estimates set status = 'INVOICED' where id = ?", id);
        mvc.perform(withJson(put("/api/v1/estimates/{id}", id).with(bearer(admin)),
                        body(estimate(leadId, "version", 0, "lines", List.of(line(productA, 2))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ESTIMATE_INVOICED"));
        mvc.perform(delete("/api/v1/estimates/{id}", id).with(bearer(sa)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ESTIMATE_INVOICED"));
    }

    @Test
    void theListSearchesFiltersAndDerivesExpiry() throws Exception {
        String a = content(post("/api/v1/estimates"), admin, estimate(leadId, "lines", List.of(line(productA, 1))));
        UUID expiredId = id(a);
        UUID draftId = id(create(admin, estimate(leadId, "lines", List.of(line(productA, 2)))).andReturn());
        UUID deletedId = id(create(admin, estimate(leadId, "lines", List.of(line(productA, 3)))).andReturn());
        mvc.perform(post("/api/v1/estimates/{id}/send", expiredId).with(bearer(admin))).andExpect(status().isOk());
        jdbc.update("update estimates set valid_until = current_date - 1 where id = ?", expiredId);
        mvc.perform(delete("/api/v1/estimates/{id}", deletedId).with(bearer(sa))).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/estimates").param("q", tag).with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
        String leadNumber = JsonPath.read(a, "$.data.lead.leadNumber");
        mvc.perform(get("/api/v1/estimates").param("q", leadNumber.toLowerCase(Locale.ROOT)).with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/v1/estimates").param("q", tag).param("expired", "true").with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(expiredId.toString()))
                .andExpect(jsonPath("$.data[0].expired").value(true));
        mvc.perform(get("/api/v1/estimates").param("q", tag).param("status", "DRAFT").with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(draftId.toString()));
        mvc.perform(get("/api/v1/estimates").param("leadId", leadId.toString()).with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(2));

        mvc.perform(get("/api/v1/estimates/export").with(bearer(admin))).andExpect(status().isForbidden());
    }

    @Test
    void aLeadWhoseEstimatesWereDeletedCanBeDeletedAndScopeHidesEstimates() throws Exception {
        UUID lead = lead("Removable " + tag);
        UUID id = id(create(admin, estimate(lead, "lines", List.of(line(productA, 1)))).andReturn());
        mvc.perform(delete("/api/v1/leads/{id}", lead).with(bearer(sa))).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/estimates/{id}", id).with(bearer(sa))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/leads/{id}", lead).with(bearer(sa))).andExpect(status().isNoContent());

        UUID visible = id(create(admin, estimate(leadId, "lines", List.of(line(productA, 1)))).andReturn());
        TestUser scoped = createUser(RoleCode.ADMIN);
        Map<String, Object> estimates = module("ESTIMATES", true);
        estimates.put("dataScope", "SELF");
        grant(scoped, List.of(estimates), List.of());
        Session session = login(scoped);
        mvc.perform(get("/api/v1/estimates").param("q", tag).with(bearer(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/estimates/{id}", visible).with(bearer(session))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/estimates/{id}/send", visible).with(bearer(session))).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** No B2C names, B2C rates, B2C totals, uplift or twin number in text meant for partners. */
    private void assertPartnerSafe(String text) {
        assertThat(text).doesNotContain(secretB2cName, "B2C plain " + tag, B2C_PRICE, "1,543.21", "3086.42",
                "2854.94", "309.80", "INT-EST", "Renamed B2C");
        assertThat(text.toLowerCase(Locale.ROOT)).doesNotContain("b2c", "uplift");
    }

    private Map<String, Object> estimate(UUID lead, Object... overrides) {
        Map<String, Object> map = new HashMap<>();
        map.put("leadId", lead);
        map.put("igst", false);
        for (int i = 0; i + 1 < overrides.length; i += 2) {
            map.put((String) overrides[i], overrides[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> line(UUID productId, Object quantity) {
        Map<String, Object> map = new HashMap<>();
        map.put("productId", productId);
        map.put("quantity", quantity);
        return map;
    }

    private ResultActions create(Session session, Map<String, Object> request) throws Exception {
        return mvc.perform(withJson(post("/api/v1/estimates").with(bearer(session)), body(request)));
    }

    private String content(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                           Session session) throws Exception {
        return mvc.perform(request.with(bearer(session))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String content(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                           Session session, Map<String, Object> body) throws Exception {
        return mvc.perform(withJson(request.with(bearer(session)), body(body))).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
    }

    private static UUID id(String body) {
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private static UUID id(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return id(result.getResponse().getContentAsString());
    }

    private static BigDecimal decimalIn(String json, String path) {
        return decimal(json, path);
    }

    private static BigDecimal decimal(String body, String path) {
        Object value = JsonPath.read(body, path);
        return new BigDecimal(value.toString());
    }

    private long sequenceValue() {
        return count("select last_value from document_sequences where prefix = 'EST' and fy_tag = ?",
                String.valueOf(LocalDate.now(ZoneId.of("Asia/Kolkata")).getYear()));
    }

    private String leadStage(UUID lead) {
        return jdbc.queryForObject("select stage from leads where id = ?", String.class, lead);
    }

    private long leadVersion(UUID lead) {
        return count("select version from leads where id = ?", lead);
    }

    private UUID partner(String name) throws Exception {
        return id(content(post("/api/v1/partners"), sa, Map.of("name", name, "category", "INTERIOR_DESIGNER")));
    }

    private UUID lead(String clientName) throws Exception {
        return id(content(post("/api/v1/leads"), admin, Map.of("clientName", clientName, "partnerId", partnerId,
                "projectType", "VILLA", "city", "Hyderabad")));
    }

    private UUID product(String suffix, String partnerPrice, String b2cPrice, String b2cName, int gst) throws Exception {
        return id(content(post("/api/v1/products"), sa, productBody(suffix, partnerPrice, b2cPrice, b2cName, gst)));
    }

    private Map<String, Object> productBody(String suffix, String partnerPrice, String b2cPrice, String b2cName, int gst) {
        Map<String, Object> p = new HashMap<>();
        p.put("sku", tag + suffix);
        p.put("category", "SMART_GLIDE");
        p.put("hsn", "85365090");
        p.put("partnerName", "Partner" + suffix.replace("-", " ") + " " + tag);
        p.put("partnerPrice", new BigDecimal(partnerPrice));
        p.put("gstPercent", gst);
        p.put("b2cName", b2cName);
        if (b2cPrice != null) {
            p.put("b2cPrice", new BigDecimal(b2cPrice));
        }
        return p;
    }

    private void setActive(UUID product, boolean active) throws Exception {
        long version = count("select version from products where id = ?", product);
        mvc.perform(withJson(patch("/api/v1/products/{id}/status", product).with(bearer(sa)),
                body(Map.of("active", active, "version", version)))).andExpect(status().isOk());
    }

    private static Map<String, Object> module(String code, boolean write) {
        Map<String, Object> map = new HashMap<>();
        map.put("moduleCode", code);
        map.put("read", true);
        map.put("write", write);
        return map;
    }

    private void grant(TestUser user, List<Map<String, Object>> modules, List<String> actions) throws Exception {
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", user.id()).with(bearer(sa)),
                        body(Map.of("modules", modules, "actions", actions))))
                .andExpect(status().isOk());
    }
}
