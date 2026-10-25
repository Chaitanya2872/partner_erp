package com.iotiq.partnererp.integration.products;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.products.service.ProductLineSnapshot;
import com.iotiq.partnererp.modules.products.service.ProductService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7 product maintenance: create/update/status/delete, internal-field permissions, validation, history,
 * CSV export and import (D-17, D-18, P7-xx). Every product created here has a SKU starting with the test tag and
 * is removed afterwards, so the 29 seeded products stay as they are.
 */
class ProductApiIT extends ApiIntegrationTest {

    @Autowired
    private ProductService productService;

    private Session sa;
    private Session admin;
    private Session user;
    private String tag;

    @BeforeEach
    void setUp() throws Exception {
        sa = login(createUser(RoleCode.SUPER_ADMIN));
        admin = login(createUser(RoleCode.ADMIN));
        user = login(createUser(RoleCode.USER));
        tag = "T7" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @AfterEach
    void removeTestProducts() {
        String like = tag + "%";
        jdbc.update("delete from estimates where id in (select estimate_id from estimate_items ei join products p "
                + "on p.id = ei.product_id where p.sku ilike ?)", like);
        jdbc.update("delete from products where sku ilike ?", like);
    }

    // ------------------------------------------------------------------------------------------------ create

    @Test
    void anInternalWriterCreatesAProductAndTheCreationIsAudited() throws Exception {
        String body = create(sa, product("-A")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sku").value(tag + "-A"))
                .andExpect(jsonPath("$.data.categoryLabel").value("Smart Glide"))
                .andExpect(jsonPath("$.data.b2cName").value("Glide B2C " + tag))
                .andExpect(jsonPath("$.data.b2cPrice").value(10000.0))
                .andExpect(jsonPath("$.data.discountPercent").value(20))
                .andExpect(jsonPath("$.data.b2cPriceMissing").value(false))
                .andExpect(jsonPath("$.data.aliases.length()").value(2))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.data.id"));
        assertThat(count("select count(*) from audit_logs where entity_type = 'PRODUCT' and entity_id = ? "
                + "and action = 'PRODUCT_CREATED'", id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select unit from products where id = ?", String.class, id)).isEqualTo("nos");
    }

    @Test
    void creatingNeedsWriteAndTheInternalAction() throws Exception {
        create(user, product("-U")).andExpect(status().isForbidden());
        // Admin has Products write by default but not VIEW_INTERNAL_B2C, so can't supply the required B2C name.
        create(admin, product("-W")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_B2C_REQUIRED"));
        assertThat(count("select count(*) from products where sku ilike ?", tag + "%")).isZero();
    }

    @Test
    void theSkuIsUniqueIgnoringCase() throws Exception {
        create(sa, product("-dup")).andExpect(status().isCreated());
        create(sa, product("-DUP")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("sku"))
                .andExpect(jsonPath("$.error.fieldErrors[0].message").value("That SKU already exists."));
        // The seeded STI-2M in another case is taken too.
        Map<String, Object> seeded = product("");
        seeded.put("sku", "sti-2m");
        create(sa, seeded).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].message").value("That SKU already exists."));
    }

    @Test
    void invalidValuesAreRejectedFieldByField() throws Exception {
        Map<String, Object> bad = new HashMap<>();
        bad.put("sku", "=cmd " + tag);
        bad.put("hsn", "12AB");
        bad.put("partnerPrice", -1);
        bad.put("gstPercent", 101);
        bad.put("b2cPrice", 10.123);
        bad.put("costPrice", -5);
        String body = create(sa, bad).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        List<String> fields = JsonPath.read(body, "$.error.fieldErrors[*].field");
        assertThat(fields).contains("sku", "category", "hsn", "partnerName", "partnerPrice", "gstPercent", "b2cName",
                "b2cPrice", "costPrice");
        List<String> messages = JsonPath.read(body, "$.error.fieldErrors[*].message");
        assertThat(messages).contains("Can't be negative.", "GST % must be between 0 and 100.",
                "Use at most 2 decimal places.", "Enter an HSN or SAC code of 4, 6 or 8 digits.",
                "B2C name is required.", "Partner-facing name is required.");
    }

    // ------------------------------------------------------------------------------------------------ update

    @Test
    void writersWithoutTheInternalActionEditPartnerFieldsOnlyAndInternalValuesSurvive() throws Exception {
        UUID id = id(create(sa, product("-E")));
        Map<String, Object> edit = partnerSafe("-E", 7500);
        edit.put("version", 0);
        mvc.perform(withJson(put("/api/v1/products/{id}", id).with(bearer(admin)), body(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partnerPrice").value(7500.0))
                .andExpect(jsonPath("$.data.b2cPrice").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(1));
        Map<String, Object> stored = jdbc.queryForMap("select b2c_name, b2c_price, cost_price, aliases::text a "
                + "from products where id = ?", id);
        assertThat(stored.get("b2c_name")).isEqualTo("Glide B2C " + tag);
        assertThat((BigDecimal) stored.get("b2c_price")).isEqualByComparingTo("10000");
        assertThat((BigDecimal) stored.get("cost_price")).isEqualByComparingTo("6000");
        assertThat((String) stored.get("a")).contains("Glide alt one");

        // Sending an internal field is refused and changes nothing.
        edit.put("version", 1);
        edit.put("b2cPrice", 1);
        mvc.perform(withJson(put("/api/v1/products/{id}", id).with(bearer(admin)), body(edit)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_B2C_REQUIRED"));
        assertThat(jdbc.queryForObject("select b2c_price from products where id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("10000");
    }

    @Test
    void updatesUseTheVersionAndAuditOnlyTheChangedFields() throws Exception {
        UUID id = id(create(sa, product("-V")));
        Map<String, Object> edit = product("-V");
        edit.put("b2cPrice", 12000);
        edit.put("version", 0);
        mvc.perform(withJson(put("/api/v1/products/{id}", id).with(bearer(sa)), body(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountPercent").value(33));
        // Stale version.
        mvc.perform(withJson(put("/api/v1/products/{id}", id).with(bearer(sa)), body(edit)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));
        // Saving the same values again writes nothing.
        edit.put("version", 1);
        mvc.perform(withJson(put("/api/v1/products/{id}", id).with(bearer(sa)), body(edit)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        Map<String, Object> audit = jdbc.queryForMap("select old_values::text o, new_values::text n from audit_logs "
                + "where entity_id = ? and action = 'PRODUCT_UPDATED'", id);
        assertThat((String) audit.get("o")).isEqualTo("{\"b2cPrice\": 10000.00}");
        assertThat((String) audit.get("n")).isEqualTo("{\"b2cPrice\": 12000.00}");
    }

    // ------------------------------------------------------------------------------------------------ status / delete

    @Test
    void deactivatedProductsLeaveThePickerButStayOnTheSheet() throws Exception {
        UUID id = id(create(sa, product("-S")));
        mvc.perform(get("/api/v1/products/options").with(bearer(admin)).param("q", tag))
                .andExpect(jsonPath("$.data[*].sku").value(hasItem(tag + "-S")))
                .andExpect(jsonPath("$.data[0].b2cName").doesNotExist())
                .andExpect(jsonPath("$.data[0].rate").value(8000.0));
        setActive(admin, id, false, 0).andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        setActive(admin, id, false, 0).andExpect(status().isConflict());

        mvc.perform(get("/api/v1/products/options").with(bearer(admin)).param("q", tag))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/products").with(bearer(admin)).param("q", tag).param("active", "false"))
                .andExpect(jsonPath("$.data[0].sku").value(tag + "-S"));
        mvc.perform(get("/api/v1/products/{id}", id).with(bearer(user))).andExpect(status().isOk());
        assertThatThrownBy(() -> productService.lineSnapshot(id)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("deactivated");

        setActive(user, id, true, 1).andExpect(status().isForbidden());
        setActive(admin, id, true, 1).andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(true));
        ProductLineSnapshot snapshot = productService.lineSnapshot(id);
        assertThat(snapshot.name()).isEqualTo("Glide partner " + tag);
        assertThat(snapshot.rate()).isEqualByComparingTo("8000");
        assertThat(snapshot.b2cRate()).isEqualByComparingTo("10000");
        assertThat(count("select count(*) from audit_logs where entity_id = ? and action = 'PRODUCT_STATUS_CHANGED'", id))
                .isEqualTo(2);
    }

    @Test
    void onlyUnusedProductsCanBeDeleted() throws Exception {
        UUID unused = id(create(sa, product("-D1")));
        UUID used = id(create(sa, product("-D2")));
        mvc.perform(delete("/api/v1/products/{id}", unused).with(bearer(admin))).andExpect(status().isForbidden());

        UUID partnerId = UUID.fromString(JsonPath.read(mvc.perform(withJson(post("/api/v1/partners").with(bearer(sa)),
                        body(Map.of("name", "Partner " + tag, "category", "INTERIOR_DESIGNER"))))
                .andReturn().getResponse().getContentAsString(), "$.data.id"));
        UUID estimateId = UUID.randomUUID();
        jdbc.update("insert into estimates (id, estimate_number, partner_id, estimate_date) values (?, ?, ?, current_date)",
                estimateId, "EST-" + tag, partnerId);
        jdbc.update("insert into estimate_items (estimate_id, line_no, product_id, description, quantity, unit_price, "
                + "gst_percent, line_total) values (?, 1, ?, 'x', 1, 8000, 18, 8000)", estimateId, used);

        mvc.perform(delete("/api/v1/products/{id}", used).with(bearer(sa)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_IN_USE"));
        mvc.perform(delete("/api/v1/products/{id}", unused).with(bearer(sa))).andExpect(status().isNoContent());
        assertThat(count("select count(*) from products where id = ?", unused)).isZero();
        assertThat(count("select count(*) from audit_logs where entity_id = ? and action = 'PRODUCT_DELETED'", unused))
                .isEqualTo(1);
        mvc.perform(get("/api/v1/products/{id}", unused).with(bearer(sa))).andExpect(status().isNotFound());
    }

    @Test
    void documentLineSnapshotsSurviveProductEditsDeactivationAndRefusedDeletes() throws Exception {
        UUID id = id(create(sa, product("-L")));
        UUID partnerId = UUID.fromString(JsonPath.read(mvc.perform(withJson(post("/api/v1/partners").with(bearer(sa)),
                        body(Map.of("name", "Partner " + tag, "category", "INTERIOR_DESIGNER"))))
                .andReturn().getResponse().getContentAsString(), "$.data.id"));
        UUID estimateId = UUID.randomUUID();
        jdbc.update("insert into estimates (id, estimate_number, partner_id, estimate_date) values (?, ?, ?, current_date)",
                estimateId, "EST-" + tag, partnerId);
        jdbc.update("insert into estimate_items (estimate_id, line_no, product_id, description, b2c_name, hsn, unit, "
                + "quantity, unit_price, b2c_unit_price, gst_percent, line_total) "
                + "values (?, 1, ?, ?, ?, '85365090', 'nos', 2, 8000, 10000, 18, 16000)",
                estimateId, id, "Glide partner " + tag, "Glide B2C " + tag);
        String snapshotSql = "select product_id::text || '|' || description || '|' || b2c_name || '|' || hsn || '|' || unit "
                + "|| '|' || unit_price || '|' || b2c_unit_price || '|' || gst_percent from estimate_items where estimate_id = ?";
        String before = jdbc.queryForObject(snapshotSql, String.class, estimateId);

        Map<String, Object> edit = product("-L");
        edit.put("partnerName", "Renamed " + tag);
        edit.put("partnerPrice", 9500);
        edit.put("b2cPrice", 14000);
        edit.put("gstPercent", 12);
        edit.put("hsn", "8536");
        edit.put("version", 0);
        mvc.perform(withJson(put("/api/v1/products/{id}", id).with(bearer(sa)), body(edit))).andExpect(status().isOk());
        setActive(sa, id, false, 1).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/products/{id}", id).with(bearer(sa))).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject(snapshotSql, String.class, estimateId)).isEqualTo(before);
        assertThat(count("select count(*) from products where id = ?", id)).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------------ search / history

    @Test
    void searchCoversCategoryLabelsAndTheB2cMissingFilterIsInternalOnly() throws Exception {
        Map<String, Object> missing = product("-M");
        missing.remove("b2cPrice");
        create(sa, missing).andExpect(status().isCreated());
        create(sa, product("-P")).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/products").with(bearer(user)).param("q", "glide").param("size", "200"))
                .andExpect(jsonPath("$.data[*].sku").value(hasItem(tag + "-M")))
                .andExpect(jsonPath("$.data[*].sku").value(hasItem("SG-10F")));
        mvc.perform(get("/api/v1/products").with(bearer(sa)).param("q", tag).param("b2cMissing", "true"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sku").value(tag + "-M"))
                .andExpect(jsonPath("$.data[0].b2cPriceMissing").value(true));
        // For readers without the internal action the filter is ignored (no oracle on hidden prices).
        mvc.perform(get("/api/v1/products").with(bearer(user)).param("q", tag).param("b2cMissing", "true"))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void historyHidesInternalChangesFromReadersWithoutTheAction() throws Exception {
        UUID id = id(create(sa, product("-H")));
        Map<String, Object> edit = product("-H");
        edit.put("b2cPrice", 11000);
        edit.put("version", 0);
        mvc.perform(withJson(put("/api/v1/products/{id}", id).with(bearer(sa)), body(edit))).andExpect(status().isOk());
        edit.put("partnerPrice", 8100);
        edit.put("version", 1);
        mvc.perform(withJson(put("/api/v1/products/{id}", id).with(bearer(sa)), body(edit))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/products/{id}/history", id).with(bearer(sa)))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].changes[0].field").value("partnerPrice"))
                .andExpect(jsonPath("$.data[1].changes[0].field").value("b2cPrice"))
                .andExpect(jsonPath("$.data[2].action").value("PRODUCT_CREATED"));
        String hidden = mvc.perform(get("/api/v1/products/{id}/history", id).with(bearer(user)))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].changes[0].field").value("partnerPrice"))
                .andReturn().getResponse().getContentAsString();
        assertThat(hidden).doesNotContain("b2cPrice", "costPrice", "b2cName", "aliases", "11000", "Glide B2C");

        // The audit log viewer applies the same rule to product entries.
        TestUser auditor = createUser(RoleCode.ADMIN);
        grant(auditor, List.of(Map.of("moduleCode", "AUDIT_LOGS", "read", true)), List.of());
        String audit = mvc.perform(get("/api/v1/audit-logs").with(bearer(login(auditor)))
                        .param("entityType", "PRODUCT").param("entityId", id.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(audit).doesNotContain("b2cPrice", "costPrice", "b2cName", "11000", "Glide B2C");
        mvc.perform(get("/api/v1/audit-logs").with(bearer(sa)).param("entityType", "PRODUCT")
                        .param("entityId", id.toString()))
                .andExpect(content().string(containsString("b2cPrice")));
    }

    @Test
    void metaListsEveryCategoryWithCounts() throws Exception {
        mvc.perform(get("/api/v1/products/meta").with(bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories.length()").value(5))
                .andExpect(jsonPath("$.data.categories[4].label").value("Services"))
                .andExpect(jsonPath("$.data.categories[4].total").value(0))
                .andExpect(jsonPath("$.data.defaultGstPercent").value(18.0))
                .andExpect(jsonPath("$.data.defaultUnit").value("nos"));
    }

    // ------------------------------------------------------------------------------------------------ CSV export

    @Test
    void exportNeedsTheExportPermissionAndHidesB2cColumnsWithoutTheAction() throws Exception {
        mvc.perform(get("/api/v1/products/export").with(bearer(admin))).andExpect(status().isForbidden());

        String internal = mvc.perform(get("/api/v1/products/export").with(bearer(sa)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("iotiq-item-sheet.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(internal).startsWith("﻿sku,category,b2c_name,cp_name,cp_alternatives (| separated),hsn,"
                + "gst_percent,unit,b2c_price,cp_price,cost,active\r\n");
        assertThat(internal).contains("STI-2M,Smart Touch Interfaces,2 Module With Edge,Smart Touch Interface - 2M (STI-2M),,,18,nos,,5749,,yes");
        assertThat(internal.split("\r\n")).hasSize(30);

        TestUser exporter = createUser(RoleCode.ADMIN);
        grant(exporter, List.of(Map.of("moduleCode", "PRODUCTS", "read", true, "export", true)), List.of());
        String partnerSafe = mvc.perform(get("/api/v1/products/export").with(bearer(login(exporter)))
                        .param("category", "SMART_TOUCH_INTERFACES"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(partnerSafe).startsWith("﻿sku,category,cp_name,hsn,gst_percent,unit,cp_price,active\r\n");
        assertThat(partnerSafe).contains("STI-2M,Smart Touch Interfaces,Smart Touch Interface - 2M (STI-2M),,18,nos,5749,yes");
        assertThat(partnerSafe).doesNotContain("b2c", "cost", "2 Module With Edge");
        assertThat(partnerSafe.split("\r\n")).hasSize(7);
    }

    // ------------------------------------------------------------------------------------------------ CSV import

    @Test
    void theTemplateUsesAValidCategoryAndPreviewsAsACreate() throws Exception {
        String template = mvc.perform(get("/api/v1/products/import-template").with(bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(template).contains("SW-4M,Smart Touch Interfaces,").doesNotContain("Switching");
        importCsv(sa, template.replace("SW-4M", tag + "-TPL"), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.rows[0].outcome").value("CREATE"));
        assertThat(count("select count(*) from products where sku ilike ?", tag + "%")).isZero();
        mvc.perform(get("/api/v1/products/import-template").with(bearer(user))).andExpect(status().isForbidden());
    }

    @Test
    void importPreviewsThenCommitsValidRowsAndReportsErrors() throws Exception {
        String csv = "sku,category,b2c_name,cp_name,cp_alternatives (| separated),hsn,gst_percent,unit,b2c_price,cp_price,cost\r\n"
                + tag + "-1,Smart Glide,Curtain motor B2C,Curtain drive unit,Drive A|Drive B,8501,18,nos,9000,7000,5000\r\n"
                + tag + "-2,Switching,Bad B2C,Bad partner,,,18,nos,1,1,\r\n"
                + tag + "-3,locks & entry,\"Lock, B2C\",\"Lock \"\"Pro\"\"\",,,12.5,pcs,,4000,\r\n"
                + tag + "-1,Smart Glide,Again,Again,,,18,nos,1,1,\r\n"
                + tag + "-4,Smart Glide,,No B2C name,,,18,nos,,100,\r\n"
                + tag + "-5,Smart Glide,Neg,Neg,,ABCD,18,nos,-1,abc,\r\n";
        importCsv(sa, csv, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.failed").value(4))
                .andExpect(jsonPath("$.data.rows[1].errors[0]").value(containsString("Unknown category \"Switching\"")))
                .andExpect(jsonPath("$.data.rows[2].warnings[0]").value("B2C price needed."))
                .andExpect(jsonPath("$.data.rows[3].errors[0]").value("sku: This SKU is already on row 1 of the file."))
                .andExpect(jsonPath("$.data.rows[4].errors[0]").value("b2c_name: B2C name is required for a new product."))
                .andExpect(jsonPath("$.data.rows[5].errors.length()").value(3));
        assertThat(count("select count(*) from products where sku ilike ?", tag + "%")).isZero();

        importCsv(sa, csv, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(false))
                .andExpect(jsonPath("$.data.created").value(2));
        Map<String, Object> lock = jdbc.queryForMap("select category, b2c_name, partner_name, gst_percent, unit, b2c_price "
                + "from products where sku = ?", tag + "-3");
        assertThat(lock.get("category")).isEqualTo("LOCKS_ENTRY");
        assertThat(lock.get("b2c_name")).isEqualTo("Lock, B2C");
        assertThat(lock.get("partner_name")).isEqualTo("Lock \"Pro\"");
        assertThat((BigDecimal) lock.get("gst_percent")).isEqualByComparingTo("12.5");
        assertThat(lock.get("unit")).isEqualTo("pcs");
        assertThat(lock.get("b2c_price")).isNull();
        assertThat(count("select count(*) from audit_logs where action = 'PRODUCT_IMPORTED' and entity_id in "
                + "(select id from products where sku ilike ?)", tag + "%")).isEqualTo(2);
        assertThat(count("select count(*) from audit_logs where action = 'PRODUCT_IMPORT_COMPLETED' "
                + "and new_values ->> 'created' = '2'")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void missingColumnsAndBlankCellsKeepStoredValues() throws Exception {
        UUID id = id(create(sa, product("-K")));
        // No b2c_price/cost/alias columns; blank category, hsn and gst cells; only the partner price changes.
        String csv = "SKU,Category,B2C_Name,CP_Name,HSN,GST_Percent,CP_Price\n"
                + tag.toLowerCase() + "-k,,,,,,8500\n";
        importCsv(sa, csv, true).andExpect(jsonPath("$.data.rows[0].outcome").value("UPDATE"))
                .andExpect(jsonPath("$.data.rows[0].changes[0]").value("cp_price: 8000 → 8500"))
                .andExpect(jsonPath("$.data.columns").value(not(hasItem("b2c_price"))));
        importCsv(sa, csv, false).andExpect(jsonPath("$.data.updated").value(1));
        Map<String, Object> row = jdbc.queryForMap("select sku, category, b2c_name, hsn, gst_percent, partner_price, "
                + "b2c_price, cost_price, aliases::text a, version from products where id = ?", id);
        // The SKU matches ignoring case; the stored spelling is kept.
        assertThat(row.get("sku")).isEqualTo(tag + "-K");
        assertThat(row.get("category")).isEqualTo("SMART_GLIDE");
        assertThat(row.get("b2c_name")).isEqualTo("Glide B2C " + tag);
        assertThat(row.get("hsn")).isEqualTo("85365090");
        assertThat((BigDecimal) row.get("gst_percent")).isEqualByComparingTo("18");
        assertThat((BigDecimal) row.get("partner_price")).isEqualByComparingTo("8500");
        assertThat((BigDecimal) row.get("b2c_price")).isEqualByComparingTo("10000");
        assertThat((BigDecimal) row.get("cost_price")).isEqualByComparingTo("6000");
        assertThat((String) row.get("a")).contains("Glide alt one");

        // Importing the same file again changes nothing.
        importCsv(sa, csv, true).andExpect(jsonPath("$.data.unchanged").value(1))
                .andExpect(jsonPath("$.data.rows[0].outcome").value("UNCHANGED"));
    }

    @Test
    void importNeedsTheInternalActionAndTheRequiredColumns() throws Exception {
        String csv = "sku,b2c_name,cp_name\n" + tag + "-X,A,B\n";
        importCsv(user, csv, true).andExpect(status().isForbidden());
        importCsv(admin, csv, true).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_B2C_REQUIRED"));
        importCsv(sa, "sku,cp_name\n" + tag + "-X,B\n", true).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].message").value(containsString("sku, b2c_name and cp_name")));
        importCsv(sa, "sku,b2c_name,cp_name\n", true).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].message").value("The file has no data rows."));
        mvc.perform(multipart("/api/v1/products/import").with(bearer(sa))).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private Map<String, Object> product(String suffix) {
        Map<String, Object> p = partnerSafe(suffix, 8000);
        p.put("b2cName", "Glide B2C " + tag);
        p.put("aliases", List.of("Glide alt one", "Glide alt two", "glide ALT one"));
        p.put("b2cPrice", 10000);
        p.put("costPrice", 6000);
        return p;
    }

    private Map<String, Object> partnerSafe(String suffix, int partnerPrice) {
        Map<String, Object> p = new HashMap<>();
        p.put("sku", tag + suffix);
        p.put("category", "SMART_GLIDE");
        p.put("hsn", "8536 5090");
        p.put("partnerName", "Glide partner " + tag);
        p.put("partnerPrice", partnerPrice);
        p.put("gstPercent", 18);
        return p;
    }

    private ResultActions create(Session session, Map<String, Object> product) throws Exception {
        return mvc.perform(withJson(post("/api/v1/products").with(bearer(session)), body(product)));
    }

    private UUID id(ResultActions created) throws Exception {
        return UUID.fromString(JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.data.id"));
    }

    private ResultActions setActive(Session session, UUID id, boolean active, long version) throws Exception {
        return mvc.perform(withJson(patch("/api/v1/products/{id}/status", id).with(bearer(session)),
                body(Map.of("active", active, "version", version))));
    }

    private ResultActions importCsv(Session session, String csv, boolean dryRun) throws Exception {
        return mvc.perform(multipart("/api/v1/products/import")
                .file(new MockMultipartFile("file", "items.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .param("dryRun", String.valueOf(dryRun))
                .with(bearer(session)));
    }

    private void grant(TestUser target, List<Map<String, Object>> modules, List<String> actions) throws Exception {
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", target.id()).with(bearer(sa)),
                        body(Map.of("modules", modules, "actions", actions))))
                .andExpect(status().isOk());
    }
}
