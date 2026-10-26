package com.iotiq.partnererp.modules.estimates.service;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.persistence.DocumentTotals;
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
import com.iotiq.partnererp.modules.estimates.dto.EstimateHistoryEntry;
import com.iotiq.partnererp.modules.estimates.dto.EstimateLeadOption;
import com.iotiq.partnererp.modules.estimates.dto.EstimateMeta;
import com.iotiq.partnererp.modules.estimates.dto.EstimatePreview;
import com.iotiq.partnererp.modules.estimates.dto.EstimateRequest;
import com.iotiq.partnererp.modules.estimates.dto.EstimateResponse;
import com.iotiq.partnererp.modules.estimates.dto.EstimateSummary;
import com.iotiq.partnererp.modules.estimates.dto.InternalEstimateDocument;
import com.iotiq.partnererp.modules.estimates.dto.TotalsResponse;
import com.iotiq.partnererp.modules.estimates.entity.Estimate;
import com.iotiq.partnererp.modules.estimates.entity.EstimateItem;
import com.iotiq.partnererp.modules.estimates.entity.EstimateStatus;
import com.iotiq.partnererp.modules.estimates.mapper.EstimateMapper;
import com.iotiq.partnererp.modules.estimates.repository.EstimateRepository;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;
import com.iotiq.partnererp.modules.leads.entity.Lead;
import com.iotiq.partnererp.modules.leads.repository.LeadRepository;
import com.iotiq.partnererp.modules.leads.service.LeadService;
import com.iotiq.partnererp.modules.partners.entity.Partner;
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
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Estimates and their internal B2C twins (BUSINESS_RULES §6, Phase 8 decisions §I).
 *
 * <ul>
 *   <li>Numbers are {@code EST-YYYY-NNNNNN}, allocated on save from a locked sequence row (decision 3).</li>
 *   <li>Lines are snapshots (§6.2, decision 5): a new line copies the product as it is now; a saved line keeps its
 *       copy, and totals are only ever recomputed from the saved copy when the estimate itself is saved.</li>
 *   <li>The estimate and its twin are written in one transaction (FR-09).</li>
 *   <li>Status actions follow BR-E4 and move the lead automatically (§4.3).</li>
 *   <li>Partner-safe and internal data stay apart (decision 6): partner DTOs, documents, exports and audit entries
 *       never contain B2C names, B2C rates or the uplift.</li>
 * </ul>
 */
@Service
public class EstimateService {

    static final int EXPORT_LIMIT = 5000;
    static final int LEAD_OPTIONS_LIMIT = 50;
    private static final String FEE_APPROVER_MODULE = ModuleCode.PARTNERS.name();

    private final EstimateRepository repository;
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

    public EstimateService(EstimateRepository repository, LeadRepository leadRepository, LeadService leadService,
                           ProductService productService, ProductRepository productRepository,
                           B2CDocumentService twinService, DocumentNumberService numbers,
                           CompanySettingsRepository settingsRepository, UserRepository userRepository,
                           AuditLogRepository auditLogRepository, DataScopeService dataScopeService,
                           AuditService auditService, ObjectMapper objectMapper, EntityManager entityManager,
                           Clock clock) {
        this.repository = repository;
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

    /** List filters; any may be null. */
    public record Filters(String query, EstimateStatus status, UUID partnerId, UUID leadId, Boolean expired) {
    }

    // ------------------------------------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<EstimateSummary> search(Filters filters, Pageable pageable, AuthenticatedUser user) {
        LocalDate today = LocalDate.now(clock);
        return repository.findAll(spec(filters, user, today), pageable).map(e -> EstimateMapper.toSummary(e, today));
    }

    @Transactional(readOnly = true)
    public List<EstimateSummary> searchAll(Filters filters, Sort sort, AuthenticatedUser user) {
        LocalDate today = LocalDate.now(clock);
        return repository.findAll(spec(filters, user, today), PageRequest.of(0, EXPORT_LIMIT, sort))
                .map(e -> EstimateMapper.toSummary(e, today)).getContent();
    }

    @Transactional(readOnly = true)
    public EstimateResponse get(UUID id, AuthenticatedUser user) {
        return toResponse(load(id, user));
    }

