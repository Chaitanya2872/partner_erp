package com.iotiq.partnererp.unit.estimates;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.common.persistence.LineKind;
import com.iotiq.partnererp.common.util.DocumentCalculator;
import com.iotiq.partnererp.modules.estimates.dto.EstimateLeadRef;
import com.iotiq.partnererp.modules.estimates.dto.EstimateResponse;
import com.iotiq.partnererp.modules.estimates.dto.InternalEstimateDocument;
import com.iotiq.partnererp.modules.estimates.dto.TotalsResponse;
import com.iotiq.partnererp.modules.estimates.entity.EstimateStatus;
import com.iotiq.partnererp.modules.estimates.service.EstimateDocumentRenderer;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;
import com.iotiq.partnererp.modules.settings.entity.CompanySettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EstimateDocumentRendererTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 26);

    @Test
    void thePartnerCopyShowsPartnerValuesEscapedWithIndianGrouping() {
        String html = EstimateDocumentRenderer.partner(estimate(BillTo.CLIENT, false), company());

        assertThat(html).contains("EST-2026-000007", "Valid till 10 Nov 2026", "Smart &lt;Glide&gt; &amp; Co",
                "Rao &quot;Villa&quot;", "Referred by", "Alpha Interiors", "₹1,23,456.00", "CGST", "SGST",
                "₹11,111.04", "₹11,111.04", "Prices exclude civil work.");
        assertThat(html).doesNotContain("<Glide>", "INTERNAL", "IGST", "B2C");
    }

    @Test
    void thePartnerCopyFollowsTheTemplateLayout() {
        CompanySettings company = company();
        company.setTerms("Delivery in 15 days.\n\nWarranty as per <product> terms.");
        String html = EstimateDocumentRenderer.partner(estimate(BillTo.CLIENT, false), company);

        assertThat(html).contains("class=\"hd\"", "ESTIMATE", "Partner copy", "Estimate details", "Client / project",
                "Hyderabad", "Intra-state (CGST + SGST)", "<th>Code</th>", "SG-10F", "Amount in words:",
                "Rupees One Lakh Forty Five Thousand Six Hundred Seventy Eight and Eight Paise Only", "GRAND TOTAL",
                "Terms &amp; conditions", "<li>Delivery in 15 days.</li>", "Warranty as per &lt;product&gt; terms.",
                "Notes", "Accepted by", "@page{size:A4", "@media print");
        assertThat(html).doesNotContain("<product>", "INTERNAL", "B2C");
    }

    @Test
    void bulkPurchasesBillThePartnerAndIgstIsASingleLine() {
        String html = EstimateDocumentRenderer.partner(estimate(BillTo.PARTNER, true), company());
        assertThat(html).contains("Billed to partner", "IGST").doesNotContain("CGST", "98765");
    }

    @Test
    void theInternalCopyIsStampedAndShowsB2cNamesAndTheUplift() {
        InternalEstimateDocument twin = new InternalEstimateDocument(UUID.randomUUID(), "INT-EST-2026-000007",
                "EST-2026-000007", DATE, EstimateStatus.DRAFT, "draft", BigDecimal.ZERO, false,
                List.of(new InternalEstimateDocument.Line(1, LineKind.PRODUCT, "Glide Pro B2C", "Smart <Glide> & Co", "8536", "nos",
                        new BigDecimal("2.000"), new BigDecimal("0.00"), new BigDecimal("18.000"),
                        new BigDecimal("0.00"), true)),
                totals("0"), totals("123456"), new BigDecimal("-123456.00"), true, 1, null);

        String html = EstimateDocumentRenderer.internal(estimate(BillTo.CLIENT, false), twin, company());

        assertThat(html).contains("INTERNAL ONLY", "INT-EST-2026-000007", "Partner document: EST-2026-000007",
                "Glide Pro B2C", "Partner copy name: Smart &lt;Glide&gt; &amp; Co", "No B2C price set",
                "Partner discount vs B2C list: -₹1,23,456.00", "1 line(s) have no B2C price");
    }

    @Test
    void installationLinesGetTheirOwnSectionAndSubtotalOnBothCopies() {
        EstimateResponse base = estimate(BillTo.CLIENT, false);
        EstimateResponse.Line install = new EstimateResponse.Line(UUID.randomUUID(), 2, LineKind.INSTALLATION, null,
                null, true, "Fit rails <on site>", null, "job", new BigDecimal("2.000"), new BigDecimal("500.00"),
                new BigDecimal("12.000"), new BigDecimal("1000.00"));
        EstimateResponse e = new EstimateResponse(base.id(), base.estimateNumber(), base.estimateDate(),
                base.validUntil(), base.status(), base.statusLabel(), false, null, base.lead(), base.partner(),
                base.billTo(), base.billToLabel(), base.discountPercent(), base.igst(), base.feePercent(), base.notes(),
                List.of(base.lines().get(0), install), base.totals(), null, null, null, 0);

        String partner = EstimateDocumentRenderer.partner(e, company());
        assertThat(partner).contains("Installation", "Fit rails &lt;on site&gt;", "Installation subtotal", "₹1,000.00");
        assertThat(partner.indexOf("Smart &lt;Glide&gt;")).isLessThan(partner.indexOf(">Installation<"));

        InternalEstimateDocument twin = new InternalEstimateDocument(UUID.randomUUID(), "INT-EST-2026-000007",
                "EST-2026-000007", DATE, EstimateStatus.DRAFT, "draft", BigDecimal.ZERO, false,
                List.of(new InternalEstimateDocument.Line(1, LineKind.PRODUCT, "Glide Pro B2C", "Smart Glide", "8536",
                                "nos", BigDecimal.ONE, new BigDecimal("10.00"), new BigDecimal("18.000"),
                                new BigDecimal("10.00"), false),
                        new InternalEstimateDocument.Line(2, LineKind.INSTALLATION, "Fit rails", "Fit rails", null,
                                "job", new BigDecimal("2.000"), new BigDecimal("500.00"), new BigDecimal("12.000"),
                                new BigDecimal("1000.00"), false)),
                totals("1010"), totals("1010"), BigDecimal.ZERO, false, 0, null);
        String internal = EstimateDocumentRenderer.internal(e, twin, company());
        assertThat(internal).contains(">Installation<", "Installation subtotal", "Fit rails")
                .doesNotContain("No B2C price set");
    }

    private static EstimateResponse estimate(BillTo billTo, boolean igst) {
        EstimateLeadRef lead = new EstimateLeadRef(UUID.randomUUID(), "LEAD-2026-000001", "Rao \"Villa\"",
                "98765 43210", "Hyderabad", "Villa", LeadStage.NEW, "New", false);
        EstimateResponse.Line line = new EstimateResponse.Line(UUID.randomUUID(), 1, LineKind.PRODUCT, UUID.randomUUID(), "SG-10F", true,
                "Smart <Glide> & Co", "8536", "nos", new BigDecimal("2.000"), new BigDecimal("61728.00"),
                new BigDecimal("18.000"), new BigDecimal("123456.00"));
        return new EstimateResponse(UUID.randomUUID(), "EST-2026-000007", DATE, DATE.plusDays(15),
                EstimateStatus.DRAFT, "draft", false, null, lead, new PartnerRef(UUID.randomUUID(), "Alpha Interiors"),
                billTo, billTo.getLabel(), BigDecimal.ZERO, igst, new BigDecimal("8.000"), "Prices exclude civil work.",
                List.of(line), totals("123456"), null, null, null, 0);
    }

    private static TotalsResponse totals(String taxable) {
        BigDecimal t = new BigDecimal(taxable);
        return TotalsResponse.of(DocumentCalculator.totals(
                List.of(new DocumentCalculator.Line(BigDecimal.ONE, t, new BigDecimal("18"))), BigDecimal.ZERO));
    }

    private static CompanySettings company() {
        CompanySettings c = new CompanySettings();
        c.setCompanyName("IOTIQ");
        c.setAddress("Madhapur, Hyderabad");
        c.setPhone("8121234162");
        c.setEmail("sales@example.test");
        return c;
    }
}
