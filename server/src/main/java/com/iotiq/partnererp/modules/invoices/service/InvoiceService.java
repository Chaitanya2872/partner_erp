package com.iotiq.partnererp.modules.invoices.service;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.common.enums.PaymentStatus;
import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.persistence.DocumentTotals;
import com.iotiq.partnererp.common.persistence.LineKind;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.common.util.DocumentCalculator;
import com.iotiq.partnererp.modules.audit.entity.AuditLog;
import com.iotiq.partnererp.modules.audit.repository.AuditLogRepository;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.DataScope;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.auth.service.DataScopeService;
import com.iotiq.partnererp.modules.b2c.entity.B2CDocument;
import com.iotiq.partnererp.modules.b2c.service.B2CDocumentService;
import com.iotiq.partnererp.modules.estimates.entity.Estimate;
import com.iotiq.partnererp.modules.estimates.entity.EstimateItem;
import com.iotiq.partnererp.modules.estimates.entity.EstimateStatus;
import com.iotiq.partnererp.modules.estimates.repository.EstimateRepository;
import com.iotiq.partnererp.modules.estimates.service.EstimateRules;
import com.iotiq.partnererp.modules.estimates.service.EstimateService;
import com.iotiq.partnererp.modules.invoices.dto.EstimateComparison;
import com.iotiq.partnererp.modules.invoices.dto.InternalInvoiceDocument;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceActionRequest;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceHistoryEntry;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceLeadOption;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceMeta;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceParty;
import com.iotiq.partnererp.modules.invoices.dto.InvoicePreview;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceRequest;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceResponse;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceSummary;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceTotals;
import com.iotiq.partnererp.modules.invoices.dto.TaxTreatmentResponse;
import com.iotiq.partnererp.modules.invoices.entity.Invoice;
import com.iotiq.partnererp.modules.invoices.entity.InvoiceItem;
import com.iotiq.partnererp.modules.invoices.mapper.InvoiceMapper;
import com.iotiq.partnererp.modules.invoices.repository.InvoiceRepository;
import com.iotiq.partnererp.modules.invoices.tax.GstCalculator;
import com.iotiq.partnererp.modules.invoices.tax.GstState;
import com.iotiq.partnererp.modules.invoices.tax.Gstin;
import com.iotiq.partnererp.modules.invoices.tax.PlaceOfSupplyBasis;
import com.iotiq.partnererp.modules.invoices.tax.TaxTreatment;
import com.iotiq.partnererp.modules.invoices.tax.TaxType;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;
import com.iotiq.partnererp.modules.leads.entity.Lead;
import com.iotiq.partnererp.modules.leads.repository.LeadRepository;
import com.iotiq.partnererp.modules.leads.service.LeadService;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import com.iotiq.partnererp.modules.payments.entity.Payment;
import com.iotiq.partnererp.modules.products.entity.Product;
import com.iotiq.partnererp.modules.products.repository.ProductRepository;
import com.iotiq.partnererp.modules.products.service.ProductCsv;
import com.iotiq.partnererp.modules.products.service.ProductLineSnapshot;
import com.iotiq.partnererp.modules.products.service.ProductService;
import com.iotiq.partnererp.modules.sequences.service.DocumentNumberService;
import com.iotiq.partnererp.modules.settings.entity.CompanySettings;
import com.iotiq.partnererp.modules.settings.repository.CompanySettingsRepository;
import com.iotiq.partnererp.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Invoices and their internal B2C twins (Phase 9 decisions, DECISIONS.md §J).
 *
 * <ul>
 *   <li>Lifecycle DRAFT → ISSUED → CANCELLED. Only drafts change; numbers ({@code INV-YYYY-NNNNNN}) are allocated on
 *       issue from the locked sequence row and never reused.</li>
 *   <li>Tax: the place of supply and its basis are explicit inputs; intra- or inter-state follows from the supplier's
 *       registered state ({@link TaxTreatment}). Doubtful treatments need an {@code INVOICES:APPROVE} confirmation.
 *       Line taxes are computed per line ({@link GstCalculator}) and frozen on issue with the supplier and customer
 *       details.</li>
 *   <li>Conversion copies the estimate's commercial values unchanged, locks the estimate (INVOICED) and allows one
 *       live invoice per estimate. Discarding the draft gives the estimate its previous status back.</li>
 *   <li>Cancellation needs {@code INVOICES:DELETE} and a reason, and is refused while unreversed payments or any
 *       payouts exist (credit notes aren't supported).</li>
 *   <li>Only regular domestic taxable supplies are issued: issuing needs an explicit confirmation of that, a declared
 *       e-invoicing status that is "not applicable", and at least one taxable line (P10-G1).</li>
 *   <li>Partner-safe and internal data stay apart: partner DTOs, documents, exports and audit entries never contain
 *       B2C names, B2C rates or the uplift.</li>
 * </ul>
 */
@Service
public class InvoiceService {

    static final int EXPORT_LIMIT = 5000;
    static final int LEAD_OPTIONS_LIMIT = 50;
    static final String APPROVE = "APPROVE";

    private final InvoiceRepository repository;
    private final EstimateRepository estimateRepository;
    private final LeadRepository leadRepository;
    private final LeadService leadService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final B2CDocumentService twinService;
    private final DocumentNumberService numbers;
    private final CompanySettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final Clock clock;

    public InvoiceService(InvoiceRepository repository, EstimateRepository estimateRepository,
                          LeadRepository leadRepository, LeadService leadService, ProductService productService,
                          ProductRepository productRepository, B2CDocumentService twinService,
                          DocumentNumberService numbers, CompanySettingsRepository settingsRepository,
                          UserRepository userRepository, AuditLogRepository auditLogRepository,
                          DataScopeService dataScopeService, AuditService auditService, ObjectMapper objectMapper,
                          EntityManager entityManager, Clock clock) {
        this.repository = repository;
        this.estimateRepository = estimateRepository;
        this.leadRepository = leadRepository;
        this.leadService = leadService;
        this.productService = productService;
        this.productRepository = productRepository;
        this.twinService = twinService;
        this.numbers = numbers;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Payment-status filter values (Phase 9 decision 3). */
    public enum PaymentFilter { UNPAID, PARTIALLY_PAID, PAID, OVERDUE }

    /** List filters; any may be null. Dates filter on the issue date. */
    public record Filters(String query, InvoiceStatus status, PaymentFilter payment, UUID partnerId, UUID leadId,
                          UUID estimateId, LocalDate from, LocalDate to) {
    }

    /** A rendered document and its download file name. */
    public record Document(String filename, String html) {
    }

    // ------------------------------------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<InvoiceSummary> search(Filters filters, Pageable pageable, AuthenticatedUser user) {
        Page<Invoice> page = repository.findAll(spec(filters, user), pageable);
        Map<UUID, BigDecimal> paid = paid(page.getContent());
        LocalDate today = LocalDate.now(clock);
        return page.map(i -> summary(i, paid, today));
    }

    @Transactional(readOnly = true)
    public List<InvoiceSummary> searchAll(Filters filters, Sort sort, AuthenticatedUser user) {
        List<Invoice> rows = repository.findAll(spec(filters, user), PageRequest.of(0, EXPORT_LIMIT, sort)).getContent();
        Map<UUID, BigDecimal> paid = paid(rows);
        LocalDate today = LocalDate.now(clock);
        return rows.stream().map(i -> summary(i, paid, today)).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID id, AuthenticatedUser user) {
        return toResponse(load(id, user));
    }

    /** The internal B2C twin; it exists once the invoice is issued. Callers need {@code INTERNAL_B2C} read. */
    @Transactional(readOnly = true)
    public InternalInvoiceDocument internalDocument(UUID id, AuthenticatedUser user) {
        Invoice invoice = load(id, user);
        B2CDocument twin = twinService.findForInvoice(id).orElseThrow(() -> invoice.getStatus() == InvoiceStatus.DRAFT
                ? BusinessException.conflict("INTERNAL_COPY_NOT_READY",
                "The internal B2C copy is created when the invoice is issued.")
                : new ResourceNotFoundException("Internal B2C copy", id));
        return InvoiceMapper.toInternal(invoice, twin);
    }

    /** Printable partner copy (HTML), built from the partner-safe response only. */
    @Transactional(readOnly = true)
    public Document partnerDocument(UUID id, AuthenticatedUser user) {
        InvoiceResponse invoice = get(id, user);
        String name = invoice.invoiceNumber() != null ? invoice.invoiceNumber() : "DRAFT-INVOICE";
        return new Document(name + ".html", InvoiceDocumentRenderer.partner(invoice, settings()));
    }

    /** Printable internal B2C copy (HTML), file name INTERNAL-&lt;number&gt;.html as in the source. */
    @Transactional(readOnly = true)
    public Document internalDocumentHtml(UUID id, AuthenticatedUser user) {
        InvoiceResponse invoice = get(id, user);
        InternalInvoiceDocument twin = internalDocument(id, user);
        return new Document("INTERNAL-" + twin.internalNumber() + ".html",
                InvoiceDocumentRenderer.internal(invoice, twin, settings()));
    }

    /** Partner-safe CSV of the filtered list (at most 5,000 rows). */
    @Transactional(readOnly = true)
    public String exportCsv(Filters filters, Sort sort, AuthenticatedUser user) {
        List<List<String>> rows = searchAll(filters, sort, user).stream().map(i -> List.of(
                Objects.toString(i.invoiceNumber(), ""), i.statusLabel(), Objects.toString(i.invoiceDate(), ""),
                Objects.toString(i.dueDate(), ""), Objects.toString(i.paymentStatusLabel(), ""),
                Objects.toString(i.leadNumber(), ""), Objects.toString(i.clientName(), ""),
                Objects.toString(i.billingName(), ""), i.partner().name(), Objects.toString(i.estimateNumber(), ""),
                i.taxType() == null ? "" : i.taxType().name(), i.taxableAmount().toPlainString(),
                i.grandTotal().toPlainString(), i.paidAmount() == null ? "" : i.paidAmount().toPlainString(),
                i.balance() == null ? "" : i.balance().toPlainString())).toList();
        return ProductCsv.write(List.of("invoice", "status", "invoice_date", "due_date", "payment_status", "lead",
                "client", "billed_to", "partner", "estimate", "tax_type", "taxable_amount", "grand_total", "paid",
                "balance"), rows);
    }

    @Transactional(readOnly = true)
    public List<InvoiceHistoryEntry> history(UUID id, AuthenticatedUser user) {
        load(id, user);
        List<AuditLog> entries = auditLogRepository.findTop200ByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                AuditActions.ENTITY_INVOICE, id);
        Map<UUID, String> names = names(entries.stream().map(AuditLog::getUserId).collect(Collectors.toSet()));
        return entries.stream().map(entry -> new InvoiceHistoryEntry(entry.getId(), entry.getCreatedAt(),
                entry.getUserId() == null ? null : names.get(entry.getUserId()), entry.getAction(),
                changes(entry))).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceMeta meta(AuthenticatedUser user) {
        CompanySettings settings = settings();
        return new InvoiceMeta(
                Arrays.stream(InvoiceStatus.values()).map(s -> new InvoiceMeta.Option(s.name(), s.getLabel())).toList(),
                List.of(new InvoiceMeta.Option(PaymentFilter.UNPAID.name(), "Unpaid"),
                        new InvoiceMeta.Option(PaymentFilter.PARTIALLY_PAID.name(), "Partially paid"),
                        new InvoiceMeta.Option(PaymentFilter.PAID.name(), "Paid"),
                        new InvoiceMeta.Option(PaymentFilter.OVERDUE.name(), "Overdue")),
                Arrays.stream(BillTo.values()).map(b -> new InvoiceMeta.Option(b.name(), b.getLabel())).toList(),
                Arrays.stream(GstState.values()).map(s -> new InvoiceMeta.State(s.getCode(), s.getDisplayName(),
                        s.isUtgst())).toList(),
                Arrays.stream(PlaceOfSupplyBasis.values()).map(b -> new InvoiceMeta.Option(b.name(), b.getLabel()))
                        .toList(),
                settings.getInvoiceDueDays(), settings.getTerms(), settings.getDefaultGstPercent(),
                InvoiceRules.MAX_LINES, EstimateService.canOverrideFee(user), canApprove(user), canCancel(user),
                supplierComplete(settings), settings.getStateCode());
    }

    /**
     * Restricted lead picker for direct invoices (Phase 9 decision 5): live leads that aren't Lost, for Invoices
     * writers without Leads read. Only the lead number, client name and partner are returned and searched.
     */
    @Transactional(readOnly = true)
    public List<InvoiceLeadOption> leadOptions(String query, UUID leadId, AuthenticatedUser user) {
        if (!leadsVisible(user)) {
            return List.of();
        }
        Specification<Lead> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.notEqual(root.get("stage"), LeadStage.LOST));
            boolean counting = cq != null && Long.class == cq.getResultType();
            @SuppressWarnings("unchecked")
            From<Lead, Partner> partner = counting ? root.join("partner", JoinType.INNER)
                    : (From<Lead, Partner>) (Object) root.<Lead, Partner>fetch("partner", JoinType.INNER);
            if (leadId != null) {
                predicates.add(cb.equal(root.get("id"), leadId));
            }
            if (query != null && !query.isBlank()) {
                String like = like(query);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("clientName")), like, '\\'),
                        cb.like(cb.lower(root.get("leadNumber")), like, '\\'),
                        cb.like(cb.lower(partner.get("name")), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return leadRepository.findAll(spec, PageRequest.of(0, LEAD_OPTIONS_LIMIT,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))))
                .map(l -> new InvoiceLeadOption(l.getId(), l.getLeadNumber(), l.getClientName(),
                        new PartnerRef(l.getPartner().getId(), l.getPartner().getName()))).getContent();
    }

    /**
     * Server tax and totals for the editor; nothing is saved. With {@code invoiceId}, the draft's saved lines (and,
     * for a converted draft, its commercial values) are used exactly as a save would.
     */
    @Transactional(readOnly = true)
    public InvoicePreview preview(InvoiceRequest request, UUID invoiceId, AuthenticatedUser user) {
        Invoice existing = invoiceId == null ? null : load(invoiceId, user);
        if (existing != null) {
            requireDraft(existing, "edited");
        }
        Draft d = build(request, existing, user, false);
        List<InvoiceResponse.Line> lines = new ArrayList<>();
        List<InvoicePreview.InternalLine> internalLines = new ArrayList<>();
        for (int n = 0; n < d.lines().size(); n++) {
            DraftLine l = d.lines().get(n);
            GstCalculator.LineTax t = d.tax().lines().get(n);
            lines.add(new InvoiceResponse.Line(l.existingId(), n + 1, l.kind(), l.product() == null ? null : l.product().getId(),
                    l.sku(), true, l.description(), l.hsn(), l.unit(), l.quantity(), l.unitPrice(), l.gstPercent(),
                    t.amount(), t.taxableValue(), t.cgst(), t.sgst(), t.igst(), t.tax(), t.total()));
            internalLines.add(new InvoicePreview.InternalLine(n + 1, l.product() == null ? null : l.product().getId(),
                    l.b2cName(), l.b2cRate(), DocumentCalculator.lineAmount(l.quantity(), l.b2cRate()),
                    l.kind() == LineKind.PRODUCT && l.b2cRate().signum() == 0));
        }
        InvoicePreview.Internal internal = null;
        if (ProductService.canViewInternal(user)) {
            DocumentCalculator.Totals b2c = b2cTotals(d);
            int missing = (int) internalLines.stream().filter(InvoicePreview.InternalLine::b2cPriceMissing).count();
            internal = new InvoicePreview.Internal(internalLines, b2c.taxableAmount(), b2c.grandTotal(),
                    b2c.taxableAmount().subtract(d.tax().totals().taxableAmount()), missing);
        }
        Estimate source = existing == null ? null : existing.getEstimate();
        return new InvoicePreview(lines, totals(d.tax().totals()), treatment(d.treatment(), existing),
                source == null ? null : comparison(source, d.tax().totals(), d.treatment().taxType()),
                d.defaultFee(), d.fee(), d.feeOverride(), internal);
    }

    // ------------------------------------------------------------------------------------------- commands

    /** A direct draft invoice, without an estimate (Phase 9 decision 5). */
    @Transactional
    public InvoiceResponse create(InvoiceRequest request, AuthenticatedUser user) {
        Draft d = build(request, null, user, true);
        authorizeFee(d, user);
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.DRAFT);
        apply(invoice, d);
        repository.saveAndFlush(invoice);
        Map<String, Object> after = AuditService.values("status", invoice.getStatus());
        after.putAll(InvoiceMapper.auditSnapshot(invoice));
        if (d.feeOverride()) {
            after.put("defaultFeePercent", d.defaultFee());
            after.put("feeOverrideReason", d.feeReason());
        }
        auditService.record(AuditActions.INVOICE_CREATED, AuditActions.ENTITY_INVOICE, invoice.getId(), null, after);
        return toResponse(invoice);
    }

    /**
     * Phase 9 decision 2: a draft invoice from an estimate. Commercial values, lines (with HSN/SAC, unit, prices and
     * GST rates), lead, partner, fee and notes are copied unchanged; the estimate is locked (INVOICED). Deleted,
     * draft and rejected estimates are refused, and an estimate can have only one live invoice.
     */
    @Transactional
    public InvoiceResponse convert(UUID estimateId, AuthenticatedUser user) {
        if (estimateId == null) {
            throw ValidationException.of("estimateId", "Choose the estimate to invoice.");
        }
        if (!dataScopeService.resolve(user, ModuleCode.INVOICES).unrestricted()
                || !dataScopeService.resolve(user, ModuleCode.ESTIMATES).unrestricted()) {
            throw new ResourceNotFoundException("Estimate", estimateId);
        }
        Estimate any = estimateRepository.findById(estimateId)
                .orElseThrow(() -> new ResourceNotFoundException("Estimate", estimateId));
        if (any.getDeletedAt() != null) {
            throw BusinessException.conflict("ESTIMATE_DELETED", "Deleted estimates can't be invoiced.");
        }
        Estimate estimate = estimateRepository.lockLive(estimateId)
                .orElseThrow(() -> BusinessException.conflict("ESTIMATE_DELETED", "Deleted estimates can't be invoiced."));
        repository.findLiveForEstimate(estimateId).ifPresent(live -> {
            throw BusinessException.conflict("ESTIMATE_ALREADY_INVOICED", "Estimate " + estimate.getEstimateNumber()
                    + " already has " + (live.getStatus() == InvoiceStatus.DRAFT ? "a draft invoice"
                    : "invoice " + live.getInvoiceNumber()) + ".");
        });
        switch (estimate.getStatus()) {
            case DRAFT -> throw BusinessException.conflict("ESTIMATE_NOT_CONVERTIBLE",
                    "Mark the estimate sent before invoicing it.");
            case REJECTED -> throw BusinessException.conflict("ESTIMATE_NOT_CONVERTIBLE",
                    "Rejected estimates can't be invoiced.");
            default -> {
                // SENT and ACCEPTED (BR-E4); INVOICED only when its earlier invoices were all cancelled.
            }
        }
        Lead lead = estimate.getLead();
        if (lead == null || lead.getDeletedAt() != null) {
            throw BusinessException.conflict("LEAD_REQUIRED",
                    "The estimate's lead was deleted, so the estimate can't be invoiced.");
        }
        CompanySettings settings = settings();
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setEstimate(estimate);
        invoice.setSourceEstimateStatus(estimate.getStatus().name());
        invoice.setPartner(estimate.getPartner());
        invoice.setLead(lead);
        invoice.setBillTo(estimate.getBillTo());
        invoice.setDiscountPercent(estimate.getDiscountPercent());
        invoice.setFeePercent(estimate.getFeePercent());
        invoice.setNotes(estimate.getNotes());
        invoice.setDueDays(settings.getInvoiceDueDays());
        prefillBilling(invoice, estimate.getBillTo(), lead, estimate.getPartner());
        List<DraftLine> lines = estimate.getItems().stream().map(InvoiceService::copy).toList();
        TaxTreatment.Result treatment = evaluate(invoice, settings);
        GstCalculator.Result tax = calculate(lines, invoice.getDiscountPercent(), treatment.taxType());
        applyTax(invoice, treatment, tax, lines);
        repository.saveAndFlush(invoice);

        EstimateStatus previous = estimate.getStatus();
        if (previous != EstimateStatus.INVOICED) {
            estimate.setStatus(EstimateStatus.INVOICED);
            estimateRepository.saveAndFlush(estimate);
            auditService.record(AuditActions.ESTIMATE_STATUS_CHANGED, AuditActions.ENTITY_ESTIMATE, estimate.getId(),
                    AuditService.values("status", previous),
                    AuditService.values("status", EstimateStatus.INVOICED, "invoiceId", invoice.getId()));
        }
        Map<String, Object> after = AuditService.values("status", invoice.getStatus(), "estimateId", estimate.getId(),
                "estimateNumber", estimate.getEstimateNumber());
        after.putAll(InvoiceMapper.auditSnapshot(invoice));
        auditService.record(AuditActions.INVOICE_CONVERTED, AuditActions.ENTITY_INVOICE, invoice.getId(), null, after);
        return toResponse(invoice);
    }

    /** Replace a draft. Issued and cancelled invoices can't change (409). */
    @Transactional
    public InvoiceResponse update(UUID id, InvoiceRequest request, AuthenticatedUser user) {
        Invoice invoice = lock(id, user);
        if (request == null || request.version() == null) {
            throw ValidationException.of("version", "Version is required.");
        }
        checkVersion(invoice, request.version());
        requireDraft(invoice, "edited");
        Draft d = build(request, invoice, user, true);
        authorizeFee(d, user);

        Map<String, Object> before = InvoiceMapper.auditSnapshot(invoice);
        Map<String, Object> compareBefore = InvoiceMapper.auditSnapshot(invoice, false);
        String keyBefore = currentKey(invoice);
        invoice.getItems().clear();
        repository.flush();
        apply(invoice, d);
        boolean confirmationCleared = invoice.getTaxConfirmedAt() != null && !Objects.equals(keyBefore, d.treatment().key());
        if (confirmationCleared) {
            // The inputs the confirmation covered changed: it no longer applies.
            invoice.setTaxConfirmedAt(null);
            invoice.setTaxConfirmedBy(null);
            invoice.setTaxConfirmationNote(null);
            invoice.setTaxConfirmationKey(null);
        }
        Map<String, Object> after = InvoiceMapper.auditSnapshot(invoice);
        Map<String, Object> compareAfter = InvoiceMapper.auditSnapshot(invoice, false);
        // Compared unmasked (two GSTINs can share their last characters); recorded masked.
        Set<String> changed = new LinkedHashSet<>();
        compareBefore.keySet().forEach(k -> {
            if (!Objects.equals(compareBefore.get(k), compareAfter.get(k))) {
                changed.add(k);
            }
        });
        if (changed.isEmpty() && !confirmationCleared) {
            return toResponse(invoice);
        }
        if (changed.equals(Set.of("lines")) && !confirmationCleared) {
            // Only line rows changed: bump the version anyway so concurrent editors still conflict.
            entityManager.lock(invoice, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        repository.saveAndFlush(invoice);
        Map<String, Object> newValues = only(after, changed);
        if (changed.contains("feePercent") && d.feeOverride()) {
            newValues.put("defaultFeePercent", d.defaultFee());
            newValues.put("feeOverrideReason", d.feeReason());
        }
        if (confirmationCleared) {
            newValues.put("taxConfirmation", "cleared");
        }
        auditService.record(AuditActions.INVOICE_UPDATED, AuditActions.ENTITY_INVOICE, id, only(before, changed),
                newValues);
        return toResponse(invoice);
    }

    /**
     * Confirms a doubtful tax treatment ({@code INVOICES:APPROVE}, checked by the controller), with a note. The
     * confirmation covers the current supplier state, billing state and GSTIN, place of supply, basis and tax type;
     * a change to any of them needs a new one.
     */
    @Transactional
    public InvoiceResponse confirmTax(UUID id, InvoiceActionRequest request, AuthenticatedUser user) {
        Invoice invoice = lock(id, user);
        checkVersion(invoice, request == null ? null : request.version());
        requireDraft(invoice, "confirmed");
        String note = request == null ? null : EstimateRules.text(request.note(), "note", InvoiceRules.MAX_REASON,
                new ArrayList<>());
        if (note == null) {
            throw ValidationException.of("note", "Explain why this tax treatment is right.");
        }
        if (note.length() > InvoiceRules.MAX_REASON) {
            throw ValidationException.of("note", "Must be at most " + InvoiceRules.MAX_REASON + " characters.");
        }
        TaxTreatment.Result treatment = evaluate(invoice, settings());
        if (!treatment.missing().isEmpty()) {
            throw new BusinessException("TAX_DETAILS_MISSING",
                    "Fill in the place of supply, its basis and the supplier's GST registration first.");
        }
        if (!treatment.confirmationRequired()) {
            throw BusinessException.conflict("TAX_CONFIRMATION_NOT_NEEDED",
                    "This invoice's tax treatment doesn't need a confirmation.");
        }
        invoice.setTaxConfirmedAt(clock.instant());
        invoice.setTaxConfirmedBy(user.id());
        invoice.setTaxConfirmationNote(note);
        invoice.setTaxConfirmationKey(treatment.key());
        repository.saveAndFlush(invoice);
        auditService.record(AuditActions.INVOICE_TAX_CONFIRMED, AuditActions.ENTITY_INVOICE, id, null,
                AuditService.values("taxType", treatment.taxType(), "placeOfSupplyCode", invoice.getPlaceOfSupplyCode(),
                        "placeOfSupplyBasis", invoice.getPlaceOfSupplyBasis(), "billingStateCode",
                        invoice.getBillingStateCode(), "reasons", treatment.reasons().stream()
                                .map(TaxTreatment.Reason::code).toList(), "note", note));
        return toResponse(invoice);
    }

    /**
     * Issue a draft: tax is recomputed from the current supplier registration, the number is allocated, the issue
     * date is today, and the supplier, customer, place of supply, tax treatment and line taxes are frozen. The lead
     * moves to Won (BR-V3) and the internal B2C twin is written, all in one transaction.
     */
    @Transactional
    public InvoiceResponse issue(UUID id, InvoiceActionRequest request, AuthenticatedUser user) {
        Invoice invoice = lock(id, user);
        checkVersion(invoice, request == null ? null : request.version());
        requireDraft(invoice, "issued");
        CompanySettings settings = settings();
        if (!supplierComplete(settings)) {
            throw new BusinessException("SUPPLIER_REGISTRATION_INCOMPLETE", "Add the company's GST registration "
                    + "(state, GSTIN, address and the e-invoicing declaration) in Settings before issuing invoices.");
        }
        if (Boolean.TRUE.equals(settings.getEinvoicingApplicable())) {
            throw new BusinessException("EINVOICING_NOT_SUPPORTED", "E-invoicing (IRN and QR code) applies to the "
                    + "company, and this system can't generate e-invoices. Issue the invoice through the e-invoice "
                    + "system instead.");
        }
        // Lock the lead first, so concurrent issues for one lead queue here and the Won move sees its current state.
        Lead lead = invoice.getLead() == null ? null : leadRepository.lockLive(invoice.getLead().getId()).orElse(null);
        if (lead == null) {
            throw BusinessException.conflict("LEAD_REQUIRED", "This invoice's lead was deleted. Choose another lead.");
        }
        List<FieldError> errors = new ArrayList<>();
        if (invoice.getBillingName() == null) {
            errors.add(new FieldError("billingName", "Enter the customer's billing name."));
        }
        if (invoice.getBillingAddress() == null) {
            errors.add(new FieldError("billingAddress", "Enter the customer's billing address."));
        }
        if (invoice.getPlaceOfSupplyCode() == null) {
            errors.add(new FieldError("placeOfSupplyCode", "Choose the place of supply."));
        }
        if (invoice.getPlaceOfSupplyBasis() == null) {
            errors.add(new FieldError("placeOfSupplyBasis", "Choose the basis for the place of supply."));
        }
        if (invoice.getItems().isEmpty()) {
            errors.add(new FieldError("lines", "Add at least one item."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        if (invoice.getItems().stream().allMatch(item -> item.getGstPercent().signum() == 0)) {
            throw new BusinessException("BILL_OF_SUPPLY_NOT_SUPPORTED", "Every line has 0% GST. Exempt or nil-rated "
                    + "supplies need a bill of supply, which isn't supported.");
        }
        if (request == null || !Boolean.TRUE.equals(request.regularSupplyConfirmed())) {
            throw ValidationException.of("regularSupplyConfirmed", "Confirm that this is a regular domestic taxable "
                    + "supply. Exports, SEZ supplies, reverse charge, composition, cess and exempt supplies aren't "
                    + "supported.");
        }

        // Recompute from the stored inputs and the current registration, exactly as a save would.
        Draft d = build(requestOf(invoice), invoice, user, true);
        TaxTreatment.Result treatment = d.treatment();
        if (treatment.confirmationRequired() && (invoice.getTaxConfirmedAt() == null
                || !Objects.equals(invoice.getTaxConfirmationKey(), treatment.key()))) {
            throw new BusinessException("TAX_CONFIRMATION_REQUIRED", "This tax treatment needs a confirmation by "
                    + "someone with Invoices approve before the invoice can be issued: "
                    + treatment.reasons().stream().map(TaxTreatment.Reason::message).collect(Collectors.joining(" ")));
        }
        if (request != null && request.expectedGrandTotal() != null
                && request.expectedGrandTotal().compareTo(d.tax().totals().grandTotal()) != 0) {
            throw BusinessException.conflict("TOTALS_CHANGED", "The invoice total is now "
                    + d.tax().totals().grandTotal().toPlainString() + ". Review the invoice and issue it again.");
        }
        invoice.getItems().clear();
        repository.flush();
        apply(invoice, d);

        LocalDate today = LocalDate.now(clock);
        int dueDays = invoice.getDueDays() == null ? settings.getInvoiceDueDays() : invoice.getDueDays();
        invoice.setDueDays(dueDays);
        invoice.setInvoiceNumber(numbers.next(DocumentNumberService.INVOICE));
        invoice.setInvoiceDate(today);
        invoice.setDueDate(today.plusDays(dueDays));
        invoice.setSupplierName(settings.getCompanyName());
        invoice.setSupplierAddress(settings.getAddress());
        invoice.setSupplierGstin(settings.getGstin());
        invoice.setSupplierStateCode(settings.getStateCode());
        invoice.setIssuedAt(clock.instant());
        invoice.setIssuedBy(user.id());
        invoice.setStatus(InvoiceStatus.ISSUED);
        repository.saveAndFlush(invoice);
        twinService.syncInvoice(invoice);
        leadService.applyAutomaticStage(lead.getId(), stage -> LeadStage.WON,
                "Invoice " + invoice.getInvoiceNumber() + " issued", user);
        auditService.record(AuditActions.INVOICE_ISSUED, AuditActions.ENTITY_INVOICE, id,
                AuditService.values("status", InvoiceStatus.DRAFT),
                AuditService.values("status", InvoiceStatus.ISSUED, "invoiceNumber", invoice.getInvoiceNumber(),
                        "regularSupplyConfirmed", true, "invoiceDate", invoice.getInvoiceDate(), "dueDate", invoice.getDueDate(),
                        "supplierStateCode", invoice.getSupplierStateCode(),
                        "supplierGstin", InvoiceMapper.maskGstin(invoice.getSupplierGstin()),
                        "placeOfSupplyCode", invoice.getPlaceOfSupplyCode(), "taxType", invoice.getTaxType(),
                        "taxableAmount", invoice.getTotals().getTaxableAmount(),
                        "cgstAmount", invoice.getCgstAmount(), "sgstAmount", invoice.getSgstAmount(),
                        "igstAmount", invoice.getIgstAmount(), "grandTotal", invoice.getTotals().getGrandTotal()));
        return toResponse(invoice);
    }

    /**
     * Cancel an issued invoice ({@code INVOICES:DELETE}, checked by the controller) with a reason. Refused while any
     * unreversed payment or any payout is recorded against it (credit notes aren't supported). The
     * number stays used and the estimate stays invoiced (it can be converted again).
     */
    @Transactional
    public InvoiceResponse cancel(UUID id, InvoiceActionRequest request, AuthenticatedUser user) {
        Invoice invoice = lock(id, user);
        checkVersion(invoice, request == null ? null : request.version());
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw BusinessException.conflict("INVOICE_DRAFT", "Drafts aren't cancelled. Discard the draft instead.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw BusinessException.conflict("INVOICE_CANCELLED", "This invoice is already cancelled.");
        }
        String reason = request == null || request.reason() == null || request.reason().isBlank() ? null
                : request.reason().strip();
        if (reason == null) {
            throw ValidationException.of("reason", "Give a reason for cancelling the invoice (at least "
                    + InvoiceRules.MIN_REASON + " characters).");
        }
        if (reason.length() < InvoiceRules.MIN_REASON) {
            throw ValidationException.of("reason", "Give a reason for cancelling the invoice (at least "
                    + InvoiceRules.MIN_REASON + " characters).");
        }
        if (reason.length() > InvoiceRules.MAX_REASON) {
            throw ValidationException.of("reason", "Must be at most " + InvoiceRules.MAX_REASON + " characters.");
        }
        if (repository.livePaymentCount(id) > 0) {
            throw BusinessException.conflict("INVOICE_HAS_PAYMENTS", "Payments are recorded against this invoice, so "
                    + "it can't be cancelled. Reverse them first if they were recorded by mistake; refunds need "
                    + "credit notes, which aren't supported.");
        }
        if (repository.unpaidPayoutLineCount(id) > 0) {
            throw BusinessException.conflict("INVOICE_IN_PAYOUT", "Partner commission on this invoice is in a payout "
                    + "that isn't paid yet. Recalculate, cancel or fail that payout first.");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(clock.instant());
        invoice.setCancelledBy(user.id());
        invoice.setCancelReason(reason);
        repository.saveAndFlush(invoice);
        auditService.record(AuditActions.INVOICE_CANCELLED, AuditActions.ENTITY_INVOICE, id,
                AuditService.values("status", InvoiceStatus.ISSUED),
                AuditService.values("status", InvoiceStatus.CANCELLED, "invoiceNumber", invoice.getInvoiceNumber(),
                        "reason", reason));
        return toResponse(invoice);
    }

    /**
     * Discard a draft ({@code INVOICES:DELETE}). A converted draft gives its estimate the status it had before
     * conversion. Issued invoices can't be deleted (cancel them instead).
     */
    @Transactional
    public void discard(UUID id, AuthenticatedUser user) {
        Invoice invoice = lock(id, user);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw BusinessException.conflict("INVOICE_ISSUED", "Issued invoices can't be deleted. Cancel it instead.");
        }
        Map<String, Object> before = AuditService.values("status", invoice.getStatus());
        before.putAll(InvoiceMapper.auditSnapshot(invoice));
        invoice.setDeletedAt(clock.instant());
        invoice.setDeletedBy(user.id());
        repository.saveAndFlush(invoice);
        Estimate estimate = invoice.getEstimate();
        if (estimate != null && invoice.getSourceEstimateStatus() != null) {
            Estimate locked = estimateRepository.lockLive(estimate.getId()).orElse(null);
            EstimateStatus restore = EstimateStatus.valueOf(invoice.getSourceEstimateStatus());
            if (locked != null && locked.getStatus() == EstimateStatus.INVOICED && restore != EstimateStatus.INVOICED) {
                locked.setStatus(restore);
                estimateRepository.saveAndFlush(locked);
                auditService.record(AuditActions.ESTIMATE_STATUS_CHANGED, AuditActions.ENTITY_ESTIMATE, locked.getId(),
                        AuditService.values("status", EstimateStatus.INVOICED),
                        AuditService.values("status", restore, "reason", "Draft invoice discarded"));
            }
        }
        auditService.record(AuditActions.INVOICE_DRAFT_DELETED, AuditActions.ENTITY_INVOICE, id, before, null);
    }

    // ------------------------------------------------------------------------------------------- permissions

    public static boolean canApprove(AuthenticatedUser user) {
        return user.permissions().canPerform(ModuleCode.INVOICES.name(), APPROVE);
    }

    public static boolean canCancel(AuthenticatedUser user) {
        return user.permissions().canPerform(ModuleCode.INVOICES.name(), "DELETE");
    }

    private void authorizeFee(Draft d, AuthenticatedUser user) {
        if (!d.feeOverride() || !d.feeChanged()) {
            return;
        }
        if (!EstimateService.canOverrideFee(user)) {
            throw new BusinessException("FEE_OVERRIDE_NOT_PERMITTED", "The referral fee for this invoice is "
                    + d.defaultFee().stripTrailingZeros().toPlainString()
                    + "%. Changing it needs the Partners approve permission.", HttpStatus.FORBIDDEN);
        }
        if (d.feeReason() == null) {
            throw ValidationException.of("feeOverrideReason", "Give a reason for changing the referral fee.");
        }
    }

    // ------------------------------------------------------------------------------------------- building

    private record DraftLine(UUID existingId, LineKind kind, Product product, String sku, String description, String b2cName,
                             String hsn, String unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal b2cRate,
                             BigDecimal gstPercent) {
    }

    private record Draft(Lead lead, Partner partner, BillTo billTo, BigDecimal discount, BigDecimal fee,
                         BigDecimal defaultFee, boolean feeOverride, boolean feeChanged, String feeReason,
                         String notes, int dueDays, String billingName, String billingAddress, GstState billingState,
                         String billingGstin, GstState placeOfSupply, PlaceOfSupplyBasis basis,
                         List<DraftLine> lines, TaxTreatment.Result treatment, GstCalculator.Result tax) {
    }

    /**
     * @param saving false for the preview, which tolerates a missing lead and lines while the form is filled in
     */
    private Draft build(InvoiceRequest r, Invoice existing, AuthenticatedUser user, boolean saving) {
        if (r == null) {
            throw ValidationException.of("leadId", "Choose a lead so the invoice knows the client and the partner.");
        }
        List<FieldError> errors = new ArrayList<>();
        CompanySettings settings = settings();
        boolean converted = existing != null && existing.getEstimate() != null;

        Lead lead = null;
        Partner partner = null;
        BillTo billTo;
        BigDecimal discount;
        BigDecimal fee = null;
        List<DraftLine> lines;
        if (converted) {
            // Phase 9 decision 2: the estimate's commercial values stay as agreed.
            String estimateNumber = existing.getEstimate().getEstimateNumber();
            String locked = "This comes from estimate " + estimateNumber + " and can't change on its invoice."
                    + " Discard the draft to change the estimate.";
            lead = existing.getLead();
            partner = existing.getPartner();
            billTo = existing.getBillTo();
            discount = existing.getDiscountPercent();
            fee = existing.getFeePercent();
            if (r.leadId() != null && (lead == null || !lead.getId().equals(r.leadId()))) {
                errors.add(new FieldError("leadId", locked));
            }
            if (r.billTo() != null && r.billTo() != billTo) {
                errors.add(new FieldError("billTo", locked));
            }
            if (r.discountPercent() != null && r.discountPercent().compareTo(discount) != 0) {
                errors.add(new FieldError("discountPercent", locked));
            }
            if (r.feePercent() != null && r.feePercent().compareTo(fee) != 0) {
                errors.add(new FieldError("feePercent", locked));
            }
            if (r.lines() != null) {
                errors.add(new FieldError("lines", locked));
            }
            lines = existing.getItems().stream().map(InvoiceService::copy).toList();
        } else {
            boolean sameLead = existing != null && existing.getLead() != null
                    && existing.getLead().getId().equals(r.leadId());
            if (r.leadId() == null) {
                if (saving) {
                    errors.add(new FieldError("leadId", "Choose a lead so the invoice knows the client and the partner."));
                }
            } else if (sameLead) {
                lead = existing.getLead();
            } else {
                lead = leadsVisible(user) ? leadRepository.findByIdAndDeletedAtIsNull(r.leadId()).orElse(null) : null;
                if (lead == null) {
                    errors.add(new FieldError("leadId", "This lead doesn't exist or was deleted."));
                } else if (lead.getStage() == LeadStage.LOST) {
                    errors.add(new FieldError("leadId", "Lost leads can't be invoiced. Reopen the lead first."));
                }
            }
            partner = lead == null ? null : sameLead ? existing.getPartner() : lead.getPartner();
            billTo = r.billTo() == null ? BillTo.CLIENT : r.billTo();
            discount = r.discountPercent() == null ? BigDecimal.ZERO.setScale(3)
                    : EstimateRules.percent(r.discountPercent(), "discountPercent", "discount", errors);
            if (billTo == BillTo.PARTNER) {
                if (r.feePercent() != null && r.feePercent().signum() != 0) {
                    errors.add(new FieldError("feePercent", "Partner purchases carry no referral fee."));
                }
                fee = BigDecimal.ZERO.setScale(3);
            } else if (r.feePercent() != null) {
                fee = EstimateRules.percent(r.feePercent(), "feePercent", "referral fee", errors);
            }
            lines = lines(r.lines() == null ? List.of() : r.lines(), existing, user, errors);
            if (saving && lines.isEmpty() && errors.stream().noneMatch(e -> e.field().startsWith("lines"))) {
                errors.add(new FieldError("lines", "Add at least one item."));
            }
            if (lines.size() > InvoiceRules.MAX_LINES) {
                errors.add(new FieldError("lines", "An invoice can have at most " + InvoiceRules.MAX_LINES + " lines."));
            }
        }

        String notes = EstimateRules.text(r.notes(), "notes", EstimateRules.MAX_NOTES, errors);
        String reason = EstimateRules.text(r.feeOverrideReason(), "feeOverrideReason", 500, errors);
        int dueDays = settings.getInvoiceDueDays();
        if (r.dueDays() != null) {
            if (r.dueDays() < 0 || r.dueDays() > InvoiceRules.MAX_DUE_DAYS) {
                errors.add(new FieldError("dueDays", "Due days must be between 0 and " + InvoiceRules.MAX_DUE_DAYS + "."));
            } else {
                dueDays = r.dueDays();
            }
        } else if (existing != null && existing.getDueDays() != null) {
            dueDays = existing.getDueDays();
        }
        String billingName = EstimateRules.text(r.billingName(), "billingName", 200, errors);
        String billingAddress = EstimateRules.text(r.billingAddress(), "billingAddress", InvoiceRules.MAX_ADDRESS, errors);
        GstState billingState = state(r.billingStateCode(), "billingStateCode", "billing state", errors);
        String billingGstin = Gstin.normalize(r.billingGstin());
        if (billingGstin != null) {
            String problem = Gstin.problem(billingGstin);
            if (problem != null) {
                errors.add(new FieldError("billingGstin", problem));
            }
        }
        GstState placeOfSupply = state(r.placeOfSupplyCode(), "placeOfSupplyCode", "place of supply", errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        BigDecimal defaultFee = fee;
        boolean feeOverride = false;
        boolean feeChanged = false;
        if (!converted && lead != null) {
            boolean sameContext = existing != null && existing.getLead() != null
                    && existing.getLead().getId().equals(lead.getId()) && existing.getBillTo() == billTo;
            defaultFee = EstimateRules.defaultFee(billTo, lead);
            if (fee == null) {
                fee = sameContext ? existing.getFeePercent() : defaultFee;
            }
            feeOverride = fee.compareTo(defaultFee) != 0;
            feeChanged = !sameContext || fee.compareTo(existing.getFeePercent()) != 0;
        } else if (fee == null) {
            fee = BigDecimal.ZERO.setScale(3);
            defaultFee = fee;
        }

        TaxTreatment.Result treatment = TaxTreatment.evaluate(new TaxTreatment.Inputs(
                GstState.byCode(settings.getStateCode()).orElse(null), billingState, billingGstin, placeOfSupply,
                r.placeOfSupplyBasis(), converted ? existing.getEstimate().isIgst() : null));
        GstCalculator.Result tax = calculate(lines, discount, treatment.taxType());
        boolean tooLarge = tax.totals().grandTotal().compareTo(new BigDecimal("999999999999.99")) > 0
                || tax.lines().stream().anyMatch(l -> l.total().compareTo(new BigDecimal("999999999999.99")) > 0);
        if (tooLarge) {
            throw ValidationException.of("lines", "The invoice total is too large.");
        }
        return new Draft(lead, partner, billTo, discount, fee, defaultFee, feeOverride, feeChanged, reason, notes,
                dueDays, billingName, billingAddress, billingState, billingGstin, placeOfSupply,
                r.placeOfSupplyBasis(), lines, treatment, tax);
    }

    /**
     * §6.2 snapshots for direct invoices. Product lines take rate and GST from the product sheet (a saved line keeps
     * its copy); any price or GST sent for them is ignored. Only a saved installation line's GST change needs
     * {@code INVOICES:APPROVE}: tax rates are sensitive (Phase 9 decision 6).
     */
    private List<DraftLine> lines(List<InvoiceRequest.Line> input, Invoice existing, AuthenticatedUser user,
                                  List<FieldError> errors) {
        Map<UUID, InvoiceItem> saved = existing == null ? Map.of()
                : existing.getItems().stream().collect(Collectors.toMap(InvoiceItem::getId, Function.identity()));
        boolean approver = canApprove(user);
        List<DraftLine> lines = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            InvoiceRequest.Line in = input.get(i);
            if (in == null) {
                continue;
            }
            String f = "lines[" + i + "]";
            LineKind kind = LineKind.PRODUCT;
            if (in.kind() != null && !in.kind().isBlank()) {
                try {
                    kind = LineKind.valueOf(in.kind().strip().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    errors.add(new FieldError(f + ".kind", "Unknown line type."));
                    continue;
                }
            }
            if (kind == LineKind.INSTALLATION) {
                installationLine(in, f, saved, approver, lines, errors);
                continue;
            }
            if (in.productId() == null) {
                continue;
            }
            InvoiceItem kept = in.id() == null ? null : saved.get(in.id());
            if (in.id() != null && kept == null) {
                errors.add(new FieldError(f + ".id", "This line isn't part of the invoice any more. Reload and try again."));
                continue;
            }
            BigDecimal quantity = EstimateRules.quantity(in.quantity(), f + ".quantity", errors);
            if (kept != null && kept.getProduct() != null && kept.getProduct().getId().equals(in.productId())) {
                // Product lines are priced by the product sheet: a saved line keeps its copy, whatever the request says.
                BigDecimal rate = kept.getUnitPrice();
                BigDecimal gst = kept.getGstPercent();
                if (quantity != null && rate != null && gst != null) {
                    lines.add(new DraftLine(kept.getId(), LineKind.PRODUCT, kept.getProduct(), kept.getProduct().getSku(),
                            kept.getDescription(), kept.getB2cName(), kept.getHsn(), kept.getUnit(), quantity, rate,
                            kept.getB2cUnitPrice(), gst));
                }
                continue;
            }
            ProductLineSnapshot snap;
            try {
                snap = productService.lineSnapshot(in.productId());
            } catch (ResourceNotFoundException e) {
                errors.add(new FieldError(f + ".productId", "This product doesn't exist."));
                continue;
            } catch (BusinessException e) {
                errors.add(new FieldError(f + ".productId", e.getMessage()));
                continue;
            }
            // Price and GST always come from the product sheet; any value in the request is ignored.
            BigDecimal rate = snap.rate();
            BigDecimal gst = snap.gstPercent().setScale(3);
            if (quantity != null && rate != null && gst != null) {
                lines.add(new DraftLine(null, LineKind.PRODUCT, productRepository.getReferenceById(snap.productId()), snap.sku(),
                        snap.name(), snap.b2cName(), snap.hsn(), snap.unit(), quantity, rate.setScale(2),
                        snap.b2cRate().setScale(2), gst));
            }
        }
        return lines;
    }

    /**
     * Installation line of a direct invoice: free text priced like any line, no product. It has no source rate, so
     * only a change to a saved line's GST rate needs {@code INVOICES:APPROVE}.
     */
    private static void installationLine(InvoiceRequest.Line in, String f, Map<UUID, InvoiceItem> saved,
                                         boolean approver, List<DraftLine> lines, List<FieldError> errors) {
        InvoiceItem kept = in.id() == null ? null : saved.get(in.id());
        if (in.id() != null && (kept == null || !kept.isInstallation())) {
            errors.add(new FieldError(f + ".id", "This line isn't part of the invoice any more. Reload and try again."));
            return;
        }
        if (in.productId() != null) {
            errors.add(new FieldError(f + ".productId", "Installation lines have no product."));
        }
        String description = EstimateRules.text(in.description(), f + ".description", 300, errors);
        if (description == null) {
            errors.add(new FieldError(f + ".description", "Describe the installation work."));
        }
        String unit = EstimateRules.text(in.unit(), f + ".unit", 20, errors);
        BigDecimal quantity = EstimateRules.quantity(in.quantity(), f + ".quantity", errors);
        BigDecimal rate = EstimateRules.rate(in.unitPrice(), f + ".unitPrice", errors);
        BigDecimal gst = EstimateRules.percent(in.gstPercent(), f + ".gstPercent", "GST", errors);
        if (gst != null && kept != null && gst.compareTo(kept.getGstPercent()) != 0 && !approver) {
            errors.add(new FieldError(f + ".gstPercent", "Changing a GST rate needs the Invoices approve permission."));
            return;
        }
        if (description != null && description.length() <= 300 && (unit == null || unit.length() <= 20)
                && quantity != null && rate != null && gst != null && in.productId() == null) {
            lines.add(new DraftLine(kept == null ? null : kept.getId(), LineKind.INSTALLATION, null, null, description,
                    description, null, unit == null ? "job" : unit, quantity, rate, rate, gst));
        }
    }

    private static DraftLine copy(EstimateItem i) {
        return new DraftLine(null, i.getKind(), i.getProduct(), i.getProduct() == null ? null : i.getProduct().getSku(),
                i.getDescription(), i.getB2cName(), i.getHsn(), i.getUnit(), i.getQuantity(), i.getUnitPrice(),
                i.getB2cUnitPrice(), i.getGstPercent());
    }

    private static DraftLine copy(InvoiceItem i) {
        return new DraftLine(i.getId(), i.getKind(), i.getProduct(), i.getProduct() == null ? null : i.getProduct().getSku(),
                i.getDescription(), i.getB2cName(), i.getHsn(), i.getUnit(), i.getQuantity(), i.getUnitPrice(),
                i.getB2cUnitPrice(), i.getGstPercent());
    }

    /** The stored inputs of a draft as a request, so issuing recomputes exactly as a save. */
    private static InvoiceRequest requestOf(Invoice i) {
        boolean converted = i.getEstimate() != null;
        List<InvoiceRequest.Line> lines = converted ? null : i.getItems().stream()
                .map(l -> new InvoiceRequest.Line(l.getId(), l.getKind().name(),
                        l.getProduct() == null ? null : l.getProduct().getId(), l.getDescription(), l.getUnit(),
                        l.getQuantity(), l.getUnitPrice(), l.getGstPercent())).toList();
        return new InvoiceRequest(converted ? null : i.getLead() == null ? null : i.getLead().getId(),
                converted ? null : i.getBillTo(), converted ? null : i.getDiscountPercent(),
                converted ? null : i.getFeePercent(), null, i.getNotes(), i.getDueDays(), i.getBillingName(),
                i.getBillingAddress(), i.getBillingStateCode(), i.getBillingGstin(), i.getPlaceOfSupplyCode(),
                i.getPlaceOfSupplyBasis(), lines, i.getVersion());
    }

    private static GstState state(String code, String field, String label, List<FieldError> errors) {
        if (code == null || code.isBlank()) {
            return null;
        }
        GstState state = GstState.byCode(code.strip()).orElse(null);
        if (state == null) {
            errors.add(new FieldError(field, "Choose a valid " + label + "."));
        }
        return state;
    }

    private static GstCalculator.Result calculate(List<DraftLine> lines, BigDecimal discount, TaxType type) {
        return GstCalculator.calculate(lines.stream()
                .map(l -> new GstCalculator.Line(l.quantity(), l.unitPrice(), l.gstPercent())).toList(), discount, type);
    }

    private static DocumentCalculator.Totals b2cTotals(Draft d) {
        return DocumentCalculator.totals(d.lines().stream()
                .map(l -> new DocumentCalculator.Line(l.quantity(), l.b2cRate(), l.gstPercent())).toList(), d.discount());
    }

    private static void apply(Invoice invoice, Draft d) {
        invoice.setPartner(d.partner());
        invoice.setLead(d.lead());
        invoice.setBillTo(d.billTo());
        invoice.setDiscountPercent(d.discount());
        invoice.setFeePercent(d.fee());
        invoice.setNotes(d.notes());
        invoice.setDueDays(d.dueDays());
        invoice.setBillingName(d.billingName());
        invoice.setBillingAddress(d.billingAddress());
        invoice.setBillingStateCode(d.billingState() == null ? null : d.billingState().getCode());
        invoice.setBillingGstin(d.billingGstin());
        invoice.setPlaceOfSupplyCode(d.placeOfSupply() == null ? null : d.placeOfSupply().getCode());
        invoice.setPlaceOfSupplyBasis(d.basis());
        applyTax(invoice, d.treatment(), d.tax(), d.lines());
    }

    private static void applyTax(Invoice invoice, TaxTreatment.Result treatment, GstCalculator.Result tax,
                                 List<DraftLine> lines) {
        invoice.setTaxType(treatment.taxType());
        invoice.setUtgst(treatment.utgst());
        invoice.setIgst(treatment.taxType() == TaxType.INTER);
        GstCalculator.Totals t = tax.totals();
        invoice.setTotals(new DocumentTotals(t.subtotal(), t.discountAmount(), t.taxableAmount(), t.gstAmount(),
                t.grandTotal()));
        invoice.setCgstAmount(t.cgst());
        invoice.setSgstAmount(t.sgst());
        invoice.setIgstAmount(t.igst());
        for (int n = 0; n < lines.size(); n++) {
            DraftLine l = lines.get(n);
            GstCalculator.LineTax lt = tax.lines().get(n);
            InvoiceItem item = new InvoiceItem();
            item.setLineNo(n + 1);
            item.setKind(l.kind());
            item.setProduct(l.product());
            item.setDescription(l.description());
            item.setB2cName(l.b2cName());
            item.setHsn(l.hsn());
            item.setUnit(l.unit());
            item.setQuantity(l.quantity());
            item.setUnitPrice(l.unitPrice());
            item.setB2cUnitPrice(l.b2cRate());
            item.setGstPercent(l.gstPercent());
            item.setLineTotal(lt.amount());
            item.setTaxableValue(lt.taxableValue());
            item.setCgstAmount(lt.cgst());
            item.setSgstAmount(lt.sgst());
            item.setIgstAmount(lt.igst());
            item.setTaxAmount(lt.tax());
            item.setTotalAmount(lt.total());
            invoice.addItem(item);
        }
    }

    /**
     * Converted drafts start with the buyer's name only. The partner's GSTIN is a protected partner field
     * ({@code PARTNERS:VIEW_PARTNER_SENSITIVE}), so it is never copied: the invoice writer enters the billing GSTIN.
     */
    private static void prefillBilling(Invoice invoice, BillTo billTo, Lead lead, Partner partner) {
        invoice.setBillingName(billTo == BillTo.PARTNER ? partner.getName() : lead.getClientName());
    }

    // ---- tax treatment, comparison, payments

    private static TaxTreatment.Result evaluate(Invoice i, CompanySettings settings) {
        return TaxTreatment.evaluate(new TaxTreatment.Inputs(GstState.byCode(settings.getStateCode()).orElse(null),
                GstState.byCode(i.getBillingStateCode()).orElse(null), i.getBillingGstin(),
                GstState.byCode(i.getPlaceOfSupplyCode()).orElse(null), i.getPlaceOfSupplyBasis(),
                i.getEstimate() == null ? null : i.getEstimate().isIgst()));
    }

    private String currentKey(Invoice i) {
        return evaluate(i, settings()).key();
    }

    private TaxTreatmentResponse treatment(TaxTreatment.Result t, Invoice invoice) {
        boolean confirmed = invoice != null && invoice.getTaxConfirmedAt() != null
                && Objects.equals(invoice.getTaxConfirmationKey(), t.key());
        String by = invoice == null || invoice.getTaxConfirmedBy() == null ? null
                : names(Set.of(invoice.getTaxConfirmedBy())).get(invoice.getTaxConfirmedBy());
        return new TaxTreatmentResponse(t.taxType(), t.taxType() == null ? null : t.taxType().getLabel(), t.utgst(),
                t.taxType() == TaxType.INTRA ? (t.utgst() ? "UTGST" : "SGST") : null, t.missing(),
                t.reasons().stream().map(r -> new TaxTreatmentResponse.Reason(r.code(), r.message())).toList(),
                t.confirmationRequired(), confirmed, invoice == null ? null : invoice.getTaxConfirmedAt(), by,
                invoice == null ? null : invoice.getTaxConfirmationNote());
    }

    /** The stored treatment of an issued or cancelled invoice: frozen, never re-evaluated. */
    private TaxTreatmentResponse frozenTreatment(Invoice i) {
        String by = i.getTaxConfirmedBy() == null ? null : names(Set.of(i.getTaxConfirmedBy())).get(i.getTaxConfirmedBy());
        TaxType type = i.getTaxType();
        return new TaxTreatmentResponse(type, type == null ? null : type.getLabel(), i.isUtgst(),
                type == TaxType.INTRA ? (i.isUtgst() ? "UTGST" : "SGST") : null, List.of(), List.of(),
                i.getTaxConfirmedAt() != null, i.getTaxConfirmedAt() != null, i.getTaxConfirmedAt(), by,
                i.getTaxConfirmationNote());
    }

    private static EstimateComparison comparison(Estimate e, GstCalculator.Totals t, TaxType type) {
        DocumentTotals et = e.getTotals();
        return new EstimateComparison(e.getId(), e.getEstimateNumber(), e.isIgst(), et.getTaxableAmount(),
                et.getGstAmount(), et.getGrandTotal(), t.taxableAmount().subtract(et.getTaxableAmount()),
                t.gstAmount().subtract(et.getGstAmount()), t.grandTotal().subtract(et.getGrandTotal()),
                type != null && e.isIgst() != (type == TaxType.INTER));
    }

    private static InvoiceTotals totals(GstCalculator.Totals t) {
        return new InvoiceTotals(t.subtotal(), t.discountAmount(), t.taxableAmount(), t.cgst(), t.sgst(), t.igst(),
                t.gstAmount(), t.grandTotal());
    }

    private Map<UUID, BigDecimal> paid(List<Invoice> invoices) {
        List<UUID> issued = invoices.stream().filter(i -> i.getStatus() != InvoiceStatus.DRAFT).map(Invoice::getId).toList();
        if (issued.isEmpty()) {
            return Map.of();
        }
        return repository.paidAmounts(issued).stream()
                .collect(Collectors.toMap(InvoiceRepository.PaidAmount::getInvoiceId, InvoiceRepository.PaidAmount::getPaid));
    }

    private static InvoiceSummary summary(Invoice i, Map<UUID, BigDecimal> paid, LocalDate today) {
        BigDecimal amount = paid.getOrDefault(i.getId(), BigDecimal.ZERO).setScale(2);
        PaymentStatus status = InvoiceRules.paymentStatus(i.getStatus(), i.getTotals().getGrandTotal(), amount,
                i.getDueDate(), today);
        return InvoiceMapper.toSummary(i, status, amount);
    }

    private static boolean supplierComplete(CompanySettings s) {
        return GstState.byCode(s.getStateCode()).isPresent() && s.getGstin() != null
                && s.getAddress() != null && !s.getAddress().isBlank() && s.getEinvoicingApplicable() != null;
    }

    // ---- loading, scope, responses

    private Invoice load(UUID id, AuthenticatedUser user) {
        requireInvoiceScope(id, user);
        return repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }

    private Invoice lock(UUID id, AuthenticatedUser user) {
        requireInvoiceScope(id, user);
        return repository.lockLive(id).orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }

    /** Records outside the caller's data scope are reported as missing (P3-07); SELF/TEAM show nothing (D-30). */
    private void requireInvoiceScope(UUID id, AuthenticatedUser user) {
        if (!dataScopeService.resolve(user, ModuleCode.INVOICES).unrestricted()) {
            throw new ResourceNotFoundException("Invoice", id);
        }
    }

    /** As for estimates: the Invoices scope must be unrestricted, and so must the Leads scope when the caller has one. */
    private boolean leadsVisible(AuthenticatedUser user) {
        return dataScopeService.resolve(user, ModuleCode.INVOICES).unrestricted()
                && user.permissions().scope(ModuleCode.LEADS.name()).map(scope -> scope == DataScope.ALL).orElse(true);
    }

    private static void checkVersion(Invoice invoice, Long version) {
        if (version == null) {
            throw ValidationException.of("version", "Version is required.");
        }
        if (version != invoice.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Invoice.class, invoice.getId());
        }
    }

    private static void requireDraft(Invoice invoice, String action) {
        if (invoice.getStatus() == InvoiceStatus.ISSUED) {
            throw BusinessException.conflict("INVOICE_ISSUED", "Issued invoices can't be " + action
                    + ". Cancel the invoice and create a new one if it is wrong.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw BusinessException.conflict("INVOICE_CANCELLED", "Cancelled invoices can't be " + action + ".");
        }
    }

    private InvoiceResponse toResponse(Invoice i) {
        CompanySettings settings = settings();
        LocalDate today = LocalDate.now(clock);
        boolean draft = i.getStatus() == InvoiceStatus.DRAFT;
        BigDecimal paid = draft ? null : repository.paidAmount(i.getId()).setScale(2);
        PaymentStatus paymentStatus = draft ? null
                : InvoiceRules.paymentStatus(i.getStatus(), i.getTotals().getGrandTotal(), paid, i.getDueDate(), today);
        Set<UUID> actorIds = new HashSet<>();
        for (UUID u : new UUID[]{i.getCreatedBy(), i.getIssuedBy(), i.getCancelledBy()}) {
            if (u != null) {
                actorIds.add(u);
            }
        }
        Map<UUID, String> names = names(actorIds);
        InvoiceParty supplier = draft
                ? InvoiceMapper.party(settings.getCompanyName(), settings.getAddress(), settings.getStateCode(),
                settings.getGstin())
                : InvoiceMapper.party(i.getSupplierName(), i.getSupplierAddress(), i.getSupplierStateCode(),
                i.getSupplierGstin());
        TaxTreatmentResponse tax = draft ? treatment(evaluate(i, settings), i) : frozenTreatment(i);
        InvoiceTotals totals = InvoiceMapper.totals(i);
        EstimateComparison comparison = null;
        if (i.getEstimate() != null) {
            comparison = comparison(i.getEstimate(), new GstCalculator.Totals(totals.subtotal(), totals.discountAmount(),
                    totals.taxableAmount(), totals.cgstAmount(), totals.sgstAmount(), totals.igstAmount(),
                    totals.gstAmount(), totals.grandTotal()), i.getTaxType());
        }
        GstState pos = GstState.byCode(i.getPlaceOfSupplyCode()).orElse(null);
        return new InvoiceResponse(i.getId(), i.getInvoiceNumber(), i.getStatus(), i.getStatus().getLabel(),
                i.getInvoiceDate(), i.getDueDate(), i.getDueDays() == null ? settings.getInvoiceDueDays() : i.getDueDays(),
                paymentStatus, paymentStatus == null ? null : paymentStatus.getLabel(), paid,
                paid == null ? null : i.getTotals().getGrandTotal().subtract(paid), InvoiceMapper.lead(i.getLead()),
                InvoiceMapper.partner(i), i.getEstimate() == null ? null
                : new InvoiceResponse.EstimateRef(i.getEstimate().getId(), i.getEstimate().getEstimateNumber()),
                i.getBillTo(), i.getBillTo().getLabel(), i.getDiscountPercent(), i.getFeePercent(), i.getNotes(),
                supplier, InvoiceMapper.billing(i), i.getPlaceOfSupplyCode(), pos == null ? null : pos.getDisplayName(),
                i.getPlaceOfSupplyBasis(), i.getPlaceOfSupplyBasis() == null ? null : i.getPlaceOfSupplyBasis().getLabel(),
                tax, i.getItems().stream().map(InvoiceMapper::toLine).toList(), totals, comparison, i.getIssuedAt(),
                names.get(i.getIssuedBy()), i.getCancelledAt(), names.get(i.getCancelledBy()), i.getCancelReason(),
                i.getCreatedAt(), names.get(i.getCreatedBy()), i.getUpdatedAt(), i.getVersion());
    }

    private Map<UUID, String> names(Set<UUID> ids) {
        // A mutable map: callers look up null actor ids, which immutable maps reject.
        Map<UUID, String> names = new HashMap<>();
        Set<UUID> present = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (!present.isEmpty()) {
            userRepository.findAllById(present).forEach(u -> names.put(u.getId(), u.getFullName()));
        }
        return names;
    }

    private CompanySettings settings() {
        return settingsRepository.findSingleton().orElseThrow(() -> new IllegalStateException("Company settings are missing"));
    }

    private List<InvoiceHistoryEntry.Change> changes(AuditLog entry) {
        JsonNode before = parse(entry.getOldValues());
        JsonNode after = parse(entry.getNewValues());
        Set<String> fields = new LinkedHashSet<>();
        before.propertyNames().forEach(fields::add);
        after.propertyNames().forEach(fields::add);
        return fields.stream().map(field -> new InvoiceHistoryEntry.Change(field,
                before.path(field).isMissingNode() ? null : before.get(field),
                after.path(field).isMissingNode() ? null : after.get(field))).toList();
    }

    private JsonNode parse(String json) {
        return json == null ? objectMapper.createObjectNode() : objectMapper.readTree(json);
    }

    private static Map<String, Object> only(Map<String, Object> snapshot, Set<String> keys) {
        Map<String, Object> out = AuditService.values();
        snapshot.forEach((k, v) -> {
            if (keys.contains(k)) {
                out.put(k, v);
            }
        });
        return out;
    }

    private static String like(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private Specification<Invoice> spec(Filters filters, AuthenticatedUser user) {
        return dataScopeService.resolve(user, ModuleCode.INVOICES).<Invoice>toSpecification()
                .and(matching(filters, LocalDate.now(clock)));
    }

    private static Specification<Invoice> matching(Filters f, LocalDate today) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            boolean counting = cq != null && Long.class == cq.getResultType();
            @SuppressWarnings("unchecked")
            From<Invoice, Partner> partner = counting ? root.join("partner", JoinType.INNER)
                    : (From<Invoice, Partner>) (Object) root.<Invoice, Partner>fetch("partner", JoinType.INNER);
            @SuppressWarnings("unchecked")
            From<Invoice, Lead> lead = counting ? root.join("lead", JoinType.LEFT)
                    : (From<Invoice, Lead>) (Object) root.<Invoice, Lead>fetch("lead", JoinType.LEFT);
            @SuppressWarnings("unchecked")
            From<Invoice, Estimate> estimate = counting ? root.join("estimate", JoinType.LEFT)
                    : (From<Invoice, Estimate>) (Object) root.<Invoice, Estimate>fetch("estimate", JoinType.LEFT);
            if (f.query() != null && !f.query().isBlank()) {
                String like = like(f.query());
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("invoiceNumber"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("billingName"), "")), like, '\\'),
                        cb.like(cb.lower(partner.get("name")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(lead.get("clientName"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(lead.get("leadNumber"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(estimate.get("estimateNumber"), "")), like, '\\')));
            }
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.partnerId() != null) {
                predicates.add(cb.equal(partner.get("id"), f.partnerId()));
            }
            if (f.leadId() != null) {
                predicates.add(cb.equal(lead.get("id"), f.leadId()));
            }
            if (f.estimateId() != null) {
                predicates.add(cb.equal(estimate.get("id"), f.estimateId()));
            }
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), f.to()));
            }
            if (f.payment() != null && cq != null) {
                predicates.add(payment(f.payment(), root, () -> paidSubquery(root, cq.subquery(BigDecimal.class), cb),
                        cb, today));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Σ payment amounts of the invoice (refunds are negative). */
    private static Subquery<BigDecimal> paidSubquery(Root<Invoice> root, Subquery<BigDecimal> sq, CriteriaBuilder cb) {
        Root<Payment> p = sq.from(Payment.class);
        return sq.select(cb.coalesce(cb.sum(p.get("amount")), BigDecimal.ZERO)).where(cb.equal(p.get("invoice"), root));
    }

    /** BR-V6 in SQL, for the payment-status filter: issued invoices only. */
    private static Predicate payment(PaymentFilter filter, Root<Invoice> root, Supplier<Subquery<BigDecimal>> paidSum,
                                     CriteriaBuilder cb, LocalDate today) {
        Expression<BigDecimal> total = root.get("totals").get("grandTotal");
        Predicate issued = cb.equal(root.get("status"), InvoiceStatus.ISSUED);
        Predicate paid = cb.greaterThanOrEqualTo(paidSum.get(), cb.diff(total, InvoiceRules.PAID_TOLERANCE));
        Predicate overdue = cb.lessThan(root.get("dueDate"), today);
        Predicate someReceived = cb.greaterThan(paidSum.get(), BigDecimal.ZERO);
        return switch (filter) {
            case PAID -> cb.and(issued, paid);
            case PARTIALLY_PAID -> cb.and(issued, cb.not(paid), someReceived);
            case OVERDUE -> cb.and(issued, cb.not(paid), overdue);
            case UNPAID -> cb.and(issued, cb.not(paid), cb.not(someReceived), cb.not(overdue));
        };
    }
}