    /** The internal B2C twin. Callers need {@code INTERNAL_B2C} read (checked by the controller). */
    @Transactional(readOnly = true)
    public InternalEstimateDocument internalDocument(UUID id, AuthenticatedUser user) {
        Estimate estimate = load(id, user);
        B2CDocument twin = twinService.findForEstimate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Internal B2C copy", id));
        return EstimateMapper.toInternal(estimate, twin);
    }

    /** Printable partner copy (HTML). Built from the partner-safe response only. */
    @Transactional(readOnly = true)
    public Document partnerDocument(UUID id, AuthenticatedUser user) {
        EstimateResponse estimate = get(id, user);
        return new Document(estimate.estimateNumber() + ".html", EstimateDocumentRenderer.partner(estimate, settings()));
    }

    /** Printable internal B2C copy (HTML), file name INTERNAL-&lt;number&gt;.html as in the source. */
    @Transactional(readOnly = true)
    public Document internalDocumentHtml(UUID id, AuthenticatedUser user) {
        EstimateResponse estimate = get(id, user);
        InternalEstimateDocument twin = internalDocument(id, user);
        return new Document("INTERNAL-" + twin.internalNumber() + ".html",
                EstimateDocumentRenderer.internal(estimate, twin, settings()));
    }

    /** Partner-safe CSV of the filtered list (at most 5,000 rows). */
    @Transactional(readOnly = true)
    public String exportCsv(Filters filters, Sort sort, AuthenticatedUser user) {
        List<List<String>> rows = searchAll(filters, sort, user).stream().map(e -> List.of(
                e.estimateNumber(), e.estimateDate().toString(),
                e.validUntil() == null ? "" : e.validUntil().toString(), e.statusLabel(), e.expired() ? "yes" : "no",
                Objects.toString(e.leadNumber(), ""), Objects.toString(e.clientName(), ""), e.partner().name(),
                e.billTo().getLabel(), e.taxableAmount().toPlainString(), e.grandTotal().toPlainString())).toList();
        return ProductCsv.write(List.of("estimate", "date", "valid_until", "status", "expired", "lead", "client",
                "partner", "bill_to", "taxable_amount", "grand_total"), rows);
    }

    /** A rendered document and its download file name. */
    public record Document(String filename, String html) {
    }

    @Transactional(readOnly = true)
    public List<EstimateHistoryEntry> history(UUID id, AuthenticatedUser user) {
        load(id, user);
        List<AuditLog> entries = auditLogRepository.findTop200ByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                AuditActions.ENTITY_ESTIMATE, id);
        Set<UUID> actorIds = entries.stream().map(AuditLog::getUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return entries.stream().map(entry -> new EstimateHistoryEntry(entry.getId(), entry.getCreatedAt(),
                entry.getUserId() == null ? null : names.get(entry.getUserId()), entry.getAction(),
                changes(entry))).toList();
    }

    @Transactional(readOnly = true)
    public EstimateMeta meta(AuthenticatedUser user) {
        CompanySettings settings = settings();
        return new EstimateMeta(
                Arrays.stream(EstimateStatus.values()).map(s -> new EstimateMeta.Option(s.name(), s.getLabel())).toList(),
                Arrays.stream(BillTo.values()).map(b -> new EstimateMeta.Option(b.name(), b.getLabel())).toList(),
                settings.getEstimateValidityDays(), settings.getTerms(), settings.getDefaultGstPercent(),
                EstimateRules.MAX_LINES, canOverrideFee(user));
    }

