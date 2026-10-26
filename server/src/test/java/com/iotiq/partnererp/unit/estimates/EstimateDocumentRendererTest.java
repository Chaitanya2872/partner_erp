package com.iotiq.partnererp.unit.estimates;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.common.enums.LeadStage;
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
    void bulkPurchasesBillThePartnerAndIgstIsASingleLine() {
        String html = EstimateDocumentRenderer.partner(estimate(BillTo.PARTNER, true), company());
        assertThat(html).contains("Billed to partner", "IGST").doesNotContain("CGST", "98765");
    }

    @Test
    void theInternalCopyIsStampedAndShowsB2cNamesAndTheUplift() {
        InternalEstimateDocument twin = new InternalEstimateDocument(UUID.randomUUID(), "INT-EST-2026-000007",
                "EST-2026-000007", DATE, EstimateStatus.DRAFT, "draft", BigDecimal.ZERO, false,
                List.of(new InternalEstimateDocument.Line(1, "Glide Pro B2C", "Smart <Glide> & Co", "8536", "nos",
                        new BigDecimal("2.000"), new BigDecimal("0.00"), new BigDecimal("18.000"),
                        new BigDecimal("0.00"), true)),
                totals("0"), totals("123456"), new BigDecimal("-123456.00"), true, 1, null);

        String html = EstimateDocumentRenderer.internal(estimate(BillTo.CLIENT, false), twin, company());

        assertThat(html).contains("INTERNAL ONLY", "INT-EST-2026-000007", "Partner document: EST-2026-000007",
                "Glide Pro B2C", "Partner copy name: Smart &lt;Glide&gt; &amp; Co", "No B2C price set",
                "Partner discount vs B2C list: -₹1,23,456.00", "1 line(s) have no B2C price");
    }

    private static EstimateResponse estimate(BillTo billTo, boolean igst) {
        EstimateLeadRef lead = new EstimateLeadRef(UUID.randomUUID(), "LEAD-2026-000001", "Rao \"Villa\"",
                "98765 43210", "Hyderabad", "Villa", LeadStage.NEW, "New", false);
        EstimateResponse.Line line = new EstimateResponse.Line(UUID.randomUUID(), 1, UUID.randomUUID(), "SG-10F", true,
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
