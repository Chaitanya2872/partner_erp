package com.iotiq.partnererp.integration.persistence;

import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.common.persistence.DocumentTotals;
import com.iotiq.partnererp.integration.support.AbstractIntegrationTest;
import com.iotiq.partnererp.modules.audit.entity.AuditLog;
import com.iotiq.partnererp.modules.audit.repository.AuditLogRepository;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.repository.RoleRepository;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.b2c.entity.B2CDocument;
import com.iotiq.partnererp.modules.b2c.entity.B2CDocumentItem;
import com.iotiq.partnererp.modules.b2c.entity.B2CDocumentType;
import com.iotiq.partnererp.modules.b2c.repository.B2CDocumentRepository;
import com.iotiq.partnererp.modules.estimates.entity.Estimate;
import com.iotiq.partnererp.modules.estimates.entity.EstimateItem;
import com.iotiq.partnererp.modules.estimates.repository.EstimateRepository;
import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.modules.invoices.entity.Invoice;
import com.iotiq.partnererp.modules.invoices.tax.TaxType;
import com.iotiq.partnererp.modules.invoices.entity.InvoiceItem;
import com.iotiq.partnererp.modules.invoices.repository.InvoiceRepository;
import com.iotiq.partnererp.modules.leads.entity.Lead;
import com.iotiq.partnererp.modules.leads.entity.ProjectType;
import com.iotiq.partnererp.modules.leads.repository.LeadRepository;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import com.iotiq.partnererp.modules.partners.entity.PartnerCategory;
import com.iotiq.partnererp.modules.partners.repository.PartnerRepository;
import com.iotiq.partnererp.modules.payments.entity.Payment;
import com.iotiq.partnererp.modules.payments.entity.PaymentMethod;
import com.iotiq.partnererp.modules.payments.repository.PaymentRepository;
import com.iotiq.partnererp.modules.payouts.entity.PartnerPayout;
import com.iotiq.partnererp.modules.payouts.entity.PayoutStatus;
import com.iotiq.partnererp.modules.payouts.repository.PartnerPayoutRepository;
import com.iotiq.partnererp.modules.products.entity.Product;
import com.iotiq.partnererp.modules.products.entity.ProductCategory;
import com.iotiq.partnererp.modules.products.repository.ProductRepository;
import com.iotiq.partnererp.modules.sequences.entity.DocumentSequence;
import com.iotiq.partnererp.modules.sequences.repository.DocumentSequenceRepository;
import com.iotiq.partnererp.modules.settings.repository.PartnerTierRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entity mappings round-trip through the real schema. Every test runs in a transaction that is
 * rolled back afterwards.
 */