    /**
     * Restricted lead picker (Phase 8 decision 4): live leads that aren't Lost, for Estimates writers without Leads
     * read. Only the lead number, client name and partner are returned, and only those fields are searched, so phone,
     * email and other lead details can't be found or inferred through it. {@code leadId} returns that one lead when it
     * is eligible (the "New estimate" link). Data scope as in {@link #leadsVisible}.
     */
    @Transactional(readOnly = true)
    public List<EstimateLeadOption> leadOptions(String query, UUID leadId, AuthenticatedUser user) {
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
                String q = query.trim().toLowerCase(Locale.ROOT);
                String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("clientName")), like, '\\'),
                        cb.like(cb.lower(root.get("leadNumber")), like, '\\'),
                        cb.like(cb.lower(partner.get("name")), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return leadRepository.findAll(spec, PageRequest.of(0, LEAD_OPTIONS_LIMIT,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))))
                .map(l -> new EstimateLeadOption(l.getId(), l.getLeadNumber(), l.getClientName(),
                        new PartnerRef(l.getPartner().getId(), l.getPartner().getName()))).getContent();
    }

    /**
     * Whether the caller may see leads when making estimates: the Estimates data scope must be unrestricted and, when
     * the caller also has Leads access, so must the Leads scope (the narrower one wins). SELF and TEAM can't be
     * evaluated yet (no record owners, D-30), so they show no leads rather than widening to all.
     */
    private boolean leadsVisible(AuthenticatedUser user) {
        return dataScopeService.resolve(user, ModuleCode.ESTIMATES).unrestricted()
                && user.permissions().scope(ModuleCode.LEADS.name()).map(scope -> scope == DataScope.ALL).orElse(true);
    }

    /**
     * Server totals for the editor; nothing is saved. With {@code estimateId}, lines that carry one of that
     * estimate's line ids are priced from their saved snapshot, exactly as a save would.
     */
    @Transactional(readOnly = true)
    public EstimatePreview preview(EstimateRequest request, UUID estimateId, AuthenticatedUser user) {
        Estimate existing = estimateId == null ? null : load(estimateId, user);
        Draft draft = build(request, existing, user, false);
        List<EstimatePreview.Line> lines = new ArrayList<>();
        List<EstimatePreview.InternalLine> internalLines = new ArrayList<>();
        int lineNo = 1;
        for (DraftLine l : draft.lines()) {
            lines.add(new EstimatePreview.Line(lineNo, l.existingId(), l.product().getId(), l.sku(), l.description(),
                    l.hsn(), l.unit(), l.quantity(), l.unitPrice(), l.gstPercent(), l.amount()));
            internalLines.add(new EstimatePreview.InternalLine(lineNo, l.b2cName(), l.b2cRate(), l.b2cAmount(),
                    l.b2cRate().signum() == 0));
            lineNo++;
        }
        EstimatePreview.Internal internal = null;
        if (ProductService.canViewInternal(user)) {
            int missing = (int) internalLines.stream().filter(EstimatePreview.InternalLine::b2cPriceMissing).count();
            internal = new EstimatePreview.Internal(internalLines, TotalsResponse.of(draft.b2cTotals()),
                    draft.b2cTotals().taxableAmount().subtract(draft.partnerTotals().taxableAmount()), missing);
        }
        return new EstimatePreview(lines, TotalsResponse.of(draft.partnerTotals()), draft.defaultFee(), draft.fee(),
                draft.feeOverride(), internal);
    }

    // ------------------------------------------------------------------------------------------- commands

    @Transactional
    public EstimateResponse create(EstimateRequest request, AuthenticatedUser user) {
        Draft draft = build(request, null, user, true);
        authorizeFee(draft, user);
        Estimate estimate = new Estimate();
        estimate.setEstimateNumber(numbers.next(DocumentNumberService.ESTIMATE));
        estimate.setStatus(EstimateStatus.DRAFT);
        apply(estimate, draft);
        repository.saveAndFlush(estimate);
        twinService.syncEstimate(estimate);
        Map<String, Object> after = AuditService.values("estimateNumber", estimate.getEstimateNumber(),
                "status", estimate.getStatus());
        after.putAll(EstimateMapper.auditSnapshot(estimate));
        if (draft.feeOverride()) {
            // D-13: the default it replaced, the rate set and why (actor and time are on the audit row).
            after.put("defaultFeePercent", draft.defaultFee());
            after.put("feeOverrideReason", draft.feeReason());
        }
        auditService.record(AuditActions.ESTIMATE_CREATED, AuditActions.ENTITY_ESTIMATE, estimate.getId(), null, after);
        return toResponse(estimate);
    }

    @Transactional
    public EstimateResponse update(UUID id, EstimateRequest request, AuthenticatedUser user) {
        Estimate estimate = load(id, user);
        if (request == null || request.version() == null) {
            throw ValidationException.of("version", "Version is required.");
        }
        if (request.version() != estimate.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Estimate.class, id);
        }
        if (estimate.getStatus() == EstimateStatus.INVOICED) {
            throw BusinessException.conflict("ESTIMATE_INVOICED", "Invoiced estimates can't be edited.");
        }
        Draft draft = build(request, estimate, user, true);
        authorizeFee(draft, user);

        Map<String, Object> before = EstimateMapper.auditSnapshot(estimate);
        Map<String, Object> after = snapshot(draft, estimate);
        Set<String> changedKeys = new LinkedHashSet<>();
        before.keySet().forEach(k -> {
            if (!Objects.equals(before.get(k), after.get(k))) {
                changedKeys.add(k);
            }
        });
        if (changedKeys.isEmpty()) {
            return toResponse(estimate);
        }
        // Old lines go first: (estimate, line_no) is unique and Hibernate would insert before it deletes.
        estimate.getItems().clear();
        repository.flush();
        apply(estimate, draft);
        if (changedKeys.equals(Set.of("lines"))) {
            // Only line rows changed: bump the version anyway so concurrent editors still conflict.
            entityManager.lock(estimate, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        repository.saveAndFlush(estimate);
        twinService.syncEstimate(estimate);
        Map<String, Object> newValues = only(after, changedKeys);
        if (changedKeys.contains("feePercent") && draft.feeOverride()) {
            // D-13: previous rate (old values), new rate, the default it replaces and the reason.
            newValues.put("defaultFeePercent", draft.defaultFee());
            newValues.put("feeOverrideReason", draft.feeReason());
        }
        auditService.record(AuditActions.ESTIMATE_UPDATED, AuditActions.ENTITY_ESTIMATE, id,
                only(before, changedKeys), newValues);
        return toResponse(estimate);
    }

    /** BR-E4 draft → sent; the lead moves to "Estimate sent" if it is earlier in the funnel (§4.3). */
    @Transactional
    public EstimateResponse send(UUID id, Long version, AuthenticatedUser user) {
        return transition(id, version, user, EstimateStatus.DRAFT, EstimateStatus.SENT,
                "Only draft estimates can be marked sent.",
                stage -> stage.ordinal() < LeadStage.ESTIMATE_SENT.ordinal() ? LeadStage.ESTIMATE_SENT : stage);
    }

    /** BR-E4 sent → accepted; the lead is Won (§4.3). Reopening a Lost lead this way needs {@code LEADS:APPROVE}. */
    @Transactional
    public EstimateResponse accept(UUID id, Long version, AuthenticatedUser user) {
        return transition(id, version, user, EstimateStatus.SENT, EstimateStatus.ACCEPTED,
                "Only sent estimates can be accepted.", stage -> LeadStage.WON);
    }

    /** BR-E4 sent → rejected; the lead is Lost unless it is already Won (§4.3). */
    @Transactional
    public EstimateResponse reject(UUID id, Long version, AuthenticatedUser user) {
        return transition(id, version, user, EstimateStatus.SENT, EstimateStatus.REJECTED,
                "Only sent estimates can be rejected.", stage -> stage == LeadStage.WON ? LeadStage.WON : LeadStage.LOST);
    }

    /** Soft delete with its twin kept for history. Refused once the estimate is invoiced (D-26). */
    @Transactional
    public void delete(UUID id, AuthenticatedUser user) {
        Estimate estimate = lock(id, user);
        if (estimate.getStatus() == EstimateStatus.INVOICED) {
            throw BusinessException.conflict("ESTIMATE_INVOICED", "Invoiced estimates can't be deleted.");
        }
        Map<String, Object> before = AuditService.values("estimateNumber", estimate.getEstimateNumber(),
                "status", estimate.getStatus());
        before.putAll(EstimateMapper.auditSnapshot(estimate));
        estimate.setDeletedAt(clock.instant());
        estimate.setDeletedBy(user.id());
        repository.saveAndFlush(estimate);
        auditService.record(AuditActions.ESTIMATE_DELETED, AuditActions.ENTITY_ESTIMATE, id, before, null);
    }

    // ------------------------------------------------------------------------------------------- helpers

    public static boolean canOverrideFee(AuthenticatedUser user) {
        return user.permissions().canPerform(FEE_APPROVER_MODULE, "APPROVE");
    }

    private EstimateResponse transition(UUID id, Long version, AuthenticatedUser user, EstimateStatus from,
                                        EstimateStatus to, String refusal, UnaryOperator<LeadStage> leadRule) {
        Estimate estimate = lock(id, user);
        if (version != null && version != estimate.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Estimate.class, id);
        }
        if (estimate.getStatus() != from) {
            throw BusinessException.conflict("INVALID_STATUS_TRANSITION", refusal);
        }
        estimate.setStatus(to);
        if (to == EstimateStatus.SENT) {
            estimate.setSentOn(LocalDate.now(clock));
        }
        repository.saveAndFlush(estimate);
        if (estimate.getLead() != null) {
            leadService.applyAutomaticStage(estimate.getLead().getId(), leadRule,
                    "Estimate " + estimate.getEstimateNumber() + " marked " + to.getLabel(), user);
        }
        auditService.record(AuditActions.ESTIMATE_STATUS_CHANGED, AuditActions.ENTITY_ESTIMATE, id,
                AuditService.values("status", from),
                to == EstimateStatus.SENT ? AuditService.values("status", to, "sentOn", estimate.getSentOn())
                        : AuditService.values("status", to));
        return toResponse(estimate);
    }

    private void authorizeFee(Draft draft, AuthenticatedUser user) {
        if (!draft.feeOverride() || !draft.feeChanged()) {
            return;
        }
        if (!canOverrideFee(user)) {
            throw new BusinessException("FEE_OVERRIDE_NOT_PERMITTED", "The referral fee for this estimate is "
                    + draft.defaultFee().stripTrailingZeros().toPlainString()
                    + "%. Changing it needs the Partners approve permission.", HttpStatus.FORBIDDEN);
        }
        if (draft.feeReason() == null) {
            throw ValidationException.of("feeOverrideReason", "Give a reason for changing the referral fee.");
        }
    }

    // ---- building a draft from the request

    private record DraftLine(UUID existingId, Product product, String sku, String description, String b2cName,
                             String hsn, String unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal b2cRate,
                             BigDecimal gstPercent) {

        BigDecimal amount() {
            return DocumentCalculator.lineAmount(quantity, unitPrice);
        }

        BigDecimal b2cAmount() {
            return DocumentCalculator.lineAmount(quantity, b2cRate);
        }
    }

    private record Draft(Lead lead, Partner partner, LocalDate date, LocalDate validUntil, BigDecimal discount,
                         boolean igst, BillTo billTo, BigDecimal fee, BigDecimal defaultFee, boolean feeOverride,
                         boolean feeChanged, String feeReason, String notes, List<DraftLine> lines,
                         DocumentCalculator.Totals partnerTotals, DocumentCalculator.Totals b2cTotals) {
    }

    /**
     * @param saving false for the preview, which may run before the tax type is chosen
     */
    private Draft build(EstimateRequest r, Estimate existing, AuthenticatedUser user, boolean saving) {
        if (r == null) {
            throw ValidationException.of("leadId", "Choose a lead so the document knows the client and the partner.");
        }
        List<FieldError> errors = new ArrayList<>();
        CompanySettings settings = settings();

        Lead lead = null;
        boolean sameLead = existing != null && existing.getLead() != null && existing.getLead().getId().equals(r.leadId());
        if (r.leadId() == null) {
            errors.add(new FieldError("leadId", "Choose a lead so the document knows the client and the partner."));
        } else if (sameLead) {
            lead = existing.getLead();
        } else {
            // Leads outside the caller's scope are treated as missing, as in the lead picker (decision 4).
            lead = leadsVisible(user) ? leadRepository.findByIdAndDeletedAtIsNull(r.leadId()).orElse(null) : null;
            if (lead == null) {
                errors.add(new FieldError("leadId", "This lead doesn't exist or was deleted."));
            } else if (lead.getStage() == LeadStage.LOST) {
                // BR-E5: Lost leads aren't offered for new estimates.
                errors.add(new FieldError("leadId", "Lost leads can't get new estimates. Reopen the lead first."));
            }
        }

        LocalDate date = r.estimateDate() != null ? r.estimateDate()
                : existing != null ? existing.getEstimateDate() : LocalDate.now(clock);
        LocalDate validUntil = r.validUntil() != null ? r.validUntil()
                : date.plusDays(settings.getEstimateValidityDays());
        if (validUntil.isBefore(date)) {
            errors.add(new FieldError("validUntil", "Valid until can't be before the estimate date."));
        }
        BigDecimal discount = r.discountPercent() == null ? BigDecimal.ZERO.setScale(3)
                : EstimateRules.percent(r.discountPercent(), "discountPercent", "discount", errors);
        BillTo billTo = r.billTo() == null ? BillTo.CLIENT : r.billTo();
        // D-21: the tax type is never assumed. A new estimate must say CGST + SGST or IGST; an edit keeps the stored
        // choice when it is left out. (The preview, which saves nothing, prices without it: the total is the same.)
        boolean igst;
        if (r.igst() != null) {
            igst = r.igst();
        } else if (existing != null) {
            igst = existing.isIgst();
        } else {
            igst = false;
            if (saving) {
                errors.add(new FieldError("igst", "Choose CGST + SGST (same state) or IGST (other state)."));
            }
        }
        String notes = EstimateRules.text(r.notes(), "notes", EstimateRules.MAX_NOTES, errors);
        String reason = EstimateRules.text(r.feeOverrideReason(), "feeOverrideReason", 500, errors);

        List<DraftLine> lines = lines(r.lines() == null ? List.of() : r.lines(), existing, errors);
        if (lines.isEmpty() && errors.stream().noneMatch(e -> e.field().startsWith("lines"))) {
            errors.add(new FieldError("lines", "Add at least one item."));
        }
        if (lines.size() > EstimateRules.MAX_LINES) {
            errors.add(new FieldError("lines", "An estimate can have at most " + EstimateRules.MAX_LINES + " lines."));
        }

        BigDecimal fee = null;
        if (billTo == BillTo.PARTNER) {
            if (r.feePercent() != null && r.feePercent().signum() != 0) {
                errors.add(new FieldError("feePercent", "Partner purchases carry no referral fee."));
            }
            fee = BigDecimal.ZERO.setScale(3);
        } else if (r.feePercent() != null) {
            fee = EstimateRules.percent(r.feePercent(), "feePercent", "referral fee", errors);
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        Partner partner = sameLead ? existing.getPartner() : lead.getPartner();
        boolean sameContext = sameLead && existing.getBillTo() == billTo;
        BigDecimal defaultFee = EstimateRules.defaultFee(billTo, lead);
        if (fee == null) {
            fee = sameContext ? existing.getFeePercent() : defaultFee;
        }
        boolean feeOverride = fee.compareTo(defaultFee) != 0;
        boolean feeChanged = !sameContext || fee.compareTo(existing.getFeePercent()) != 0;

        DocumentCalculator.Totals partnerTotals = DocumentCalculator.totals(lines.stream()
                .map(l -> new DocumentCalculator.Line(l.quantity(), l.unitPrice(), l.gstPercent())).toList(), discount);
        DocumentCalculator.Totals b2cTotals = DocumentCalculator.totals(lines.stream()
                .map(l -> new DocumentCalculator.Line(l.quantity(), l.b2cRate(), l.gstPercent())).toList(), discount);
        boolean tooLarge = partnerTotals.grandTotal().compareTo(EstimateRules.MAX_MONEY) > 0
                || b2cTotals.grandTotal().compareTo(EstimateRules.MAX_MONEY) > 0
                || lines.stream().anyMatch(l -> l.amount().compareTo(EstimateRules.MAX_MONEY) > 0
                || l.b2cAmount().compareTo(EstimateRules.MAX_MONEY) > 0);
        if (tooLarge) {
            throw ValidationException.of("lines", "The estimate total is too large.");
        }
        return new Draft(lead, partner, date, validUntil, discount, igst, billTo, fee,
                defaultFee, feeOverride, feeChanged, reason, notes, lines, partnerTotals, b2cTotals);
    }

    /** §6.2 snapshots. Lines without a product are dropped, as in the source. */
    private List<DraftLine> lines(List<EstimateRequest.Line> input, Estimate existing, List<FieldError> errors) {
        Map<UUID, EstimateItem> saved = existing == null ? Map.of()
                : existing.getItems().stream().collect(Collectors.toMap(EstimateItem::getId, Function.identity()));
        List<DraftLine> lines = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            EstimateRequest.Line in = input.get(i);
            if (in == null || in.productId() == null) {
                continue;
            }
            String f = "lines[" + i + "]";
            EstimateItem kept = in.id() == null ? null : saved.get(in.id());
            if (in.id() != null && kept == null) {
                errors.add(new FieldError(f + ".id", "This line isn't part of the estimate any more. Reload and try again."));
                continue;
            }
            BigDecimal quantity = EstimateRules.quantity(in.quantity(), f + ".quantity", errors);
            if (kept != null && kept.getProduct() != null && kept.getProduct().getId().equals(in.productId())) {
                // Saved line: keep its snapshot; only quantity, partner rate and GST may change (decision 5).
                BigDecimal rate = in.unitPrice() == null ? kept.getUnitPrice()
                        : EstimateRules.rate(in.unitPrice(), f + ".unitPrice", errors);
                BigDecimal gst = in.gstPercent() == null ? kept.getGstPercent()
                        : EstimateRules.percent(in.gstPercent(), f + ".gstPercent", "GST", errors);
                if (quantity != null && rate != null && gst != null) {
                    lines.add(new DraftLine(kept.getId(), kept.getProduct(), kept.getProduct().getSku(),
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
            BigDecimal rate = in.unitPrice() == null ? snap.rate()
                    : EstimateRules.rate(in.unitPrice(), f + ".unitPrice", errors);
            BigDecimal gst = in.gstPercent() == null ? snap.gstPercent().setScale(3)
                    : EstimateRules.percent(in.gstPercent(), f + ".gstPercent", "GST", errors);
            if (quantity != null && rate != null && gst != null) {
                lines.add(new DraftLine(null, productRepository.getReferenceById(snap.productId()), snap.sku(),
                        snap.name(), snap.b2cName(), snap.hsn(), snap.unit(), quantity, rate.setScale(2),
                        snap.b2cRate().setScale(2), gst));
            }
        }
        return lines;
    }

    private static void apply(Estimate estimate, Draft d) {
        estimate.setPartner(d.partner());
        estimate.setLead(d.lead());
        estimate.setEstimateDate(d.date());
        estimate.setValidUntil(d.validUntil());
        estimate.setDiscountPercent(d.discount());
        estimate.setIgst(d.igst());
        estimate.setBillTo(d.billTo());
        estimate.setFeePercent(d.fee());
        estimate.setNotes(d.notes());
        DocumentCalculator.Totals t = d.partnerTotals();
        estimate.setTotals(new DocumentTotals(t.subtotal(), t.discountAmount(), t.taxableAmount(), t.gstAmount(),
                t.grandTotal()));
        int lineNo = 1;
        for (DraftLine l : d.lines()) {
            EstimateItem item = new EstimateItem();
            item.setLineNo(lineNo++);
            item.setProduct(l.product());
            item.setDescription(l.description());
            item.setB2cName(l.b2cName());
            item.setHsn(l.hsn());
            item.setUnit(l.unit());
            item.setQuantity(l.quantity());
            item.setUnitPrice(l.unitPrice());
            item.setB2cUnitPrice(l.b2cRate());
            item.setGstPercent(l.gstPercent());
            item.setLineTotal(l.amount());
            estimate.addItem(item);
        }
    }

    /** The audit snapshot the estimate will have once {@code d} is applied (same keys as {@link EstimateMapper#auditSnapshot}). */
    private static Map<String, Object> snapshot(Draft d, Estimate estimate) {
        List<Map<String, Object>> lines = new ArrayList<>();
        int lineNo = 1;
        for (DraftLine l : d.lines()) {
            lines.add(EstimateMapper.auditLine(lineNo++, l.product().getId(), l.description(), l.quantity(),
                    l.unitPrice(), l.gstPercent()));
        }
        return AuditService.values(
                "leadId", d.lead().getId(),
                "partnerId", d.partner().getId(),
                "estimateDate", d.date(),
                "validUntil", d.validUntil(),
                "discountPercent", d.discount(),
                "igst", d.igst(),
                "billTo", d.billTo(),
                "feePercent", d.fee(),
                "notes", d.notes(),
                "lines", lines,
                "grandTotal", d.partnerTotals().grandTotal());
    }

    // ---- loading, scope, responses

    private Estimate load(UUID id, AuthenticatedUser user) {
        requireScope(id, user);
        return repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Estimate", id));
    }

    private Estimate lock(UUID id, AuthenticatedUser user) {
        requireScope(id, user);
        return repository.lockLive(id).orElseThrow(() -> new ResourceNotFoundException("Estimate", id));
    }

    /** Records outside the caller's data scope are reported as missing (P3-07). */
    private void requireScope(UUID id, AuthenticatedUser user) {
        if (!dataScopeService.resolve(user, ModuleCode.ESTIMATES).unrestricted()) {
            throw new ResourceNotFoundException("Estimate", id);
        }
    }

    private EstimateResponse toResponse(Estimate estimate) {
        String createdBy = estimate.getCreatedBy() == null ? null
                : userRepository.findById(estimate.getCreatedBy()).map(User::getFullName).orElse(null);
        return EstimateMapper.toResponse(estimate, LocalDate.now(clock), createdBy);
    }

    private CompanySettings settings() {
        return settingsRepository.findSingleton().orElseThrow(() -> new IllegalStateException("Company settings are missing"));
    }

    private List<EstimateHistoryEntry.Change> changes(AuditLog entry) {
        JsonNode before = parse(entry.getOldValues());
        JsonNode after = parse(entry.getNewValues());
        Set<String> fields = new LinkedHashSet<>();
        before.propertyNames().forEach(fields::add);
        after.propertyNames().forEach(fields::add);
        return fields.stream().map(field -> new EstimateHistoryEntry.Change(field,
                before.path(field).isMissingNode() ? null : before.get(field),
                after.path(field).isMissingNode() ? null : after.get(field))).toList();
    }

    private JsonNode parse(String json) {
        return json == null ? objectMapper.createObjectNode() : objectMapper.readTree(json);
    }

    /** The entries of {@code snapshot} for {@code keys}, in snapshot order. */
    private static Map<String, Object> only(Map<String, Object> snapshot, Set<String> keys) {
        Map<String, Object> out = AuditService.values();
        snapshot.forEach((k, v) -> {
            if (keys.contains(k)) {
                out.put(k, v);
            }
        });
        return out;
    }

    private Specification<Estimate> spec(Filters filters, AuthenticatedUser user, LocalDate today) {
        return dataScopeService.resolve(user, ModuleCode.ESTIMATES).<Estimate>toSpecification()
                .and(matching(filters, today));
    }

    private static Specification<Estimate> matching(Filters f, LocalDate today) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            boolean counting = cq != null && Long.class == cq.getResultType();
            // One join each serves the search and, for page queries, fetches the partner and lead for the rows.
            @SuppressWarnings("unchecked")
            From<Estimate, Partner> partner = counting ? root.join("partner", JoinType.INNER)
                    : (From<Estimate, Partner>) (Object) root.<Estimate, Partner>fetch("partner", JoinType.INNER);
            @SuppressWarnings("unchecked")
            From<Estimate, Lead> lead = counting ? root.join("lead", JoinType.LEFT)
                    : (From<Estimate, Lead>) (Object) root.<Estimate, Lead>fetch("lead", JoinType.LEFT);
            if (f.query() != null && !f.query().isBlank()) {
                String q = f.query().trim().toLowerCase(Locale.ROOT);
                String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("estimateNumber")), like, '\\'),
                        cb.like(cb.lower(partner.get("name")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(lead.get("clientName"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(lead.get("leadNumber"), "")), like, '\\')));
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
            if (f.expired() != null) {
                Predicate expired = cb.and(cb.equal(root.get("status"), EstimateStatus.SENT),
                        cb.lessThan(root.get("validUntil"), today));
                predicates.add(f.expired() ? expired : cb.not(expired));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
