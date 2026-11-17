package com.iotiq.partnererp.integration.invoices;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 9 invoices over HTTP: GST treatment and tax snapshots, the supplier registration, conversion from estimates,
 * the DRAFT → ISSUED → CANCELLED lifecycle, numbering under concurrency, immutability (API and database),
 * permissions, data scope, partner-safe data and the audit trail.
 */
class InvoiceApiIT extends ApiIntegrationTest {

    private static final String B2C_PRICE = "1543.21";
    private static final String SUPPLIER_GSTIN = "36AABCI1234A1ZC";
    private static final String CUSTOMER_GSTIN_KA = "29AABCT1234Q1ZZ";

    private Session sa;
    private Session admin;
    private String tag;
    private String secretB2cName;
    private UUID partnerId;
    private UUID leadId;
    private UUID productA;
    private UUID productB;
    private Map<String, Object> seededSettings;

    @BeforeEach
    void setUp() throws Exception {
        sa = login(createUser(RoleCode.SUPER_ADMIN));
        admin = login(createUser(RoleCode.ADMIN));
        tag = "V" + String.format("%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000L));
        secretB2cName = "B2C secret " + tag;
        partnerId = partner("Partner " + tag);
        leadId = lead("Client " + tag);
        mvc.perform(post("/api/v1/leads/{id}/acknowledge", leadId).with(bearer(sa))).andExpect(status().isOk());
        productA = product("-A", "1000", B2C_PRICE, secretB2cName, 18);
        productB = product("-B", "250.50", null, "B2C plain " + tag, 5);
        seededSettings = jdbc.queryForMap("select company_name, address from company_settings");
        registerSupplier("36", SUPPLIER_GSTIN);
    }

    @AfterEach
    void cleanUp() {
        deleteTestProducts(tag + "%");
        // The settings row is shared by every test class: put the seeded registration back.
        jdbc.update("update company_settings set company_name = ?, address = ?, state_code = null, gstin = null, "
                        + "einvoicing_applicable = null",
                seededSettings.get("company_name"), seededSettings.get("address"));
    }

    // ------------------------------------------------------------------------------------------------ supplier

    @Test
    void theSupplierRegistrationIsValidatedAuditedWithAMaskedGstinAndNeedsSettingsWrite() throws Exception {
        long version = settingsVersion();
        Map<String, Object> bad = new HashMap<>(Map.of("legalName", "", "address", " ", "stateCode", "99",
                "gstin", "36AABCI1234A1ZD", "version", version));
        String errors = mvc.perform(withJson(put("/api/v1/settings/gst-registration").with(bearer(sa)), body(bad)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(errors).contains("legalName", "address", "stateCode", "check character", "einvoicingApplicable");
        bad.put("einvoicingApplicable", false);
        bad.putAll(Map.of("legalName", "X", "address", "Y", "stateCode", "29", "gstin", SUPPLIER_GSTIN));
        mvc.perform(withJson(put("/api/v1/settings/gst-registration").with(bearer(sa)), body(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("gstin"));

        // Admins hold Settings read only by default: they may see the registration (it is printed) but not change it.
        mvc.perform(withJson(put("/api/v1/settings/gst-registration").with(bearer(admin)),
                body(Map.of("legalName", "X", "address", "Y", "stateCode", "36", "gstin", SUPPLIER_GSTIN,
                        "version", version)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/settings/gst-registration").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stateCode").value("36"))
                .andExpect(jsonPath("$.data.stateName").value("Telangana"))
                .andExpect(jsonPath("$.data.complete").value(true));

        registerSupplier("29", "29AAGCB7383J1Z4");
        String audit = jdbc.queryForObject("select old_values::text || new_values::text from audit_logs "
                + "where action = 'GST_REGISTRATION_UPDATED' order by created_at desc limit 1", String.class);
        assertThat(audit).contains("••1Z4", "\"stateCode\"").doesNotContain("29AAGCB7383J1Z4", SUPPLIER_GSTIN);
    }

    // ------------------------------------------------------------------------------------------------ tax

    @Test
    void anIntraStateInvoiceStoresCgstAndSgstPerLineAndIssuesWithAFrozenSnapshot() throws Exception {
        String draft = create(admin, invoice(leadId, "discountPercent", 7.5, "placeOfSupplyCode", null,
                "placeOfSupplyBasis", null, "lines", List.of(line(productA, 2), line(productB, 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.invoiceNumber").doesNotExist())
                .andExpect(jsonPath("$.data.paymentStatus").doesNotExist())
                .andExpect(jsonPath("$.data.tax.taxType").doesNotExist())
                .andExpect(jsonPath("$.data.tax.missing[0]").value("placeOfSupplyCode"))
                .andReturn().getResponse().getContentAsString();
        UUID id = id(draft);
        // Without a place of supply nothing is taxed or guessed.
        assertThat(decimal(draft, "$.data.totals.gstAmount")).isEqualByComparingTo("0");
        mvc.perform(withJson(post("/api/v1/invoices/{id}/issue", id).with(bearer(admin)), body(Map.of("version", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("placeOfSupplyCode"));

        String saved = update(admin, id, invoice(leadId, "discountPercent", 7.5, "version", 0,
                "lines", List.of(line(productA, 2), line(productB, 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tax.taxType").value("INTRA"))
                .andExpect(jsonPath("$.data.tax.stateTaxLabel").value("SGST"))
                .andExpect(jsonPath("$.data.tax.confirmationRequired").value(false))
                .andReturn().getResponse().getContentAsString();
        assertLine(saved, 0, "1850.00", "166.50", "166.50", "0", "333.00", "2183.00");
        assertLine(saved, 1, "695.14", "17.38", "17.38", "0", "34.76", "729.90");
        assertThat(decimal(saved, "$.data.totals.cgstAmount")).isEqualByComparingTo("183.88");
        assertThat(decimal(saved, "$.data.totals.sgstAmount")).isEqualByComparingTo("183.88");
        assertThat(decimal(saved, "$.data.totals.grandTotal")).isEqualByComparingTo("2912.90");
        long version = ((Number) JsonPath.read(saved, "$.data.version")).longValue();

        mvc.perform(withJson(post("/api/v1/invoices/{id}/issue", id).with(bearer(admin)),
                        body(Map.of("version", version, "expectedGrandTotal", 2912.80, "regularSupplyConfirmed", true))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TOTALS_CHANGED"));
        String issued = issue(admin, id, version, "2912.90")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.supplier.gstin").value(SUPPLIER_GSTIN))
                .andExpect(jsonPath("$.data.supplier.stateCode").value("36"))
                .andExpect(jsonPath("$.data.billing.stateName").value("Telangana"))
                .andReturn().getResponse().getContentAsString();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        String number = JsonPath.read(issued, "$.data.invoiceNumber");
        assertThat(number).matches("INV-" + today.getYear() + "-\\d{6}");
        assertThat((String) JsonPath.read(issued, "$.data.invoiceDate")).isEqualTo(today.toString());
        assertThat((String) JsonPath.read(issued, "$.data.dueDate")).isEqualTo(today.plusDays(15).toString());
        assertThat(decimal(issued, "$.data.balance")).isEqualByComparingTo("2912.90");

        Map<String, Object> row = jdbc.queryForMap("select * from invoices where id = ?", id);
        assertThat(row.get("supplier_gstin")).isEqualTo(SUPPLIER_GSTIN);
        assertThat(row.get("tax_type")).isEqualTo("INTRA");
        assertThat(row.get("igst")).isEqualTo(false);
        assertThat((BigDecimal) row.get("cgst_amount")).isEqualByComparingTo("183.88");
        assertThat(jdbc.queryForList("select taxable_value::text || '/' || cgst_amount || '/' || sgst_amount || '/' "
                + "|| igst_amount || '/' || tax_amount || '/' || total_amount from invoice_items where invoice_id = ? "
                + "order by line_no", String.class, id))
                .containsExactly("1850.00/166.50/166.50/0.00/333.00/2183.00", "695.14/17.38/17.38/0.00/34.76/729.90");
        // BR-V3: the lead is Won; the internal twin exists now that the invoice has a number.
        assertThat(jdbc.queryForObject("select stage from leads where id = ?", String.class, leadId)).isEqualTo("WON");
        assertThat(jdbc.queryForObject("select internal_number from b2c_documents where invoice_id = ?", String.class, id))
                .isEqualTo("INT-" + number);

        // A later change of registration never alters the issued invoice.
        registerSupplier("29", "29AAGCB7383J1Z4");
        mvc.perform(get("/api/v1/invoices/{id}", id).with(bearer(admin)))
                .andExpect(jsonPath("$.data.supplier.gstin").value(SUPPLIER_GSTIN))
                .andExpect(jsonPath("$.data.tax.taxType").value("INTRA"));
        assertThat(actions(id)).containsExactly("INVOICE_CREATED", "INVOICE_UPDATED", "INVOICE_ISSUED");
    }

    @Test
    void anInterStateInvoiceChargesIgstAndAUnionTerritoryLevysUtgst() throws Exception {
        String body = create(admin, invoice(leadId, "discountPercent", 7.5, "billingStateCode", "29",
                "billingGstin", CUSTOMER_GSTIN_KA.toLowerCase(Locale.ROOT), "placeOfSupplyCode", "29",
                "lines", List.of(line(productA, 2), line(productB, 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tax.taxType").value("INTER"))
                .andExpect(jsonPath("$.data.billing.gstin").value(CUSTOMER_GSTIN_KA))
                .andExpect(jsonPath("$.data.tax.confirmationRequired").value(false))
                .andReturn().getResponse().getContentAsString();
        assertLine(body, 0, "1850.00", "0", "0", "333.00", "333.00", "2183.00");
        assertLine(body, 1, "695.14", "0", "0", "34.76", "34.76", "729.90");
        assertThat(decimal(body, "$.data.totals.igstAmount")).isEqualByComparingTo("367.76");
        String issued = issue(admin, id(body), 0, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(jdbc.queryForObject("select igst from invoices where id = ?", Boolean.class, id(body))).isTrue();
        assertThat(content(get("/api/v1/invoices/{id}/document", id(body)), admin))
                .contains("TAX INVOICE", "IGST", "29 · Karnataka", CUSTOMER_GSTIN_KA, JsonPath.read(issued, "$.data.invoiceNumber"));

        registerSupplier("04", "04AABCI1234A1ZJ");
        mvc.perform(withJson(post("/api/v1/invoices").with(bearer(admin)), body(invoice(leadId, "billingStateCode", "04",
                        "placeOfSupplyCode", "04", "lines", List.of(line(productA, 1))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tax.taxType").value("INTRA"))
                .andExpect(jsonPath("$.data.tax.utgst").value(true))
                .andExpect(jsonPath("$.data.tax.stateTaxLabel").value("UTGST"));
    }

    @Test
    void aDoubtfulTreatmentNeedsAnAuthorizedConfirmationThatCoversTheCurrentInputs() throws Exception {
        // Billed in Telangana, installed in Karnataka: the place of supply differs from the billing state.
        UUID id = id(create(admin, invoice(leadId, "placeOfSupplyCode", "29", "placeOfSupplyBasis", "INSTALLATION_SITE",
                "lines", List.of(line(productA, 1)))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tax.taxType").value("INTER"))
                .andExpect(jsonPath("$.data.tax.confirmationRequired").value(true))
                .andExpect(jsonPath("$.data.tax.reasons[0].code").value("PLACE_OF_SUPPLY_DIFFERS"))
                .andReturn());
        issue(admin, id, 0, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TAX_CONFIRMATION_REQUIRED"));
        mvc.perform(withJson(post("/api/v1/invoices/{id}/confirm-tax", id).with(bearer(admin)),
                body(Map.of("version", 0, "note", "Installed at the Bengaluru site")))).andExpect(status().isForbidden());

        Session approver = login(user(List.of(module("INVOICES", true, "approve", true)), List.of()));
        mvc.perform(withJson(post("/api/v1/invoices/{id}/confirm-tax", id).with(bearer(approver)),
                body(Map.of("version", 0, "note", " ")))).andExpect(status().isBadRequest());
        mvc.perform(withJson(post("/api/v1/invoices/{id}/confirm-tax", id).with(bearer(approver)),
                        body(Map.of("version", 0, "note", "Installed at the Bengaluru site"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tax.confirmed").value(true))
                .andExpect(jsonPath("$.data.tax.confirmationNote").value("Installed at the Bengaluru site"));
        long version = count("select version from invoices where id = ?", id);

        // Changing a covered input (the basis) clears the confirmation; it must be given again.
        String changed = update(admin, id, invoice(leadId, "placeOfSupplyCode", "29", "placeOfSupplyBasis",
                "DELIVERY_LOCATION", "version", version, "lines", List.of(line(productA, 1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tax.confirmed").value(false))
                .andReturn().getResponse().getContentAsString();
        version = ((Number) JsonPath.read(changed, "$.data.version")).longValue();
        issue(admin, id, version, null).andExpect(jsonPath("$.error.code").value("TAX_CONFIRMATION_REQUIRED"));
        mvc.perform(withJson(post("/api/v1/invoices/{id}/confirm-tax", id).with(bearer(approver)),
                body(Map.of("version", version, "note", "Delivered to Bengaluru")))).andExpect(status().isOk());
        issue(admin, id, version + 1, null).andExpect(status().isOk());
        assertThat(actions(id)).containsExactly("INVOICE_CREATED", "INVOICE_TAX_CONFIRMED", "INVOICE_UPDATED",
                "INVOICE_TAX_CONFIRMED", "INVOICE_ISSUED");
        assertThat(jdbc.queryForObject("select tax_confirmation_note from invoices where id = ?", String.class, id))
                .isEqualTo("Delivered to Bengaluru");
    }

    @Test
    void issuingNeedsACompleteSupplierRegistration() throws Exception {
        UUID id = id(create(admin, invoice(leadId, "lines", List.of(line(productA, 1)))).andReturn());
        jdbc.update("update company_settings set gstin = null");
        try {
            issue(admin, id, 0, null).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("SUPPLIER_REGISTRATION_INCOMPLETE"));
            mvc.perform(get("/api/v1/invoices/meta").with(bearer(admin)))
                    .andExpect(jsonPath("$.data.supplierComplete").value(false));
        } finally {
            jdbc.update("update company_settings set gstin = ?", SUPPLIER_GSTIN);
        }
        issue(admin, id, 0, null).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------------------ conversion

    @Test
    void installationLinesSurviveConversionDraftEditsAndIssue() throws Exception {
        UUID estimate = sentEstimate(leadId, 0, List.of(line(productA, 1),
                installation("Install and commission " + tag, 2, "500", "12")));
        String estimateTotal = jdbc.queryForObject("select grand_total::text from estimates where id = ?",
                String.class, estimate);

        String draft = convert(admin, estimate).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].kind").value("PRODUCT"))
                .andExpect(jsonPath("$.data.lines[1].kind").value("INSTALLATION"))
                .andExpect(jsonPath("$.data.lines[1].description").value("Install and commission " + tag))
                .andExpect(jsonPath("$.data.lines[1].unit").value("job"))
                .andExpect(jsonPath("$.data.lines[1].productId").doesNotExist())
                .andExpect(jsonPath("$.data.lines[1].taxableValue").value(1000.0))
                .andReturn().getResponse().getContentAsString();
        UUID id = id(draft);
        String saved = update(admin, id, conversionBilling(0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[1].kind").value("INSTALLATION"))
                .andExpect(jsonPath("$.data.lines[1].taxAmount").value(120.0))
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(saved, "$.data.totals.grandTotal")).isEqualByComparingTo(estimateTotal);
        assertThat(decimal(saved, "$.data.totals.taxableAmount")).isEqualByComparingTo(
                jdbc.queryForObject("select taxable_amount from estimates where id = ?", BigDecimal.class, estimate));
        issue(admin, id, 1, estimateTotal).andExpect(status().isOk());
        assertThat(jdbc.queryForList("select kind from invoice_items where invoice_id = ? order by line_no",
                String.class, id)).containsExactly("PRODUCT", "INSTALLATION");
        String printed = content(get("/api/v1/invoices/{id}/document", id), admin);
        assertThat(printed).contains("Installation", "Install and commission " + tag);
        // The issued lines are frozen.
        assertThatThrownBy(() -> jdbc.update("update invoice_items set kind = 'PRODUCT' where invoice_id = ? "
                + "and kind = 'INSTALLATION'", id)).hasMessageContaining("can't be changed");

        // A direct invoice draft keeps and edits its installation lines.
        String direct = create(admin, invoice(leadId, "lines", List.of(line(productA, 1),
                installation("Wiring " + tag, 1, "800", "18")))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[1].kind").value("INSTALLATION"))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> keptProduct = line(productA, 1);
        keptProduct.put("id", JsonPath.read(direct, "$.data.lines[0].id"));
        Map<String, Object> edited = installation("Wiring and testing " + tag, 2, "800", "18");
        edited.put("id", JsonPath.read(direct, "$.data.lines[1].id"));
        update(admin, id(direct), invoice(leadId, "version", 0, "lines", List.of(keptProduct, edited)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[1].description").value("Wiring and testing " + tag))
                .andExpect(jsonPath("$.data.lines[1].taxableValue").value(1600.0));
        Map<String, Object> noText = installation(" ", 1, "800", "18");
        create(admin, invoice(leadId, "lines", List.of(noText))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("lines[0].description"));
    }

    @Test
    void convertingAnEstimateCopiesItsAgreedValuesLocksItAndShowsTheTaxDifference() throws Exception {
        UUID estimate = sentEstimate(leadId, 7.5, List.of(line(productA, 2), line(productB, 3)));
        String estimateBefore = estimateSnapshot(estimate);

        String draft = convert(admin, estimate).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.estimate.id").value(estimate.toString()))
                .andExpect(jsonPath("$.data.lead.id").value(leadId.toString()))
                .andExpect(jsonPath("$.data.partner.id").value(partnerId.toString()))
                .andExpect(jsonPath("$.data.billing.name").value("Client " + tag))
                .andExpect(jsonPath("$.data.feePercent").value(8.0))
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].description").value("Partner A " + tag))
                .andExpect(jsonPath("$.data.lines[0].hsn").value("85365090"))
                .andExpect(jsonPath("$.data.lines[1].unitPrice").value(250.5))
                .andExpect(jsonPath("$.data.lines[1].gstPercent").value(5.0))
                .andReturn().getResponse().getContentAsString();
        UUID id = id(draft);
        assertThat(decimal(draft, "$.data.discountPercent")).isEqualByComparingTo("7.5");
        assertThat(jdbc.queryForObject("select status from estimates where id = ?", String.class, estimate))
                .isEqualTo("INVOICED");

        // Billing details and the place of supply can change; the agreed commercial values can't.
        update(admin, id, Map.of("lines", List.of(line(productA, 9)), "version", 0, "placeOfSupplyCode", "36"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("lines"));
        update(admin, id, Map.of("discountPercent", 0, "version", 0)).andExpect(status().isBadRequest());
        String saved = update(admin, id, conversionBilling(0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tax.taxType").value("INTRA"))
                .andExpect(jsonPath("$.data.comparison.taxTypeChanged").value(false))
                .andReturn().getResponse().getContentAsString();
        // The estimate rounds once (2912.89); the invoice rounds per line (2912.90). The difference is shown.
        assertThat(decimal(saved, "$.data.comparison.estimateGrandTotal")).isEqualByComparingTo("2912.89");
        assertThat(decimal(saved, "$.data.totals.grandTotal")).isEqualByComparingTo("2912.90");
        assertThat(decimal(saved, "$.data.comparison.grandTotalDifference")).isEqualByComparingTo("0.01");
        assertThat(decimal(saved, "$.data.totals.taxableAmount")).isEqualByComparingTo("2545.14");

        // The estimate is locked and its snapshot unchanged.
        mvc.perform(withJson(put("/api/v1/estimates/{id}", estimate).with(bearer(admin)),
                        body(Map.of("leadId", leadId, "version", count("select version from estimates where id = ?", estimate),
                                "lines", List.of(line(productA, 1))))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ESTIMATE_INVOICED"));
        assertThat(estimateSnapshot(estimate)).isEqualTo(estimateBefore.replace("SENT", "INVOICED"));

        issue(admin, id, 1, "2912.90").andExpect(status().isOk());
        assertThat(actions(id)).containsExactly("INVOICE_CONVERTED", "INVOICE_UPDATED", "INVOICE_ISSUED");
        assertThat(count("select count(*) from audit_logs where action = 'ESTIMATE_STATUS_CHANGED' and entity_id = ? "
                + "and new_values->>'status' = 'INVOICED'", estimate)).isEqualTo(1);
    }

    @Test
    void anEstimateIsConvertedOnceAndDeletedOrUnsentEstimatesAreRefused() throws Exception {
        UUID estimate = sentEstimate(leadId, 0, List.of(line(productA, 1)));
        UUID id = id(convert(admin, estimate).andExpect(status().isCreated()).andReturn());
        convert(admin, estimate).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ESTIMATE_ALREADY_INVOICED"));

        // Discarding the draft gives the estimate its previous status back; it can then be converted again.
        mvc.perform(delete("/api/v1/invoices/{id}", id).with(bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/invoices/{id}", id).with(bearer(sa))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select status from estimates where id = ?", String.class, estimate)).isEqualTo("SENT");
        mvc.perform(get("/api/v1/invoices/{id}", id).with(bearer(sa))).andExpect(status().isNotFound());
        assertThat(actions(id)).containsExactly("INVOICE_CONVERTED", "INVOICE_DRAFT_DELETED");
        UUID again = id(convert(admin, estimate).andExpect(status().isCreated()).andReturn());

        // After cancellation the estimate (still invoiced) can be converted again; the old number stays used.
        update(admin, again, conversionBilling(0)).andExpect(status().isOk());
        String first = issue(admin, again, 1, null).andReturn().getResponse().getContentAsString();
        cancel(sa, again, 2, "Wrong billing address").andExpect(status().isOk());
        UUID third = id(convert(admin, estimate).andExpect(status().isCreated()).andReturn());
        update(admin, third, conversionBilling(0)).andExpect(status().isOk());
        String second = issue(admin, third, 1, null).andReturn().getResponse().getContentAsString();
        assertThat(sequence(JsonPath.read(second, "$.data.invoiceNumber")))
                .isGreaterThan(sequence(JsonPath.read(first, "$.data.invoiceNumber")));

        UUID draftEstimate = estimateId(leadId, 0, List.of(line(productA, 1)));
        convert(admin, draftEstimate).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ESTIMATE_NOT_CONVERTIBLE"));
        UUID deleted = sentEstimate(leadId, 0, List.of(line(productA, 1)));
        mvc.perform(archive("/api/v1/estimates/{id}", deleted).with(bearer(sa))).andExpect(status().isNoContent());
        convert(admin, deleted).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ESTIMATE_DELETED"));
        assertThat(count("select count(*) from invoices where estimate_id in (?, ?)", draftEstimate, deleted)).isZero();
    }

    /**
     * Phase 10 approval, item 2: one live invoice per estimate, cancelled invoices kept intact, discarding a draft
     * releases the estimate, the conversion trail is audited, and no snapshot is ever overwritten.
     */
    @Test
    void conversionHistoryAndSnapshotsSurviveDiscardCancellationAndReconversion() throws Exception {
        UUID estimate = sentEstimate(leadId, 5, List.of(line(productA, 2), line(productB, 3)));
        String agreed = estimateSnapshot(estimate).substring("SENT".length());

        // Discarded draft: the estimate is released and converts again.
        UUID discarded = id(convert(admin, estimate).andExpect(status().isCreated()).andReturn());
        mvc.perform(delete("/api/v1/invoices/{id}", discarded).with(bearer(sa))).andExpect(status().isNoContent());
        assertThat(estimateSnapshot(estimate)).isEqualTo("SENT" + agreed);

        // Issued, then cancelled: the cancelled invoice keeps its number, lines, tax, supplier and estimate link.
        UUID first = id(convert(admin, estimate).andExpect(status().isCreated()).andReturn());
        convert(admin, estimate).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ESTIMATE_ALREADY_INVOICED"));
        update(admin, first, conversionBilling(0)).andExpect(status().isOk());
        issue(admin, first, 1, null).andExpect(status().isOk());
        cancel(sa, first, 2, "Raised in error").andExpect(status().isOk());
        String cancelled = invoiceSnapshot(first);

        UUID second = id(convert(admin, estimate).andExpect(status().isCreated()).andReturn());
        convert(admin, estimate).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ESTIMATE_ALREADY_INVOICED"));
        assertThat(count("select count(*) from invoices where estimate_id = ? and status <> 'CANCELLED' "
                + "and deleted_at is null", estimate)).isEqualTo(1);
        update(admin, second, conversionBilling(0)).andExpect(status().isOk());
        issue(admin, second, 1, null).andExpect(status().isOk());
        String issued = invoiceSnapshot(second);

        // A later supplier registration change touches neither invoice; the estimate's agreed values never change.
        registerSupplier("29", "29AAGCB7383J1Z4");
        assertThat(invoiceSnapshot(first)).isEqualTo(cancelled).contains("CANCELLED", "|36|" + SUPPLIER_GSTIN);
        assertThat(invoiceSnapshot(second)).isEqualTo(issued).contains("ISSUED", "|36|" + SUPPLIER_GSTIN);
        assertThat(estimateSnapshot(estimate)).isEqualTo("INVOICED" + agreed);
        assertThat(count("select count(*) from invoices where estimate_id = ?", estimate)).isEqualTo(3);
        mvc.perform(get("/api/v1/invoices").param("estimateId", estimate.toString()).with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/v1/invoices/{id}", first).with(bearer(admin)))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("Raised in error"));

        // The trail: every conversion names the estimate; the estimate's own history shows the status changes.
        assertThat(actions(discarded)).containsExactly("INVOICE_CONVERTED", "INVOICE_DRAFT_DELETED");
        assertThat(actions(first)).containsExactly("INVOICE_CONVERTED", "INVOICE_UPDATED", "INVOICE_ISSUED",
                "INVOICE_CANCELLED");
        assertThat(actions(second)).containsExactly("INVOICE_CONVERTED", "INVOICE_UPDATED", "INVOICE_ISSUED");
        assertThat(count("select count(*) from audit_logs where action = 'INVOICE_CONVERTED' "
                + "and new_values->>'estimateId' = ?", estimate.toString())).isEqualTo(3);
        assertThat(jdbc.queryForList("select coalesce(old_values->>'status', '') || '>' || (new_values->>'status') "
                + "from audit_logs where entity_type = 'ESTIMATE' and entity_id = ? and action = 'ESTIMATE_STATUS_CHANGED' "
                + "order by created_at, id", String.class, estimate))
                .containsExactly("DRAFT>SENT", "SENT>INVOICED", "INVOICED>SENT", "SENT>INVOICED");
    }

    @Test
    void concurrentConversionsOfOneEstimateCreateExactlyOneInvoice() throws Exception {
        UUID estimate = sentEstimate(leadId, 0, List.of(line(productA, 1)));
        List<Integer> statuses = concurrently(6, () -> convert(admin, estimate).andReturn().getResponse().getStatus());
        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
        assertThat(count("select count(*) from invoices where estimate_id = ?", estimate)).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------------ numbering

    @Test
    void numbersAreAllocatedOnIssueUniqueAndGaplessUnderConcurrency() throws Exception {
        List<UUID> drafts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            drafts.add(id(create(admin, invoice(leadId, "lines", List.of(line(productB, 1)))).andReturn()));
        }
        assertThat(count("select count(*) from invoices where id in (" + placeholders(drafts.size())
                + ") and invoice_number is null", drafts.toArray())).isEqualTo(8);
        List<String> numbers = concurrently(drafts.size(), new ArrayList<>(drafts).stream().<Callable<String>>map(id -> () ->
                JsonPath.read(issue(admin, id, 0, null).andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString(), "$.data.invoiceNumber")).toList());
        assertThat(new HashSet<>(numbers)).hasSize(8);
        List<Integer> sequence = numbers.stream().map(InvoiceApiIT::sequence).sorted().toList();
        assertThat(sequence.get(7) - sequence.get(0)).isEqualTo(7);
        int year = LocalDate.now(ZoneId.of("Asia/Kolkata")).getYear();
        assertThat(numbers).allMatch(n -> n.matches("INV-" + year + "-\\d{6}"));
    }

    // ------------------------------------------------------------------------------------------------ immutability

    @Test
    void issuedInvoicesCantBeChangedThroughTheApiOrTheDatabase() throws Exception {
        UUID id = issuedInvoice();
        long version = count("select version from invoices where id = ?", id);
        update(admin, id, invoice(leadId, "version", version, "lines", List.of(line(productA, 5))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVOICE_ISSUED"));
        mvc.perform(withJson(post("/api/v1/invoices/preview").param("invoiceId", id.toString()).with(bearer(admin)),
                body(invoice(leadId)))).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/invoices/{id}", id).with(bearer(sa)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVOICE_ISSUED"));
        issue(admin, id, version, null).andExpect(status().isConflict());

        String number = jdbc.queryForObject("select invoice_number from invoices where id = ?", String.class, id);
        assertThatThrownBy(() -> jdbc.update("update invoices set grand_total = 1 where id = ?", id))
                .hasMessageContaining("can't be changed");
        assertThatThrownBy(() -> jdbc.update("update invoices set invoice_number = 'INV-X' where id = ?", id))
                .hasMessageContaining("can't be changed");
        assertThatThrownBy(() -> jdbc.update("update invoices set billing_name = 'Someone else' where id = ?", id))
                .hasMessageContaining("can't be changed");
        assertThatThrownBy(() -> jdbc.update("update invoices set status = 'DRAFT', invoice_number = null where id = ?", id))
                .hasMessageContaining("can't go back");
        assertThatThrownBy(() -> jdbc.update("update invoice_items set unit_price = 1 where invoice_id = ?", id))
                .hasMessageContaining("can't be changed");
        assertThatThrownBy(() -> jdbc.update("update invoice_items set cgst_amount = 0, sgst_amount = 0, tax_amount = 0, "
                + "total_amount = taxable_value where invoice_id = ?", id)).hasMessageContaining("can't be changed");
        assertThatThrownBy(() -> jdbc.update("delete from invoice_items where invoice_id = ?", id))
                .hasMessageContaining("can't be changed");
        assertThatThrownBy(() -> jdbc.update("delete from invoices where id = ?", id))
                .hasMessageContaining("can't be deleted");
        assertThat(jdbc.queryForObject("select invoice_number from invoices where id = ?", String.class, id)).isEqualTo(number);
    }

    // ------------------------------------------------------------------------------------------------ unsupported

    /** Phase 10 approval, item 1: unsupported GST transactions are blocked instead of issued as ordinary invoices. */
    @Test
    void onlyRegularDomesticTaxableSuppliesAreIssued() throws Exception {
        UUID id = id(create(admin, invoice(leadId, "lines", List.of(line(productA, 1)))).andReturn());
        mvc.perform(withJson(post("/api/v1/invoices/{id}/issue", id).with(bearer(admin)), body(Map.of("version", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("regularSupplyConfirmed"));

        registerSupplier("36", SUPPLIER_GSTIN, true);
        issue(admin, id, 0, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("EINVOICING_NOT_SUPPORTED"));
        jdbc.update("update company_settings set einvoicing_applicable = null");
        issue(admin, id, 0, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("SUPPLIER_REGISTRATION_INCOMPLETE"));
        mvc.perform(get("/api/v1/settings/gst-registration").with(bearer(sa)))
                .andExpect(jsonPath("$.data.complete").value(false));
        registerSupplier("36", SUPPLIER_GSTIN);

        // Every line at 0% would need a bill of supply.
        UUID exempt = id(create(sa, invoice(leadId, "lines", List.of(installation("Exempt site work", 1, "800", "0")))).andReturn());
        issue(admin, exempt, 0, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("BILL_OF_SUPPLY_NOT_SUPPORTED"));
        assertThat(count("select count(*) from invoices where id in (?, ?) and status = 'DRAFT'", id, exempt)).isEqualTo(2);

        String issued = issue(admin, id, 0, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(issued, "$.data.invoiceNumber")).startsWith("INV-");
        assertThat(jdbc.queryForObject("select new_values->>'regularSupplyConfirmed' from audit_logs "
                + "where action = 'INVOICE_ISSUED' and entity_id = ?", String.class, id)).isEqualTo("true");
        String document = mvc.perform(get("/api/v1/invoices/{id}/document", id).with(bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(document).contains("Tax payable on reverse charge</span> No");
    }

    // ------------------------------------------------------------------------------------------------ cancellation

    @Test
    void cancellingNeedsInvoicesDeleteAReasonAndNoRecordedPayments() throws Exception {
        UUID id = issuedInvoice();
        long version = count("select version from invoices where id = ?", id);
        cancel(admin, id, version, "Duplicate invoice").andExpect(status().isForbidden());
        cancel(sa, id, version, "  ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("reason"));
        cancel(sa, id, version, "Too short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("reason"));

        jdbc.update("insert into payments (invoice_id, partner_id, payment_date, amount, net_amount, payment_method) "
                + "values (?, ?, current_date, 100, 84.75, 'UPI')", id, partnerId);
        cancel(sa, id, version, "Duplicate invoice").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVOICE_HAS_PAYMENTS"));
        mvc.perform(get("/api/v1/invoices/{id}", id).with(bearer(admin)))
                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PARTLY_PAID"))
                .andExpect(jsonPath("$.data.paymentStatusLabel").value("Partially paid"));
        mvc.perform(get("/api/v1/invoices").param("partnerId", partnerId.toString()).param("payment", "PARTIALLY_PAID")
                        .with(bearer(admin)))
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(id.toString()));

        UUID other = issuedInvoice();
        long otherVersion = count("select version from invoices where id = ?", other);
        cancel(sa, other, otherVersion, "Raised against the wrong client").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("Raised against the wrong client"));
        cancel(sa, other, otherVersion + 1, "Cancelled again by mistake").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVOICE_CANCELLED"));
        Map<String, Object> audit = jdbc.queryForMap("select user_id, new_values::text as nv from audit_logs "
                + "where action = 'INVOICE_CANCELLED' and entity_id = ?", other);
        assertThat(audit.get("user_id")).isNotNull();
        assertThat((String) audit.get("nv")).contains("Raised against the wrong client");
        assertThatThrownBy(() -> jdbc.update("update invoices set cancel_reason = 'x' where id = ?", other))
                .hasMessageContaining("cancellation can't be changed");
        assertThatThrownBy(() -> jdbc.update("update invoices set status = 'ISSUED', cancelled_at = null, "
                + "cancel_reason = null where id = ?", other)).hasMessageContaining("can't go back");

        // Drafts are discarded, not cancelled.
        UUID draft = id(create(admin, invoice(leadId, "lines", List.of(line(productA, 1)))).andReturn());
        cancel(sa, draft, 0, "Draft cannot be cancelled").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVOICE_DRAFT"));
    }

    @Test
    void thePaymentFilterFindsOverdueAndPaidInvoices() throws Exception {
        UUID overdue = rawIssuedInvoice("1180.00", LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(3));
        UUID paid = rawIssuedInvoice("500.00", LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(3));
        jdbc.update("insert into payments (invoice_id, partner_id, payment_date, amount, net_amount, payment_method) "
                + "values (?, ?, current_date, 499.60, 423.39, 'UPI')", paid, partnerId);
        assertThat(ids(get("/api/v1/invoices").param("partnerId", partnerId.toString()).param("payment", "OVERDUE")))
                .containsExactly(overdue.toString());
        assertThat(ids(get("/api/v1/invoices").param("partnerId", partnerId.toString()).param("payment", "PAID")))
                .containsExactly(paid.toString());
        assertThat(ids(get("/api/v1/invoices").param("partnerId", partnerId.toString()).param("payment", "UNPAID"))).isEmpty();
        mvc.perform(get("/api/v1/invoices/{id}", overdue).with(bearer(admin)))
                .andExpect(jsonPath("$.data.paymentStatus").value("OVERDUE"));
    }

    // ------------------------------------------------------------------------------------------------ security

    @Test
    void partnerSafeResponsesDocumentsExportsAndAuditEntriesNeverCarryB2cData() throws Exception {
        UUID id = issuedInvoice();
        for (Session session : List.of(admin, sa)) {
            assertPartnerSafe(content(get("/api/v1/invoices/{id}", id), session));
            assertPartnerSafe(content(get("/api/v1/invoices").param("q", tag), session));
            assertPartnerSafe(content(get("/api/v1/invoices/{id}/history", id), session));
            assertPartnerSafe(content(get("/api/v1/invoices/{id}/document", id), session));
        }
        String csv = content(get("/api/v1/invoices/export").param("q", tag), sa);
        assertThat(csv).contains("invoice,status,invoice_date");
        assertPartnerSafe(csv);
        String preview = mvc.perform(withJson(post("/api/v1/invoices/preview").with(bearer(admin)),
                        body(invoice(leadId, "lines", List.of(line(productA, 2)))))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internal").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertPartnerSafe(preview);
        for (String row : jdbc.queryForList("select coalesce(old_values::text, '') || coalesce(new_values::text, '') "
                + "from audit_logs where entity_type = 'INVOICE' and entity_id = ?", String.class, id)) {
            assertPartnerSafe(row);
        }
        mvc.perform(withJson(post("/api/v1/invoices/preview").with(bearer(sa)),
                        body(invoice(leadId, "lines", List.of(line(productA, 2))))))
                .andExpect(jsonPath("$.data.internal.lines[0].b2cName").value(secretB2cName));
    }

    @Test
    void theInternalCopyNeedsTheInternalB2cPermissionAndExistsOnceIssued() throws Exception {
        UUID draft = id(create(admin, invoice(leadId, "lines", List.of(line(productA, 2), line(productB, 1)))).andReturn());
        mvc.perform(get("/api/v1/internal/invoices/{id}", draft).with(bearer(sa)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INTERNAL_COPY_NOT_READY"));
        issue(admin, draft, 0, null).andExpect(status().isOk());
        mvc.perform(get("/api/v1/internal/invoices/{id}", draft).with(bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/internal/invoices/{id}/document", draft).with(bearer(admin))).andExpect(status().isForbidden());
        String number = jdbc.queryForObject("select invoice_number from invoices where id = ?", String.class, draft);
        mvc.perform(get("/api/v1/internal/invoices/{id}", draft).with(bearer(sa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internalNumber").value("INT-" + number))
                .andExpect(jsonPath("$.data.lines[0].b2cName").value(secretB2cName))
                .andExpect(jsonPath("$.data.missingB2cPrices").value(1));
        assertThat(content(get("/api/v1/internal/invoices/{id}/document", draft), sa))
                .contains("INTERNAL ONLY", secretB2cName, "Partner document: " + number);
    }

    @Test
    void invoicesWritersPickLeadsAndProductsWithoutLeadsOrProductsReadAndRatesAreProtected() throws Exception {
        Session writer = login(user(List.of(module("INVOICES", true)), List.of()));
        mvc.perform(get("/api/v1/leads").with(bearer(writer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/products").with(bearer(writer))).andExpect(status().isForbidden());
        String leads = content(get("/api/v1/invoices/lead-options").param("q", tag), writer);
        assertThat((List<?>) JsonPath.read(leads, "$.data")).hasSize(1);
        assertThat(JsonPath.<Map<String, Object>>read(leads, "$.data[0]")).containsOnlyKeys("id", "leadNumber", "clientName", "partner");
        String products = content(get("/api/v1/products/options").param("q", tag), writer);
        assertThat(products).contains(tag + "-A").doesNotContain(secretB2cName, B2C_PRICE);

        String created = content(post("/api/v1/invoices"), writer, invoice(leadId, "lines", List.of(line(productA, 1))));
        UUID id = id(created);
        // Product lines are priced by the product sheet: a different GST rate or price in the request is ignored,
        // for writers and approvers alike. The line stays at the sheet's 18% and 1000.
        Map<String, Object> tampered = withGst(line(productA, 1), 12);
        tampered.put("unitPrice", new BigDecimal("1.00"));
        update(writer, id, invoice(leadId, "version", 0, "lines", List.of(tampered, line(productB, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[0].gstPercent").value(18.0))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(1000.0))
                .andExpect(jsonPath("$.data.lines[1].gstPercent").value(5.0));
        Session approver = login(user(List.of(module("INVOICES", true, "approve", true)), List.of()));
        update(approver, id, invoice(leadId, "version", 1, "lines", List.of(withGst(line(productA, 1), 12))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.lines[0].gstPercent").value(18.0));
        // Fee overrides still need Partners approve (D-13).
        create(writer, invoice(leadId, "feePercent", 15, "lines", List.of(line(productA, 1))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FEE_OVERRIDE_NOT_PERMITTED"));

        Session none = login(user(List.of(module("ESTIMATES", true)), List.of()));
        mvc.perform(get("/api/v1/invoices").with(bearer(none))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/invoices/lead-options").with(bearer(none))).andExpect(status().isForbidden());
        Session reader = login(user(List.of(module("INVOICES", false)), List.of()));
        mvc.perform(withJson(post("/api/v1/invoices").with(bearer(reader)), body(invoice(leadId))))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/v1/invoices/from-estimate").with(bearer(writer)),
                body(Map.of("estimateId", UUID.randomUUID())))).andExpect(status().isForbidden());
    }

    @Test
    void restrictedDataScopesSeeNoInvoices() throws Exception {
        UUID id = id(create(admin, invoice(leadId, "lines", List.of(line(productA, 1)))).andReturn());
        Map<String, Object> scoped = module("INVOICES", true);
        scoped.put("dataScope", "SELF");
        Session session = login(user(List.of(scoped), List.of()));
        mvc.perform(get("/api/v1/invoices").param("q", tag).with(bearer(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/invoices/{id}", id).with(bearer(session))).andExpect(status().isNotFound());
        issue(session, id, 0, null).andExpect(status().isNotFound());
        assertThat((List<?>) JsonPath.read(content(get("/api/v1/invoices/lead-options"), session), "$.data")).isEmpty();
    }

    @Test
    void theListSearchesAndFiltersByStatus() throws Exception {
        UUID draft = id(create(admin, invoice(leadId, "billingName", "Searchable " + tag,
                "lines", List.of(line(productA, 1)))).andReturn());
        UUID issued = issuedInvoice();
        assertThat(ids(get("/api/v1/invoices").param("q", "searchable " + tag.toLowerCase(Locale.ROOT))))
                .containsExactly(draft.toString());
        assertThat(ids(get("/api/v1/invoices").param("partnerId", partnerId.toString()).param("status", "ISSUED")))
                .containsExactly(issued.toString());
        String number = jdbc.queryForObject("select invoice_number from invoices where id = ?", String.class, issued);
        assertThat(ids(get("/api/v1/invoices").param("q", number))).containsExactly(issued.toString());
        String meta = content(get("/api/v1/invoices/meta"), admin);
        assertThat((List<?>) JsonPath.read(meta, "$.data.states")).hasSize(36);
        assertThat((Boolean) JsonPath.read(meta, "$.data.canApprove")).isFalse();
        assertThat((Boolean) JsonPath.read(meta, "$.data.canCancel")).isFalse();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** No B2C names, B2C rates, B2C totals, uplift or twin number in text meant for partners. */
    private void assertPartnerSafe(String text) {
        assertThat(text).doesNotContain(secretB2cName, "B2C plain " + tag, B2C_PRICE, "1,543.21", "INT-INV");
        assertThat(text.toLowerCase(Locale.ROOT)).doesNotContain("b2c", "uplift");
    }

    private void registerSupplier(String state, String gstin, boolean einvoicing) throws Exception {
        String name = jdbc.queryForObject("select company_name from company_settings", String.class);
        mvc.perform(withJson(put("/api/v1/settings/gst-registration").with(bearer(sa)),
                        body(Map.of("legalName", name, "address", "Plot 7, Test Park, Hyderabad 500081", "stateCode", state,
                                "gstin", gstin, "einvoicingApplicable", einvoicing, "version", settingsVersion()))))
                .andExpect(status().isOk());
    }

    private void registerSupplier(String state, String gstin) throws Exception {
        registerSupplier(state, gstin, false);
    }

    private long settingsVersion() {
        return count("select version from company_settings");
    }

    private Map<String, Object> invoice(UUID lead, Object... overrides) {
        Map<String, Object> map = new HashMap<>();
        map.put("leadId", lead);
        map.put("billingName", "Client " + tag);
        map.put("billingAddress", "Road 1, Hyderabad");
        map.put("billingStateCode", "36");
        map.put("placeOfSupplyCode", "36");
        map.put("placeOfSupplyBasis", "DELIVERY_LOCATION");
        for (int i = 0; i + 1 < overrides.length; i += 2) {
            map.put((String) overrides[i], overrides[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> conversionBilling(long version) {
        return Map.of("billingName", "Converted client", "billingAddress", "Road 2, Hyderabad", "billingStateCode", "36",
                "placeOfSupplyCode", "36", "placeOfSupplyBasis", "INSTALLATION_SITE", "version", version);
    }

    private static Map<String, Object> line(UUID productId, Object quantity) {
        Map<String, Object> map = new HashMap<>();
        map.put("productId", productId);
        map.put("quantity", quantity);
        return map;
    }

    private static Map<String, Object> withGst(Map<String, Object> line, Object gst) {
        line.put("gstPercent", gst);
        return line;
    }

    private UUID issuedInvoice() throws Exception {
        UUID id = id(create(admin, invoice(leadId, "lines", List.of(line(productA, 1)))).andReturn());
        issue(admin, id, 0, null).andExpect(status().isOk());
        return id;
    }

    /** An issued invoice inserted directly (no lines), to control the due date for the payment filter. */
    private UUID rawIssuedInvoice(String total, LocalDate due) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into invoices (id, invoice_number, partner_id, invoice_date, due_date, status, issued_at, "
                        + "tax_type, taxable_amount, grand_total) values (?, ?, ?, ?, ?, 'ISSUED', now(), 'INTRA', ?, ?)",
                id, "TST-" + id.toString().substring(0, 12), partnerId, due.minusDays(15), due, new BigDecimal(total),
                new BigDecimal(total));
        return id;
    }

    private static Map<String, Object> installation(String description, Object quantity, String rate, String gst) {
        Map<String, Object> map = new HashMap<>();
        map.put("kind", "INSTALLATION");
        map.put("description", description);
        map.put("quantity", quantity);
        map.put("unitPrice", new BigDecimal(rate));
        map.put("gstPercent", new BigDecimal(gst));
        return map;
    }

    private ResultActions create(Session session, Map<String, Object> request) throws Exception {
        return mvc.perform(withJson(post("/api/v1/invoices").with(bearer(session)), body(request)));
    }

    private ResultActions update(Session session, UUID id, Map<String, Object> request) throws Exception {
        return mvc.perform(withJson(put("/api/v1/invoices/{id}", id).with(bearer(session)), body(request)));
    }

    private ResultActions issue(Session session, UUID id, long version, String expectedTotal) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("version", version);
        request.put("regularSupplyConfirmed", true);
        if (expectedTotal != null) {
            request.put("expectedGrandTotal", new BigDecimal(expectedTotal));
        }
        return mvc.perform(withJson(post("/api/v1/invoices/{id}/issue", id).with(bearer(session)), body(request)));
    }

    private ResultActions cancel(Session session, UUID id, long version, String reason) throws Exception {
        return mvc.perform(withJson(post("/api/v1/invoices/{id}/cancel", id).with(bearer(session)),
                body(Map.of("version", version, "reason", reason))));
    }

    private ResultActions convert(Session session, UUID estimate) throws Exception {
        return mvc.perform(withJson(post("/api/v1/invoices/from-estimate").with(bearer(session)),
                body(Map.of("estimateId", estimate))));
    }

    private UUID estimateId(UUID lead, double discount, List<Map<String, Object>> lines) throws Exception {
        return id(content(post("/api/v1/estimates"), admin, Map.of("leadId", lead, "igst", false,
                "discountPercent", discount, "lines", lines)));
    }

    private UUID sentEstimate(UUID lead, double discount, List<Map<String, Object>> lines) throws Exception {
        UUID id = estimateId(lead, discount, lines);
        mvc.perform(post("/api/v1/estimates/{id}/send", id).with(bearer(admin))).andExpect(status().isOk());
        return id;
    }

    private String estimateSnapshot(UUID estimate) {
        return jdbc.queryForObject("select status || '|' || subtotal || '|' || taxable_amount || '|' || gst_amount || '|' "
                + "|| grand_total || '|' || (select string_agg(quantity || 'x' || unit_price || '@' || gst_percent, ',' "
                + "order by line_no) from estimate_items where estimate_id = e.id) from estimates e where id = ?",
                String.class, estimate);
    }

    /** Number, status, supplier, totals and lines of an invoice as stored. */
    private String invoiceSnapshot(UUID id) {
        return jdbc.queryForObject("select invoice_number || '|' || status || '|' || supplier_state_code || '|' "
                + "|| supplier_gstin || '|' || supplier_name || '|' || taxable_amount || '|' || cgst_amount || '|' "
                + "|| sgst_amount || '|' || igst_amount || '|' || grand_total || '|' || invoice_date || '|' "
                + "|| (select string_agg(quantity || 'x' || unit_price || '=' || taxable_value || '+' || tax_amount, ',' "
                + "order by line_no) from invoice_items where invoice_id = i.id) from invoices i where id = ?",
                String.class, id);
    }

    private List<String> actions(UUID id) {
        return jdbc.queryForList("select action from audit_logs where entity_type = 'INVOICE' and entity_id = ? "
                + "order by created_at, id", String.class, id);
    }

    private List<String> ids(MockHttpServletRequestBuilder request) throws Exception {
        return JsonPath.read(content(request, admin), "$.data[*].id");
    }

    private static int sequence(String number) {
        return Integer.parseInt(number.substring(number.lastIndexOf('-') + 1));
    }

    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
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

    private static void assertLine(String body, int index, String taxable, String cgst, String sgst, String igst,
                                   String tax, String total) {
        String p = "$.data.lines[" + index + "].";
        assertThat(decimal(body, p + "taxableValue")).isEqualByComparingTo(taxable);
        assertThat(decimal(body, p + "cgstAmount")).isEqualByComparingTo(cgst);
        assertThat(decimal(body, p + "sgstAmount")).isEqualByComparingTo(sgst);
        assertThat(decimal(body, p + "igstAmount")).isEqualByComparingTo(igst);
        assertThat(decimal(body, p + "taxAmount")).isEqualByComparingTo(tax);
        assertThat(decimal(body, p + "lineTotal")).isEqualByComparingTo(total);
    }

    private static UUID id(String body) {
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private static UUID id(MvcResult result) throws Exception {
        return id(result.getResponse().getContentAsString());
    }

    private static BigDecimal decimal(String body, String path) {
        Object value = JsonPath.read(body, path);
        return new BigDecimal(value.toString());
    }

    private UUID partner(String name) throws Exception {
        return id(content(post("/api/v1/partners"), sa, Map.of("name", name, "category", "INTERIOR_DESIGNER")));
    }

    private UUID lead(String clientName) throws Exception {
        return id(content(post("/api/v1/leads"), admin, Map.of("clientName", clientName, "partnerId", partnerId,
                "projectType", "VILLA", "city", "Hyderabad")));
    }

    private UUID product(String suffix, String partnerPrice, String b2cPrice, String b2cName, int gst) throws Exception {
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
        return id(content(post("/api/v1/products"), sa, p));
    }

    private static Map<String, Object> module(String code, boolean write, Object... flags) {
        Map<String, Object> map = new HashMap<>();
        map.put("moduleCode", code);
        map.put("read", true);
        map.put("write", write);
        for (int i = 0; i + 1 < flags.length; i += 2) {
            map.put((String) flags[i], flags[i + 1]);
        }
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