@Transactional
class PersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private PartnerTierRepository tierRepository;
    @Autowired
    private PartnerRepository partnerRepository;
    @Autowired
    private LeadRepository leadRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private EstimateRepository estimateRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private B2CDocumentRepository b2cRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PartnerPayoutRepository payoutRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private DocumentSequenceRepository sequenceRepository;

    @Test
    void partnerAndLeadRoundTrip() {
        Partner partner = newPartner();
        Lead lead = newLead(partner, "+91 98765 43210");
        flushAndClear();

        Partner reloaded = partnerRepository.findById(partner.getId()).orElseThrow();
        assertThat(reloaded.getTier().getTierLevel()).isEqualTo((short) 1);
        assertThat(reloaded.getAgreementVersion()).isEqualTo("v2.1");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isZero();

        Lead reloadedLead = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloadedLead.getStage()).isEqualTo(LeadStage.NEW);
        assertThat(reloadedLead.getClientPhoneDigits()).isEqualTo("9876543210");
        assertThat(leadRepository.findByClientPhoneDigits("9876543210")).extracting(Lead::getId).contains(lead.getId());

        reloaded.setName("Renamed Partner");
        flushAndClear();
        assertThat(partnerRepository.findById(partner.getId()).orElseThrow().getVersion()).isEqualTo(1);
    }

    @Test
    void productAliasesRoundTrip() {
        Product product = new Product();
        product.setSku("TEST-ALIAS");
        product.setCategory(ProductCategory.SERVICES);
        product.setB2cName("Installation");
        product.setPartnerName("Installation");
        product.setPartnerPrice(new BigDecimal("1500.00"));
        product.setGstPercent(new BigDecimal("18"));
        product.setAliases(List.of("install", "fitting"));
        productRepository.save(product);
        flushAndClear();

        Product reloaded = productRepository.findBySkuIgnoreCase("test-alias").orElseThrow();
        assertThat(reloaded.getAliases()).containsExactly("install", "fitting");
        assertThat(reloaded.getB2cPrice()).isNull();
    }

    @Test
    void salesDocumentGraphRoundTrip() {
        Partner partner = newPartner();
        Lead lead = newLead(partner, "9000000002");
        Product product = productRepository.findBySkuIgnoreCase("STI-2M").orElseThrow();

        Estimate estimate = new Estimate();
        estimate.setEstimateNumber("EST-T-0001");
        estimate.setPartner(partner);
        estimate.setLead(lead);
        estimate.setEstimateDate(LocalDate.of(2026, 10, 1));
        estimate.setFeePercent(new BigDecimal("8"));
        estimate.setTotals(totals("11498.00", "0", "11498.00", "2069.64", "13567.64"));
        estimate.addItem(estimateLine(2, product));
        estimate.addItem(estimateLine(1, product));
        estimateRepository.save(estimate);

        B2CDocument twin = new B2CDocument();
        twin.setDocumentType(B2CDocumentType.ESTIMATE);
        twin.setEstimate(estimate);
        twin.setInternalNumber("INT-EST-T-0001");
        twin.setParentNumber("EST-T-0001");
        twin.setPartner(partner);
        twin.setLead(lead);
        twin.setDocumentDate(estimate.getEstimateDate());
        twin.setPartnerTotals(totals("11498.00", "0", "11498.00", "2069.64", "13567.64"));
        twin.setB2cIncomplete(true);
        B2CDocumentItem twinLine = new B2CDocumentItem();
        twinLine.setLineNo(1);
        twinLine.setProduct(product);
        twinLine.setDescription(product.getB2cName());
        twinLine.setPartnerName(product.getPartnerName());
        twinLine.setQuantity(new BigDecimal("2"));
        twinLine.setUnitPrice(BigDecimal.ZERO);
        twinLine.setGstPercent(new BigDecimal("18"));
        twinLine.setLineTotal(BigDecimal.ZERO);
        twin.addItem(twinLine);
        b2cRepository.save(twin);

        // Lines go in while the invoice is a draft: V16 triggers freeze the lines of issued invoices.
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setPartner(partner);
        invoice.setLead(lead);
        invoice.setEstimate(estimate);
        invoice.setFeePercent(new BigDecimal("8"));
        invoice.setTotals(totals("5749.00", "0", "5749.00", "1034.82", "6783.82"));
        InvoiceItem invoiceLine = new InvoiceItem();
        copyLine(estimateLine(1, product), invoiceLine);
        invoice.addItem(invoiceLine);
        invoiceRepository.saveAndFlush(invoice);
        invoice.setInvoiceNumber("INV-T-0001");
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.parse("2026-10-05T05:00:00Z"));
        invoice.setTaxType(TaxType.INTRA);
        invoice.setInvoiceDate(LocalDate.of(2026, 10, 5));
        invoice.setDueDate(LocalDate.of(2026, 10, 20));
        invoiceRepository.saveAndFlush(invoice);

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setPartner(partner);
        payment.setLead(lead);
        payment.setPaymentDate(LocalDate.of(2026, 10, 6));
        payment.setAmount(new BigDecimal("6783.82"));
        payment.setNetAmount(new BigDecimal("5749.00"));
        payment.setPaymentMethod(PaymentMethod.NEFT_RTGS);
        paymentRepository.save(payment);

        PartnerPayout payout = new PartnerPayout();
        payout.setPayment(payment);
        payout.setPartner(partner);
        payout.setInvoice(invoice);
        payout.setBaseAmount(new BigDecimal("5749.00"));
        payout.setCommissionPercent(new BigDecimal("8"));
        payout.setGrossCommission(new BigDecimal("459.92"));
        payout.setAccruedOn(payment.getPaymentDate());
        payout.setDueDate(LocalDate.of(2026, 10, 27));
        payout.setTierPayoutDays((short) 15);
        payoutRepository.save(payout);
        flushAndClear();

        Estimate e = estimateRepository.findByEstimateNumber("EST-T-0001").orElseThrow();
        assertThat(e.getItems()).extracting(EstimateItem::getLineNo).containsExactly(1, 2);
        assertThat(e.getItems().get(0).getB2cName()).isEqualTo("2 Module With Edge");
        assertThat(e.getTotals().getGrandTotal()).isEqualByComparingTo("13567.64");
        assertThat(e.getLead().getId()).isEqualTo(lead.getId());

        B2CDocument t = b2cRepository.findByEstimateId(e.getId()).orElseThrow();
        assertThat(t.getPartnerTotals().getTaxableAmount()).isEqualByComparingTo("11498.00");
        assertThat(t.getB2cTotals().getTaxableAmount()).isEqualByComparingTo("0");
        assertThat(t.isB2cIncomplete()).isTrue();
        assertThat(t.getItems()).singleElement()
                .satisfies(i -> assertThat(i.getPartnerName()).isEqualTo("Smart Touch Interface - 2M (STI-2M)"));

        Invoice i = invoiceRepository.findByInvoiceNumber("INV-T-0001").orElseThrow();
        assertThat(i.getEstimate().getId()).isEqualTo(e.getId());
        assertThat(i.getItems()).hasSize(1);

        Payment p = paymentRepository.findByInvoiceIdOrderByPaymentDateAsc(i.getId()).get(0);
        assertThat(p.isRefund()).isFalse();
        assertThat(p.getCreatedAt()).isNotNull();

        PartnerPayout po = payoutRepository.findByPaymentId(p.getId()).orElseThrow();
        assertThat(po.getStatus()).isEqualTo(PayoutStatus.ACCRUED);
        assertThat(po.getGrossCommission()).isEqualByComparingTo("459.92");
    }

    @Test
    void deletingEstimateCascadesToItemsAndTwinAndDetachesInvoice() {
        Partner partner = newPartner();
        Product product = productRepository.findBySkuIgnoreCase("SRC-4").orElseThrow();
        Estimate estimate = new Estimate();
        estimate.setEstimateNumber("EST-T-0002");
        estimate.setPartner(partner);
        estimate.setEstimateDate(LocalDate.of(2026, 10, 1));
        estimate.addItem(estimateLine(1, product));
        estimateRepository.save(estimate);
        B2CDocument twin = new B2CDocument();
        twin.setDocumentType(B2CDocumentType.ESTIMATE);
        twin.setEstimate(estimate);
        twin.setInternalNumber("INT-EST-T-0002");
        twin.setParentNumber("EST-T-0002");
        twin.setPartner(partner);
        twin.setDocumentDate(estimate.getEstimateDate());
        b2cRepository.save(twin);
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-T-0002");
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.parse("2026-10-02T05:00:00Z"));
        invoice.setTaxType(TaxType.INTER);
        invoice.setPartner(partner);
        invoice.setEstimate(estimate);
        invoice.setInvoiceDate(LocalDate.of(2026, 10, 2));
        invoiceRepository.save(invoice);
        flushAndClear();

        jdbc.update("delete from estimates where id = ?", estimate.getId());

        assertThat(count("select count(*) from estimate_items where estimate_id = ?", estimate.getId())).isZero();
        assertThat(count("select count(*) from b2c_documents where estimate_id = ?", estimate.getId())).isZero();
        assertThat(count("select count(*) from invoices where id = ? and estimate_id is null", invoice.getId())).isEqualTo(1);
    }

    @Test
    void deletingLeadKeepsDocuments() {
        Partner partner = newPartner();
        Lead lead = newLead(partner, "9000000003");
        Estimate estimate = new Estimate();
        estimate.setEstimateNumber("EST-T-0003");
        estimate.setPartner(partner);
        estimate.setLead(lead);
        estimate.setEstimateDate(LocalDate.of(2026, 10, 1));
        estimateRepository.save(estimate);
        flushAndClear();

        jdbc.update("delete from leads where id = ?", lead.getId());

        assertThat(count("select count(*) from estimates where id = ? and lead_id is null", estimate.getId())).isEqualTo(1);
    }

    @Test
    void userRoleRoundTrip() {
        User user = new User();
        user.setFullName("Test Admin");
        user.setEmail("Admin.Test@Example.com");
        user.setPasswordHash("$2a$12$abcdefghijklmnopqrstuuJ3k8h1gDq0yQ6l2c7q5o3X9Wv0mZ1yS");
        user.setRole(roleRepository.findByCode("ADMIN").orElseThrow());
        userRepository.save(user);
        flushAndClear();

        User reloaded = userRepository.findByEmailIgnoreCase("admin.test@example.com").orElseThrow();
        assertThat(reloaded.getRole().getCode()).isEqualTo("ADMIN");
        assertThat(reloaded.getSessionVersion()).isZero();
    }

    @Test
    void auditLogStoresJson() {
        AuditLog entry = new AuditLog();
        entry.setEntityType("PARTNER");
        entry.setEntityId(UUID.randomUUID());
        entry.setAction("UPDATE");
        entry.setOldValues("{\"name\":\"Old\"}");
        entry.setNewValues("{\"name\":\"New\",\"pan\":\"***\"}");
        entry.setRequestId("req-1");
        auditLogRepository.save(entry);
        flushAndClear();

        assertThat(jdbc.queryForObject("select new_values ->> 'name' from audit_logs where id = ?", String.class, entry.getId()))
                .isEqualTo("New");
        AuditLog reloaded = auditLogRepository.findById(entry.getId()).orElseThrow();
        assertThat(reloaded.getOldValues()).contains("\"Old\"");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void documentSequenceCanBeLockedAndIncremented() {
        sequenceRepository.save(new DocumentSequence("TST", "2627"));
        flushAndClear();

        DocumentSequence seq = sequenceRepository.findForUpdate(new DocumentSequence.Key("TST", "2627")).orElseThrow();
        seq.setLastValue(seq.getLastValue() + 1);
        flushAndClear();

        assertThat(sequenceRepository.findById(new DocumentSequence.Key("TST", "2627")).orElseThrow().getLastValue())
                .isEqualTo(1);
    }

    private Partner newPartner() {
        Partner partner = new Partner();
        partner.setName("Test Partner " + UUID.randomUUID());
        partner.setCategory(PartnerCategory.ARCHITECT);
        partner.setTier(tierRepository.findByTierLevel((short) 1).orElseThrow());
        partner.setPan("ABCDE1234F");
        return partnerRepository.save(partner);
    }

    private Lead newLead(Partner partner, String phone) {
        Lead lead = new Lead();
        lead.setPartner(partner);
        lead.setClientName("Client");
        lead.setClientPhone(phone);
        lead.setProjectType(ProjectType.VILLA);
        return leadRepository.save(lead);
    }

    private static EstimateItem estimateLine(int lineNo, Product product) {
        EstimateItem item = new EstimateItem();
        item.setLineNo(lineNo);
        item.setProduct(product);
        item.setDescription(product.getPartnerName());
        item.setB2cName(product.getB2cName());
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(product.getPartnerPrice());
        item.setGstPercent(product.getGstPercent());
        item.setLineTotal(product.getPartnerPrice());
        return item;
    }

    private static void copyLine(EstimateItem from, InvoiceItem to) {
        to.setLineNo(from.getLineNo());
        to.setProduct(from.getProduct());
        to.setDescription(from.getDescription());
        to.setB2cName(from.getB2cName());
        to.setQuantity(from.getQuantity());
        to.setUnitPrice(from.getUnitPrice());
        to.setGstPercent(from.getGstPercent());
        to.setLineTotal(from.getLineTotal());
    }

    private static DocumentTotals totals(String subtotal, String discount, String taxable, String gst, String grand) {
        return new DocumentTotals(new BigDecimal(subtotal), new BigDecimal(discount), new BigDecimal(taxable),
                new BigDecimal(gst), new BigDecimal(grand));
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
