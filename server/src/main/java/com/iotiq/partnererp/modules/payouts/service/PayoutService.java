package com.iotiq.partnererp.modules.payouts.service;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.audit.entity.AuditLog;
import com.iotiq.partnererp.modules.audit.repository.AuditLogRepository;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.auth.service.DataScopeService;
import com.iotiq.partnererp.modules.invoices.entity.Invoice;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import com.iotiq.partnererp.modules.partners.mapper.PartnerMapper;
import com.iotiq.partnererp.modules.partners.repository.PartnerRepository;
import com.iotiq.partnererp.modules.partners.service.PartnerService;
import com.iotiq.partnererp.modules.payments.entity.Payment;
import com.iotiq.partnererp.modules.payments.repository.PaymentRepository;
import com.iotiq.partnererp.modules.payments.service.PaymentRules;
import com.iotiq.partnererp.modules.payouts.dto.PayoutActionRequest;
import com.iotiq.partnererp.modules.payouts.dto.PayoutCreateRequest;
import com.iotiq.partnererp.modules.payouts.dto.PayoutDashboard;
import com.iotiq.partnererp.modules.payouts.dto.PayoutHistoryEntry;
import com.iotiq.partnererp.modules.payouts.dto.PayoutLineView;
import com.iotiq.partnererp.modules.payouts.dto.PayoutMarkPaidRequest;
import com.iotiq.partnererp.modules.payouts.dto.PayoutMeta;
import com.iotiq.partnererp.modules.payouts.dto.PayoutPreview;
import com.iotiq.partnererp.modules.payouts.dto.PayoutPreviewRequest;
import com.iotiq.partnererp.modules.payouts.dto.PayoutResponse;
import com.iotiq.partnererp.modules.payouts.dto.PayoutSummary;
import com.iotiq.partnererp.modules.payouts.dto.PayoutTds;
import com.iotiq.partnererp.modules.payouts.dto.TdsAdjustmentRequest;
import com.iotiq.partnererp.modules.payouts.entity.PartnerAdjustment;
import com.iotiq.partnererp.modules.payouts.entity.Payout;
import com.iotiq.partnererp.modules.payouts.entity.PayoutEnums.AdjustmentStatus;
import com.iotiq.partnererp.modules.payouts.entity.PayoutEnums.LineType;
import com.iotiq.partnererp.modules.payouts.entity.PayoutEnums.ReleaseReason;
import com.iotiq.partnererp.modules.payouts.entity.PayoutEnums.TdsEvent;
import com.iotiq.partnererp.modules.payouts.entity.PayoutLine;
import com.iotiq.partnererp.modules.payouts.entity.PayoutStatus;
import com.iotiq.partnererp.modules.payouts.entity.PayoutTdsCalculation;
import com.iotiq.partnererp.modules.payouts.entity.TdsConfiguration;
import com.iotiq.partnererp.modules.payouts.repository.PartnerAdjustmentRepository;
import com.iotiq.partnererp.modules.payouts.repository.PayoutLineRepository;
import com.iotiq.partnererp.modules.payouts.repository.PayoutQueries;
import com.iotiq.partnererp.modules.payouts.repository.PayoutRepository;
import com.iotiq.partnererp.modules.payouts.repository.PayoutTdsCalculationRepository;
import com.iotiq.partnererp.modules.payouts.repository.TdsConfigurationRepository;
import com.iotiq.partnererp.modules.payouts.service.PayoutRules.TdsResult;
import com.iotiq.partnererp.modules.payouts.service.PayoutRules.TdsStatus;
import com.iotiq.partnererp.modules.products.service.ProductCsv;
import com.iotiq.partnererp.modules.sequences.service.DocumentNumberService;
import com.iotiq.partnererp.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Partner payouts (Phase 11, DECISIONS.md §L).
 *
 * <ul>
 *   <li>A payout is a batch for one partner, generated from the eligible receipts (standing, invoice fee % above
 *   zero, dated on or before the as-of date, not in a live payout) and the approved adjustments carried forward.
 *   Commission per receipt is r2(ex-GST amount × the invoice's fee %), the rate the invoice was issued with.</li>
 *   <li>Lifecycle: Draft → Pending approval → Approved → Processing → Paid; Rejected, Cancelled and Failed release
 *   the lines for a later payout. The creator or submitter can't approve or reject; disbursement needs
 *   {@code PAYOUTS:MARK_PAYOUT_PAID}. There is no bank integration: Processing and Paid record what Finance did.</li>
 *   <li>Reversals (D-12): a draft or pending payout holding a reversed receipt must be recalculated; an approved or
 *   processing one is blocked; a paid one never changes and a negative adjustment is carried forward instead.</li>
 *   <li>TDS (D-08) comes from the confirmed configuration in force on the as-of date. Without one, payouts can be
 *   previewed and drafted but not approved.</li>
 * </ul>
 */
@Service
public class PayoutService {

    public static final String MARK_PAID = "MARK_PAYOUT_PAID";
    public static final String TDS_INCOMPLETE = "TDS settings aren't confirmed for this date. Finance must enter "
            + "them in Settings and a second person confirm them before payouts can be approved.";
    static final String PAN_NOT_VERIFIED = "The partner's PAN isn't verified and no rate for partners without a "
            + "verified PAN is configured. Verify the PAN on the partner's page, then recalculate.";
    static final int EXPORT_LIMIT = 5_000;
    private static final Set<PayoutStatus> OPEN = EnumSet.of(PayoutStatus.DRAFT, PayoutStatus.PENDING_APPROVAL);
    private static final Set<PayoutStatus> UNPAID = EnumSet.of(PayoutStatus.DRAFT, PayoutStatus.PENDING_APPROVAL,
            PayoutStatus.APPROVED, PayoutStatus.PROCESSING);

    private final PayoutRepository repository;
    private final PayoutLineRepository lineRepository;
    private final PartnerAdjustmentRepository adjustmentRepository;
    private final PayoutTdsCalculationRepository calculationRepository;
    private final TdsConfigurationRepository tdsRepository;
    private final PayoutQueries queries;
    private final PartnerRepository partnerRepository;
    private final PaymentRepository paymentRepository;
    private final PartnerAdjustmentService adjustments;
    private final DocumentNumberService numbers;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;
    private final UserNames userNames;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final Clock clock;

    public PayoutService(PayoutRepository repository, PayoutLineRepository lineRepository,
                         PartnerAdjustmentRepository adjustmentRepository,
                         PayoutTdsCalculationRepository calculationRepository, TdsConfigurationRepository tdsRepository,
                         PayoutQueries queries, PartnerRepository partnerRepository,
                         PaymentRepository paymentRepository, PartnerAdjustmentService adjustments,
                         DocumentNumberService numbers, DataScopeService dataScopeService, AuditService auditService,
                         AuditLogRepository auditLogRepository, UserNames userNames, ObjectMapper objectMapper,
                         EntityManager entityManager, Clock clock) {
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.calculationRepository = calculationRepository;
        this.tdsRepository = tdsRepository;
        this.queries = queries;
        this.partnerRepository = partnerRepository;
        this.paymentRepository = paymentRepository;
        this.adjustments = adjustments;
        this.numbers = numbers;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;
        this.userNames = userNames;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public record Filters(String query, PayoutStatus status, UUID partnerId, LocalDate from, LocalDate to) {
    }

    /** @param created false when the idempotency key was already used and the existing payout is returned */
    public record Created(PayoutResponse payout, boolean created) {
    }

    /** What a payout run would allocate now. */
    private record Plan(List<PayoutQueries.EligibleReceipt> receipts, List<PartnerAdjustment> adjustments,
                        BigDecimal receiptsTotal, BigDecimal adjustmentsTotal) {

        BigDecimal commission() {
            return receiptsTotal.add(adjustmentsTotal);
        }

        boolean isEmpty() {
            return receipts.isEmpty() && adjustments.isEmpty();
        }
    }

    /** A TDS calculation with its inputs. */
    private record TdsOutcome(TdsConfiguration config, PayoutQueries.PriorFy prior, boolean panVerified,
                              TdsResult result) {
    }

    // ------------------------------------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<PayoutSummary> search(Filters filters, Pageable pageable, AuthenticatedUser user) {
        Page<Payout> page = repository.findAll(spec(filters, user), pageable);
        Set<UUID> blocked = blocked(page.getContent());
        return page.map(p -> summary(p, blocked));
    }

    /** CSV of the filtered payouts (at most 5,000). Bank details are never exported. */
    @Transactional(readOnly = true)
    public String exportCsv(Filters filters, Sort sort, AuthenticatedUser user) {
        List<Payout> rows = repository.findAll(spec(filters, user), PageRequest.of(0, EXPORT_LIMIT, sort)).getContent();
        Set<UUID> blocked = blocked(rows);
        List<List<String>> lines = rows.stream().map(p -> List.of(
                Objects.toString(p.getPayoutNumber(), p.isLegacy() ? "legacy" : ""), p.getPartner().getName(),
                Objects.toString(p.getPartner().getPartnerCode(), ""), p.getStatus().getLabel(),
                p.getAsOfDate().toString(), PayoutRules.fyLabel(p.getFyStart()), plain(p.getCommissionTotal()),
                Objects.toString(p.getTdsSection(), ""), plain(p.getTdsRate()), plain(p.getTdsBase()),
                plain(p.getTdsAmount()), p.isTdsAdjusted() ? "yes" : "", plain(p.getNetAmount()),
                p.getTdsStatus(), blocked.contains(p.getId()) ? "yes" : "",
                p.getPaidDate() == null ? "" : p.getPaidDate().toString(),
                Objects.toString(p.getPaymentReference(), ""), Objects.toString(p.getStatusReason(), ""),
                p.getCreatedAt() == null ? "" : p.getCreatedAt().toString())).toList();
        return ProductCsv.write(List.of("payout", "partner", "partner_code", "status", "as_of", "financial_year",
                "commission", "tds_section", "tds_rate", "tds_base", "tds", "tds_adjusted", "net_payable",
                "tds_status", "reversed_receipts", "paid_date", "payment_reference", "reason", "created_at"), lines);
    }

    @Transactional(readOnly = true)
    public PayoutResponse get(UUID id, AuthenticatedUser user) {
        return toResponse(load(id, user), user);
    }

    /** Audited events of the payout, newest first. */
    @Transactional(readOnly = true)
    public List<PayoutHistoryEntry> history(UUID id, AuthenticatedUser user) {
        Payout payout = load(id, user);
        List<AuditLog> entries = auditLogRepository.findTop200ByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                AuditActions.ENTITY_PAYOUT, payout.getId());
        Map<UUID, String> names = userNames.of(entries.stream().map(AuditLog::getUserId).collect(Collectors.toSet()));
        return entries.stream().map(e -> new PayoutHistoryEntry(e.getId(), e.getCreatedAt(),
                e.getUserId() == null ? null : names.get(e.getUserId()), e.getAction(), changes(e))).toList();
    }

    @Transactional(readOnly = true)
    public PayoutMeta meta(AuthenticatedUser user) {
        boolean configured = tdsRepository.inForce(LocalDate.now(clock)).isPresent();
        return new PayoutMeta(
                Arrays.stream(PayoutStatus.values()).map(s -> new PayoutMeta.Option(s.name(), s.getLabel())).toList(),
                Arrays.stream(AdjustmentStatus.values()).map(s -> new PayoutMeta.Option(s.name(), s.getLabel())).toList(),
                PayoutRules.MAX_REASON, PayoutRules.MAX_NOTES, PayoutRules.MAX_REFERENCE, configured,
                configured ? null : TDS_INCOMPLETE, canWrite(user), canApprove(user), canMarkPaid(user),
                user.permissions().canPerform(ModuleCode.PAYOUTS.name(), "EXPORT"), TdsConfigurationService.canEdit(user),
                PartnerService.canSeeSensitive(user));
    }

    /** Totals for the payout dashboard: payouts by status, and what is waiting to be paid out. */
    @Transactional(readOnly = true)
    public PayoutDashboard dashboard(AuthenticatedUser user) {
        LocalDate today = LocalDate.now(clock);
        LocalDate fy = PayoutRules.fyStart(today);
        boolean configured = tdsRepository.inForce(today).isPresent();
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        if (!dataScopeService.resolve(user, ModuleCode.PAYOUTS).unrestricted()) {
            return new PayoutDashboard(PayoutRules.fyLabel(fy), List.of(), zero, 0, zero, 0, zero, zero, 0, configured);
        }
        List<PayoutQueries.Balance> balances = queries.balances(null, null, today, fy);
        Map<String, PayoutQueries.StatusTotal> totals = queries.statusTotals().stream()
                .collect(Collectors.toMap(PayoutQueries.StatusTotal::status, t -> t));
        List<PayoutDashboard.StatusTotal> byStatus = Arrays.stream(PayoutStatus.values()).map(s -> {
            PayoutQueries.StatusTotal t = totals.get(s.name());
            return t == null ? new PayoutDashboard.StatusTotal(s.name(), s.getLabel(), 0, zero, zero, zero)
                    : new PayoutDashboard.StatusTotal(s.name(), s.getLabel(), t.count(), t.commission(), t.tds(), t.net());
        }).toList();
        return new PayoutDashboard(PayoutRules.fyLabel(fy), byStatus,
                sum(balances, PayoutQueries.Balance::eligibleCommission),
                balances.stream().mapToLong(PayoutQueries.Balance::eligibleReceipts).sum(),
                sum(balances, PayoutQueries.Balance::carriedAdjustments),
                balances.stream().mapToLong(PayoutQueries.Balance::pendingAdjustments).sum(),
                sum(balances, PayoutQueries.Balance::paidFy), sum(balances, PayoutQueries.Balance::tdsFy),
                balances.stream().filter(b -> b.available().signum() > 0).count(), configured);
    }

    /** Payout position of every partner with commission, adjustments or payouts (optionally searched). */
    @Transactional(readOnly = true)
    public List<PayoutQueries.Balance> balances(String query, AuthenticatedUser user) {
        if (!dataScopeService.resolve(user, ModuleCode.PAYOUTS).unrestricted()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        return queries.balances(null, query == null || query.isBlank() ? null : PayoutRules.like(query), today,
                PayoutRules.fyStart(today));
    }

    /** One partner's payout position (zero when the partner has no payout activity). */
    @Transactional(readOnly = true)
    public PayoutQueries.Balance balance(UUID partnerId, AuthenticatedUser user) {
        if (!dataScopeService.resolve(user, ModuleCode.PAYOUTS).unrestricted()) {
            throw new ResourceNotFoundException("Partner", partnerId);
        }
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", partnerId));
        LocalDate today = LocalDate.now(clock);
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return queries.balances(partnerId, null, today, PayoutRules.fyStart(today)).stream().findFirst()
                .orElse(new PayoutQueries.Balance(partner.getId(), partner.getName(), partner.getPartnerCode(),
                        partner.getPan() != null, partner.isPanVerified(), partner.getBankAccount() != null, 0, zero,
                        zero, 0, zero, 0, zero, zero, zero, zero, null));
    }

    /** What a payout for the partner would contain now. Nothing is saved. */
    @Transactional(readOnly = true)
    public PayoutPreview preview(PayoutPreviewRequest r, AuthenticatedUser user) {
        requireScope(user);
        if (r == null || r.partnerId() == null) {
            throw ValidationException.of("partnerId", "Choose the partner.");
        }
        LocalDate asOf = asOfDate(r.asOfDate());
        Partner partner = partnerRepository.findById(r.partnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Partner", r.partnerId()));
        Plan plan = plan(partner.getId(), asOf);
        LocalDate fy = PayoutRules.fyStart(asOf);
        TdsOutcome tds = computeTds(partner, asOf, fy, null, plan.commission());
        Payout open = repository.findFirstByPartnerIdAndStatusIn(partner.getId(), OPEN).orElse(null);
        List<String> blockers = new ArrayList<>();
        if (open != null) {
            blockers.add("Payout " + open.getPayoutNumber() + " is already open for this partner. Recalculate or "
                    + "finish it first.");
        }
        if (plan.isEmpty()) {
            blockers.add("Nothing to pay: no eligible receipts or carried adjustments up to " + asOf + ".");
        } else if (plan.commission().signum() <= 0) {
            blockers.add("Carried adjustments (" + rupees(plan.adjustmentsTotal()) + ") exceed the eligible "
                    + "commission, so nothing can be paid yet. They stay carried forward.");
        }
        blockers.addAll(approvalWarnings(partner, tds.result(), plan.commission()));
        List<PayoutLineView> lines = new ArrayList<>();
        plan.receipts().forEach(e -> lines.add(new PayoutLineView(null, LineType.COMMISSION.name(), e.paymentId(),
                e.paymentNumber(), e.invoiceId(), e.invoiceNumber(), e.paymentDate(), e.netAmount(), e.feePercent(),
                PayoutRules.commission(e.netAmount(), e.feePercent()), null, null, null, null, false, null, null)));
        plan.adjustments().forEach(a -> lines.add(adjustmentView(null, a, null, null)));
        BigDecimal net = tds.result().tds() == null ? null : plan.commission().subtract(tds.result().tds());
        return new PayoutPreview(new PartnerRef(partner.getId(), partner.getName()), asOf, PayoutRules.fyLabel(fy),
                lines, plan.receiptsTotal(), plan.adjustmentsTotal(), plan.commission(),
                tdsView(tds, tds.result().tds(), false, partner.isPanVerified()), net,
                open == null && !plan.isEmpty() && plan.commission().signum() > 0 && canWrite(user), blockers,
                open == null ? null : summary(open, Set.of()));
    }

    // ------------------------------------------------------------------------------------------- generation

    /**
     * Generate a draft payout for a partner ({@code PAYOUTS:WRITE}, checked by the controller). The partner row is
     * locked, a partner has at most one draft or pending payout, and each receipt or adjustment is allocated to at
     * most one live payout (unique indexes), so concurrent runs can never pay the same commission twice.
     */
    @Transactional
    public Created create(PayoutCreateRequest r, AuthenticatedUser user) {
        requireScope(user);
        String notes = r == null ? null : PayoutRules.clean(r.notes());
        List<FieldError> errors = new ArrayList<>();
        if (r == null || r.partnerId() == null) {
            errors.add(new FieldError("partnerId", "Choose the partner."));
        }
        if (notes != null && notes.length() > PayoutRules.MAX_NOTES) {
            errors.add(new FieldError("notes", "Must be at most " + PayoutRules.MAX_NOTES + " characters."));
        }
        if (r == null || r.idempotencyKey() == null) {
            errors.add(new FieldError("idempotencyKey", "Reload the form and submit it again."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        LocalDate asOf = asOfDate(r.asOfDate());
        String hash = PayoutRules.requestHash(r.partnerId(), asOf, notes);
        Created replay = replay(r.idempotencyKey(), hash, user);
        if (replay != null) {
            return replay;
        }
        Partner partner = partnerRepository.lockById(r.partnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Partner", r.partnerId()));
        // A resubmission that waited for the partner lock finds the payout its first submission created.
        replay = replay(r.idempotencyKey(), hash, user);
        if (replay != null) {
            return replay;
        }
        Payout open = repository.findFirstByPartnerIdAndStatusIn(partner.getId(), OPEN).orElse(null);
        if (open != null) {
            throw BusinessException.conflict("PAYOUT_OPEN_EXISTS", "Payout " + open.getPayoutNumber()
                    + " is already open for this partner. Recalculate or finish it first.");
        }
        Plan plan = plan(partner.getId(), asOf);
        if (plan.isEmpty()) {
            throw BusinessException.conflict("NOTHING_TO_PAY",
                    "Nothing to pay: no eligible receipts or carried adjustments up to " + asOf + ".");
        }
        if (plan.commission().signum() <= 0) {
            throw BusinessException.conflict("NOTHING_TO_PAY", "Carried adjustments ("
                    + rupees(plan.adjustmentsTotal()) + ") exceed the eligible commission, so nothing can be paid yet.");
        }
        Payout payout = new Payout();
        payout.setPayoutNumber(numbers.next(DocumentNumberService.PAYOUT));
        payout.setPartner(partner);
        payout.setStatus(PayoutStatus.DRAFT);
        payout.setAsOfDate(asOf);
        payout.setFyStart(PayoutRules.fyStart(asOf));
        payout.setCommissionTotal(plan.commission());
        payout.setTdsStatus(TdsStatus.CONFIG_INCOMPLETE.name());
        payout.setNotes(notes);
        payout.setIdempotencyKey(r.idempotencyKey());
        payout.setRequestHash(hash);
        repository.saveAndFlush(payout);
        allocate(payout, plan);
        TdsOutcome tds = applyTds(payout, partner, TdsEvent.GENERATED);
        auditService.record(AuditActions.PAYOUT_CREATED, AuditActions.ENTITY_PAYOUT, payout.getId(), null,
                AuditService.values("payoutNumber", payout.getPayoutNumber(), "partner", partner.getName(),
                        "asOfDate", asOf, "receipts", plan.receipts().size(), "adjustments", plan.adjustments().size(),
                        "commissionTotal", payout.getCommissionTotal(), "tdsStatus", payout.getTdsStatus(),
                        "tdsAmount", payout.getTdsAmount(), "netAmount", payout.getNetAmount(),
                        "tdsSection", tds.config() == null ? null : tds.config().getSectionCode()));
        return new Created(toResponse(payout, user), true);
    }

    /**
     * Recalculate a draft or pending payout ({@code PAYOUTS:WRITE}): lines whose receipt was reversed are released,
     * receipts and adjustments that became eligible up to the as-of date are added, and TDS is calculated again.
     */
    @Transactional
    public PayoutResponse recalculate(UUID id, PayoutActionRequest r, AuthenticatedUser user) {
        Payout payout = lockForAction(id, r == null ? null : r.version(), user);
        if (!payout.getStatus().isOpen()) {
            throw invalid(payout, "recalculated");
        }
        Map<String, Object> before = amounts(payout);
        List<PayoutLine> stale = lineRepository.reversedSources(payout.getId());
        release(stale, ReleaseReason.RECALCULATED);
        Plan plan = plan(payout.getPartner().getId(), payout.getAsOfDate());
        allocate(payout, plan);
        payout.setCommissionTotal(activeTotal(payout));
        applyTds(payout, payout.getPartner(), TdsEvent.RECALCULATED);
        Map<String, Object> after = amounts(payout);
        after.put("linesReleased", stale.size());
        after.put("receiptsAdded", plan.receipts().size());
        after.put("adjustmentsAdded", plan.adjustments().size());
        auditService.record(AuditActions.PAYOUT_RECALCULATED, AuditActions.ENTITY_PAYOUT, payout.getId(), before, after);
        return toResponse(payout, user);
    }

    /**
     * Replace the calculated TDS of a draft or pending payout ({@code PAYOUTS:WRITE}) with a reason. The calculation
     * history keeps both amounts and the approver sees them. Recalculating discards the adjustment.
     */
    @Transactional
    public PayoutResponse adjustTds(UUID id, TdsAdjustmentRequest r, AuthenticatedUser user) {
        Payout payout = lockForAction(id, r == null ? null : r.version(), user);
        if (!payout.getStatus().isOpen()) {
            throw invalid(payout, "changed");
        }
        String reason = PayoutRules.clean(r.reason());
        List<FieldError> errors = new ArrayList<>();
        if (r.tdsAmount() == null) {
            errors.add(new FieldError("tdsAmount", "Enter the TDS amount."));
        } else if (r.tdsAmount().signum() < 0) {
            errors.add(new FieldError("tdsAmount", "TDS can't be negative."));
        } else if (r.tdsAmount().stripTrailingZeros().scale() > 2) {
            errors.add(new FieldError("tdsAmount", "Use at most two decimal places."));
        } else if (r.tdsAmount().compareTo(payout.getCommissionTotal()) >= 0) {
            errors.add(new FieldError("tdsAmount", "TDS must be less than the commission ("
                    + rupees(payout.getCommissionTotal()) + ")."));
        }
        if (reason == null) {
            errors.add(new FieldError("reason", "Give the reason for changing the calculated TDS."));
        } else if (reason.length() > PayoutRules.MAX_REASON) {
            errors.add(new FieldError("reason", "Must be at most " + PayoutRules.MAX_REASON + " characters."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        if (!TdsStatus.CALCULATED.name().equals(payout.getTdsStatus())) {
            throw BusinessException.conflict("TDS_NOT_CALCULATED", tdsMessage(payout.getTdsStatus()));
        }
        TdsOutcome fresh = computeTds(payout.getPartner(), payout.getAsOfDate(), payout.getFyStart(), payout.getId(),
                payout.getCommissionTotal());
        if (!lineRepository.reversedSources(payout.getId()).isEmpty() || !matches(payout, fresh)) {
            throw recalculationRequired();
        }
        Map<String, Object> before = amounts(payout);
        BigDecimal amount = r.tdsAmount().setScale(2);
        payout.setTdsAmount(amount);
        payout.setTdsAdjusted(true);
        payout.setNetAmount(payout.getCommissionTotal().subtract(amount));
        repository.saveAndFlush(payout);
        recordCalculation(payout, fresh, TdsEvent.ADJUSTED, amount, reason);
        Map<String, Object> after = amounts(payout);
        after.put("computedTds", fresh.result().tds());
        after.put("reason", reason);
        auditService.record(AuditActions.PAYOUT_TDS_ADJUSTED, AuditActions.ENTITY_PAYOUT, payout.getId(), before, after);
        return toResponse(payout, user);
    }

    // ------------------------------------------------------------------------------------------- workflow

    /** Submit a draft for approval ({@code PAYOUTS:WRITE}). */
    @Transactional
    public PayoutResponse submit(UUID id, PayoutActionRequest r, AuthenticatedUser user) {
        Payout payout = lockForAction(id, r == null ? null : r.version(), user);
        if (payout.getStatus() != PayoutStatus.DRAFT) {
            throw invalid(payout, "submitted");
        }
        if (!lineRepository.reversedSources(payout.getId()).isEmpty()) {
            throw recalculationRequired();
        }
        if (payout.getCommissionTotal().signum() <= 0) {
            throw BusinessException.conflict("NOTHING_TO_PAY", "This payout has nothing to pay. Cancel it instead.");
        }
        payout.setStatus(PayoutStatus.PENDING_APPROVAL);
        payout.setSubmittedAt(clock.instant());
        payout.setSubmittedBy(user.id());
        repository.saveAndFlush(payout);
        auditService.record(AuditActions.PAYOUT_SUBMITTED, AuditActions.ENTITY_PAYOUT, payout.getId(),
                AuditService.values("status", PayoutStatus.DRAFT), AuditService.values("status", payout.getStatus()));
        return toResponse(payout, user);
    }

    /**
     * Approve a pending payout ({@code PAYOUTS:APPROVE}). The approver can't be its creator or submitter. TDS must be
     * calculated with a confirmed configuration and still match a fresh calculation, the net amount must be above zero
     * and the partner must have PAN and bank account on file (BR-P7). The amounts are frozen from here on.
     */
    @Transactional
    public PayoutResponse approve(UUID id, PayoutActionRequest r, AuthenticatedUser user) {
        Payout payout = lockForAction(id, r == null ? null : r.version(), user);
        if (payout.getStatus() != PayoutStatus.PENDING_APPROVAL) {
            throw invalid(payout, "approved");
        }
        requireSecondPerson(payout, user, "approve");
        if (Objects.equals(tdsAdjustedBy(payout,
                calculationRepository.findByPayoutIdOrderByCreatedAtDescIdDesc(payout.getId())), user.id())) {
            throw BusinessException.conflict("SELF_APPROVAL", TDS_SELF_APPROVAL);
        }
        String note = PayoutRules.clean(r.note());
        if (note != null && note.length() > PayoutRules.MAX_NOTES) {
            throw ValidationException.of("note", "Must be at most " + PayoutRules.MAX_NOTES + " characters.");
        }
        Partner partner = payout.getPartner();
        TdsOutcome fresh = computeTds(partner, payout.getAsOfDate(), payout.getFyStart(), payout.getId(),
                payout.getCommissionTotal());
        if (!lineRepository.reversedSources(payout.getId()).isEmpty() || !matches(payout, fresh)) {
            throw recalculationRequired();
        }
        if (!TdsStatus.CALCULATED.name().equals(payout.getTdsStatus())) {
            throw BusinessException.conflict("TDS_NOT_READY", tdsMessage(payout.getTdsStatus()));
        }
        if (payout.getNetAmount() == null || payout.getNetAmount().signum() <= 0) {
            throw BusinessException.conflict("NOTHING_TO_PAY", "The net amount after TDS must be above zero.");
        }
        if (partner.getPan() == null || partner.getBankAccount() == null) {
            throw BusinessException.conflict("PARTNER_NOT_PAYOUT_READY",
                    "Add the partner's PAN and bank account before approving a payout (BR-P7).");
        }
        payout.setStatus(PayoutStatus.APPROVED);
        payout.setApprovedAt(clock.instant());
        payout.setApprovedBy(user.id());
        payout.setApprovalNote(note);
        repository.saveAndFlush(payout);
        recordCalculation(payout, fresh, TdsEvent.APPROVED, payout.getTdsAmount(), null);
        Map<String, Object> after = amounts(payout);
        after.put("status", payout.getStatus());
        after.put("note", note);
        auditService.record(AuditActions.PAYOUT_APPROVED, AuditActions.ENTITY_PAYOUT, payout.getId(),
                AuditService.values("status", PayoutStatus.PENDING_APPROVAL), after);
        return toResponse(payout, user);
    }

    /** Reject a pending payout with a reason ({@code PAYOUTS:APPROVE}); its receipts are free for a later payout. */
    @Transactional
    public PayoutResponse reject(UUID id, PayoutActionRequest r, AuthenticatedUser user) {
        Payout payout = lockForAction(id, r == null ? null : r.version(), user);
        if (payout.getStatus() != PayoutStatus.PENDING_APPROVAL) {
            throw invalid(payout, "rejected");
        }
        requireSecondPerson(payout, user, "reject");
        return close(payout, PayoutStatus.REJECTED, ReleaseReason.REJECTED, requireReason(r),
                AuditActions.PAYOUT_REJECTED, user);
    }

    /**
     * Cancel a payout with a reason: a draft or pending one ({@code PAYOUTS:WRITE}) or an approved one not yet in
     * processing ({@code PAYOUTS:APPROVE}). Its receipts and adjustments are free for a later payout.
     */
    @Transactional
    public PayoutResponse cancel(UUID id, PayoutActionRequest r, AuthenticatedUser user) {
        Payout payout = lockForAction(id, r == null ? null : r.version(), user);
        if (payout.getStatus().isOpen()) {
            if (!canWrite(user)) {
                throw new org.springframework.security.access.AccessDeniedException("Payouts write required");
            }
        } else if (payout.getStatus() == PayoutStatus.APPROVED) {
            if (!canApprove(user)) {
                throw new org.springframework.security.access.AccessDeniedException("Payouts approve required");
            }
        } else {
            throw invalid(payout, "cancelled");
        }
        return close(payout, PayoutStatus.CANCELLED, ReleaseReason.CANCELLED, requireReason(r),
                AuditActions.PAYOUT_CANCELLED, user);
    }

    /**
     * Record that Finance started the bank transfer of an approved payout ({@code PAYOUTS:MARK_PAYOUT_PAID}). No money
     * moves in this system. Refused while a receipt of the payout has been reversed: cancel it instead.
     */
    @Transactional
    public PayoutResponse startProcessing(UUID id, PayoutActionRequest r, AuthenticatedUser user) {
        Payout payout = lockForAction(id, r == null ? null : r.version(), user);
        if (payout.getStatus() != PayoutStatus.APPROVED) {
            throw invalid(payout, "moved to processing");
        }
        List<PayoutLine> reversed = lineRepository.reversedSources(payout.getId());
        if (!reversed.isEmpty()) {
            throw BusinessException.conflict("PAYOUT_BLOCKED", "Payments " + paymentNumbers(reversed)
                    + " were reversed after approval. Cancel this payout and generate a new one.");
        }
        String note = PayoutRules.clean(r.note());
        if (note != null && note.length() > PayoutRules.MAX_NOTES) {
            throw ValidationException.of("note", "Must be at most " + PayoutRules.MAX_NOTES + " characters.");
        }
        String account = payout.getPartner().getBankAccount();
        if (account == null) {
            throw BusinessException.conflict("PARTNER_NOT_PAYOUT_READY", "The partner has no bank account on file.");
        }
        String digits = account.replaceAll("[^0-9A-Za-z]", "");
        payout.setStatus(PayoutStatus.PROCESSING);
        payout.setProcessingAt(clock.instant());
        payout.setProcessingBy(user.id());
        payout.setBankAccountLast4(digits.length() <= 4 ? digits : digits.substring(digits.length() - 4));
        repository.saveAndFlush(payout);
        auditService.record(AuditActions.PAYOUT_PROCESSING, AuditActions.ENTITY_PAYOUT, payout.getId(),
                AuditService.values("status", PayoutStatus.APPROVED),
                AuditService.values("status", payout.getStatus(), "bankAccount", PartnerMapper.mask(account),
                        "netAmount", payout.getNetAmount(), "note", note));
        return toResponse(payout, user);
    }

    /**
     * Confirm that a payout under processing was paid ({@code PAYOUTS:MARK_PAYOUT_PAID}), with the bank reference and
     * the date paid (in the payout's financial year, so TDS is deposited for the year it was calculated in). If a
     * receipt was reversed after approval the caller must acknowledge it; its commission then becomes a negative
     * adjustment carried into the partner's next payout.
     */
    @Transactional
    public PayoutResponse markPaid(UUID id, PayoutMarkPaidRequest r, AuthenticatedUser user) {
        Payout payout = lockForAction(id, r == null ? null : r.version(), user);
        if (payout.getStatus() != PayoutStatus.PROCESSING) {
            throw invalid(payout, "marked paid");
        }
        String reference = PaymentRules.normalizeReference(r.reference());
        LocalDate today = LocalDate.now(clock);
        List<FieldError> errors = new ArrayList<>();
        if (r.paidDate() == null) {
            errors.add(new FieldError("paidDate", "Enter the date the transfer was made."));
        } else if (r.paidDate().isAfter(today)) {
            errors.add(new FieldError("paidDate", "The paid date can't be in the future."));
        } else if (r.paidDate().isBefore(LocalDate.ofInstant(payout.getApprovedAt(), clock.getZone()))) {
            errors.add(new FieldError("paidDate", "The paid date can't be before the approval date."));
        }
        if (reference == null) {
            errors.add(new FieldError("reference", "Enter the bank reference (UTR) of the transfer."));
        } else if (reference.length() > PayoutRules.MAX_REFERENCE) {
            errors.add(new FieldError("reference", "Must be at most " + PayoutRules.MAX_REFERENCE + " characters."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        if (!PayoutRules.fyStart(r.paidDate()).equals(payout.getFyStart())) {
            throw BusinessException.conflict("PAYOUT_FY_MISMATCH", "This payout's TDS was calculated for financial year "
                    + PayoutRules.fyLabel(payout.getFyStart()) + ", but the paid date falls in "
                    + PayoutRules.fyLabel(PayoutRules.fyStart(r.paidDate())) + ". Mark it failed and generate a new "
                    + "payout for the new year.");
        }
        List<PayoutLine> reversed = lineRepository.reversedSources(payout.getId());
        if (!reversed.isEmpty() && !Boolean.TRUE.equals(r.acknowledgeReversedSources())) {
            throw BusinessException.conflict("PAYOUT_BLOCKED", "Payments " + paymentNumbers(reversed) + " were "
                    + "reversed after approval. If the transfer was already made, confirm it to carry their commission "
                    + "forward as negative adjustments; otherwise mark the payout failed.");
        }
        payout.setStatus(PayoutStatus.PAID);
        payout.setPaidDate(r.paidDate());
        payout.setPaymentReference(reference);
        payout.setPaidAt(clock.instant());
        payout.setPaidBy(user.id());
        repository.saveAndFlush(payout);
        List<String> created = new ArrayList<>();
        for (PayoutLine line : reversed) {
            Payment reversal = paymentRepository.findByReversedPaymentId(line.getPayment().getId()).orElseThrow();
            PartnerAdjustment adjustment = adjustments.reversalAdjustment(payout, line, reversal, "Payment "
                    + line.getPayment().getPaymentNumber() + " was reversed before payout " + payout.getPayoutNumber()
                    + " was paid; the transfer was confirmed anyway.");
            if (adjustment != null) {
                created.add(adjustment.getAdjustmentNumber());
            }
        }
        auditService.record(AuditActions.PAYOUT_PAID, AuditActions.ENTITY_PAYOUT, payout.getId(),
                AuditService.values("status", PayoutStatus.PROCESSING),
                AuditService.values("status", payout.getStatus(), "paidDate", payout.getPaidDate(),
                        "reference", PaymentRules.maskReference(reference), "netAmount", payout.getNetAmount(),
                        "tdsAmount", payout.getTdsAmount(),
                        "reversalAdjustments", created.isEmpty() ? null : String.join(", ", created)));
        return toResponse(payout, user);
    }

    /** Record that the transfer of a payout under processing failed ({@code PAYOUTS:MARK_PAYOUT_PAID}), with a reason. */
    @Transactional
    public PayoutResponse fail(UUID id, PayoutActionRequest r, AuthenticatedUser user) {
        Payout payout = lockForAction(id, r == null ? null : r.version(), user);
        if (payout.getStatus() != PayoutStatus.PROCESSING) {
            throw invalid(payout, "marked failed");
        }
        return close(payout, PayoutStatus.FAILED, ReleaseReason.FAILED, requireReason(r), AuditActions.PAYOUT_FAILED,
                user);
    }

    // ------------------------------------------------------------------------------------------- permissions

    public static boolean canWrite(AuthenticatedUser user) {
        return user.permissions().canWrite(ModuleCode.PAYOUTS.name());
    }

    public static boolean canApprove(AuthenticatedUser user) {
        return user.permissions().canPerform(ModuleCode.PAYOUTS.name(), "APPROVE");
    }

    public static boolean canMarkPaid(AuthenticatedUser user) {
        return user.permissions().canPerform(ModuleCode.PAYOUTS.name(), MARK_PAID);
    }

    // ------------------------------------------------------------------------------------------- calculation

    private Plan plan(UUID partnerId, LocalDate asOf) {
        List<PayoutQueries.EligibleReceipt> receipts = queries.eligibleReceipts(partnerId, asOf).stream()
                .filter(e -> PayoutRules.commission(e.netAmount(), e.feePercent()).signum() > 0).toList();
        List<PartnerAdjustment> carried = adjustmentRepository.carried(partnerId);
        BigDecimal receiptsTotal = receipts.stream().map(e -> PayoutRules.commission(e.netAmount(), e.feePercent()))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal adjustmentsTotal = carried.stream().map(PartnerAdjustment::getAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new Plan(receipts, carried, receiptsTotal, adjustmentsTotal);
    }

    private void allocate(Payout payout, Plan plan) {
        List<PayoutLine> lines = new ArrayList<>();
        for (PayoutQueries.EligibleReceipt e : plan.receipts()) {
            PayoutLine line = new PayoutLine();
            line.setPayout(payout);
            line.setLineType(LineType.COMMISSION);
            line.setPayment(entityManager.getReference(Payment.class, e.paymentId()));
            line.setInvoice(entityManager.getReference(Invoice.class, e.invoiceId()));
            line.setPaymentDate(e.paymentDate());
            line.setBaseAmount(e.netAmount());
            line.setCommissionPercent(e.feePercent());
            line.setAmount(PayoutRules.commission(e.netAmount(), e.feePercent()));
            line.setCreatedAt(clock.instant());
            lines.add(line);
        }
        for (PartnerAdjustment a : plan.adjustments()) {
            PayoutLine line = new PayoutLine();
            line.setPayout(payout);
            line.setLineType(LineType.ADJUSTMENT);
            line.setAdjustment(a);
            line.setAmount(a.getAmount());
            line.setCreatedAt(clock.instant());
            lines.add(line);
        }
        lineRepository.saveAllAndFlush(lines);
    }

    /** Lines are released before the payout's status changes ({@code payout_records_guard} checks the old status). */
    private void release(List<PayoutLine> lines, ReleaseReason reason) {
        if (lines.isEmpty()) {
            return;
        }
        lines.forEach(l -> {
            l.setReleasedAt(clock.instant());
            l.setReleaseReason(reason);
        });
        lineRepository.saveAllAndFlush(lines);
    }

    private BigDecimal activeTotal(Payout payout) {
        return lineRepository.active(payout.getId()).stream().map(PayoutLine::getAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private TdsOutcome computeTds(Partner partner, LocalDate asOf, LocalDate fyStart, UUID payoutId,
                                  BigDecimal commission) {
        TdsConfiguration config = tdsRepository.inForce(asOf).orElse(null);
        PayoutQueries.PriorFy prior = queries.priorFy(partner.getId(), fyStart, payoutId);
        PayoutRules.TdsRule rule = config == null ? null : new PayoutRules.TdsRule(config.getSectionCode(),
                config.getRatePercent(), config.getRateWithoutPanPercent(), config.getFyThreshold());
        boolean panVerified = partner.isPanVerified();
        TdsResult result = PayoutRules.tds(rule, new PayoutRules.TdsInput(commission, prior.commission(),
                prior.taxedBase(), panVerified));
        return new TdsOutcome(config, prior, panVerified, result);
    }

    private TdsOutcome applyTds(Payout payout, Partner partner, TdsEvent event) {
        TdsOutcome o = computeTds(partner, payout.getAsOfDate(), payout.getFyStart(), payout.getId(),
                payout.getCommissionTotal());
        payout.setTdsStatus(o.result().status().name());
        payout.setTdsConfiguration(o.config());
        payout.setTdsSection(o.config() == null ? null : o.config().getSectionCode());
        payout.setTdsRate(o.result().ratePercent());
        payout.setTdsBase(o.result().tdsBase());
        payout.setTdsAmount(o.result().tds());
        payout.setTdsAdjusted(false);
        payout.setNetAmount(o.result().tds() == null ? null : payout.getCommissionTotal().subtract(o.result().tds()));
        repository.saveAndFlush(payout);
        recordCalculation(payout, o, event, o.result().tds(), null);
        return o;
    }

    private void recordCalculation(Payout payout, TdsOutcome o, TdsEvent event, BigDecimal tdsAmount, String reason) {
        PayoutTdsCalculation c = new PayoutTdsCalculation();
        c.setPayout(payout);
        c.setEvent(event);
        c.setStatus(o.result().status().name());
        c.setTdsConfiguration(o.config());
        c.setSectionCode(o.config() == null ? null : o.config().getSectionCode());
        c.setRatePercent(o.result().ratePercent());
        c.setFyThreshold(o.config() == null ? null : o.config().getFyThreshold());
        c.setFyStart(payout.getFyStart());
        c.setPanVerified(o.panVerified());
        c.setPriorFyCommission(PayoutRules.r2(o.prior().commission()));
        c.setPriorFyTdsBase(PayoutRules.r2(o.prior().taxedBase()));
        c.setCommissionTotal(payout.getCommissionTotal());
        c.setTdsBase(o.result().tdsBase());
        c.setComputedTds(o.result().tds());
        c.setTdsAmount(tdsAmount);
        c.setAdjustmentReason(reason);
        calculationRepository.saveAndFlush(c);
    }

    /** True when a fresh calculation gives what the payout holds (configuration, rate, base and computed TDS). */
    private static boolean matches(Payout payout, TdsOutcome fresh) {
        TdsResult r = fresh.result();
        UUID configId = fresh.config() == null ? null : fresh.config().getId();
        UUID heldConfig = payout.getTdsConfiguration() == null ? null : payout.getTdsConfiguration().getId();
        return r.status().name().equals(payout.getTdsStatus()) && Objects.equals(configId, heldConfig)
                && same(r.ratePercent(), payout.getTdsRate()) && same(r.tdsBase(), payout.getTdsBase())
                && (payout.isTdsAdjusted() || same(r.tds(), payout.getTdsAmount()));
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    // ------------------------------------------------------------------------------------------- workflow helpers

    private Payout lockForAction(UUID id, Long version, AuthenticatedUser user) {
        requireScope(user);
        Payout payout = repository.lock(id).orElseThrow(() -> new ResourceNotFoundException("Payout", id));
        if (payout.isLegacy()) {
            throw BusinessException.conflict("PAYOUT_LEGACY", "Payouts carried over from before Phase 11 can't change.");
        }
        if (version == null) {
            throw ValidationException.of("version", "Version is required.");
        }
        if (version != payout.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Payout.class, payout.getId());
        }
        return payout;
    }

    private PayoutResponse close(Payout payout, PayoutStatus status, ReleaseReason reason, String text, String action,
                                 AuthenticatedUser user) {
        PayoutStatus before = payout.getStatus();
        List<PayoutLine> active = lineRepository.active(payout.getId());
        release(active, reason);
        payout.setStatus(status);
        payout.setClosedAt(clock.instant());
        payout.setClosedBy(user.id());
        payout.setStatusReason(text);
        repository.saveAndFlush(payout);
        auditService.record(action, AuditActions.ENTITY_PAYOUT, payout.getId(), AuditService.values("status", before),
                AuditService.values("status", status, "reason", text, "linesReleased", active.size()));
        return toResponse(payout, user);
    }

    private static String requireReason(PayoutActionRequest r) {
        String reason = r == null ? null : PayoutRules.clean(r.reason());
        if (reason == null) {
            throw ValidationException.of("reason", "Give the reason.");
        }
        if (reason.length() > PayoutRules.MAX_REASON) {
            throw ValidationException.of("reason", "Must be at most " + PayoutRules.MAX_REASON + " characters.");
        }
        return reason;
    }

    private static final String TDS_SELF_APPROVAL = "You changed the TDS of this payout by hand, so someone else must "
            + "approve it.";

    /** Who entered the manual TDS change in force, or null when the TDS is as calculated. */
    private static UUID tdsAdjustedBy(Payout payout, List<PayoutTdsCalculation> newestFirst) {
        if (!payout.isTdsAdjusted()) {
            return null;
        }
        return newestFirst.stream().filter(c -> c.getEvent() == TdsEvent.ADJUSTED).findFirst()
                .map(PayoutTdsCalculation::getCreatedBy).orElse(null);
    }

    private static void requireSecondPerson(Payout payout, AuthenticatedUser user, String verb) {
        if (Objects.equals(payout.getCreatedBy(), user.id()) || Objects.equals(payout.getSubmittedBy(), user.id())) {
            throw BusinessException.conflict("SELF_APPROVAL",
                    "You created or submitted this payout, so someone else must " + verb + " it.");
        }
    }

    private static BusinessException invalid(Payout payout, String verb) {
        return BusinessException.conflict("INVALID_TRANSITION",
                "A payout that is " + payout.getStatus().getLabel().toLowerCase(Locale.ROOT) + " can't be " + verb + ".");
    }

    private static BusinessException recalculationRequired() {
        return BusinessException.conflict("PAYOUT_RECALCULATION_REQUIRED", "Receipts or TDS inputs changed since this "
                + "payout was calculated. Recalculate it and review the amounts again.");
    }

    private static String tdsMessage(String status) {
        if (TdsStatus.PAN_NOT_VERIFIED.name().equals(status)) {
            return PAN_NOT_VERIFIED;
        }
        return TDS_INCOMPLETE;
    }

    private Created replay(UUID key, String hash, AuthenticatedUser user) {
        Payout existing = repository.findByIdempotencyKey(key).orElse(null);
        if (existing == null) {
            return null;
        }
        if (!hash.equals(existing.getRequestHash()) || !Objects.equals(existing.getCreatedBy(), user.id())) {
            throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "This form was already submitted with different details. Reload it and try again.");
        }
        return new Created(toResponse(existing, user), false);
    }

    private LocalDate asOfDate(LocalDate requested) {
        LocalDate today = LocalDate.now(clock);
        if (requested == null) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw ValidationException.of("asOfDate", "The as-of date can't be in the future.");
        }
        return requested;
    }

    private void requireScope(AuthenticatedUser user) {
        if (!dataScopeService.resolve(user, ModuleCode.PAYOUTS).unrestricted()) {
            throw BusinessException.conflict("PAYOUTS_SCOPE",
                    "Payouts need access to all records. Ask an administrator to set the Payouts scope to All.");
        }
    }

    private Payout load(UUID id, AuthenticatedUser user) {
        if (!dataScopeService.resolve(user, ModuleCode.PAYOUTS).unrestricted()) {
            throw new ResourceNotFoundException("Payout", id);
        }
        return repository.findWithPartnerById(id).orElseThrow(() -> new ResourceNotFoundException("Payout", id));
    }

    private static List<String> approvalWarnings(Partner partner, TdsResult tds, BigDecimal commission) {
        List<String> warnings = new ArrayList<>();
        if (tds.status() == TdsStatus.CONFIG_INCOMPLETE) {
            warnings.add(TDS_INCOMPLETE);
        } else if (tds.status() == TdsStatus.PAN_NOT_VERIFIED) {
            warnings.add(PAN_NOT_VERIFIED);
        } else if (tds.tds() != null && commission.signum() > 0 && tds.tds().compareTo(commission) >= 0) {
            warnings.add("TDS (" + rupees(tds.tds()) + ") is not less than the commission. Review it with Finance "
                    + "and adjust it with a reason before approval.");
        }
        if (partner.getPan() == null || partner.getBankAccount() == null) {
            warnings.add("Add the partner's PAN and bank account before approval (BR-P7).");
        }
        return warnings;
    }

    // ------------------------------------------------------------------------------------------- responses

    private Set<UUID> blocked(List<Payout> payouts) {
        List<UUID> ids = payouts.stream().filter(p -> UNPAID.contains(p.getStatus())).map(Payout::getId).toList();
        return ids.isEmpty() ? Set.of() : new HashSet<>(lineRepository.withReversedSources(ids));
    }

    private static PayoutSummary summary(Payout p, Set<UUID> blocked) {
        return new PayoutSummary(p.getId(), p.getPayoutNumber(),
                new PartnerRef(p.getPartner().getId(), p.getPartner().getName()), p.getStatus().name(),
                p.getStatus().getLabel(), p.isLegacy(), p.getAsOfDate(), PayoutRules.fyLabel(p.getFyStart()),
                p.getCommissionTotal(), p.getTdsStatus(), p.getTdsAmount(), p.getNetAmount(), p.getPaidDate(),
                blocked.contains(p.getId()), p.getCreatedAt());
    }

    private PayoutResponse toResponse(Payout p, AuthenticatedUser user) {
        Partner partner = p.getPartner();
        List<PayoutLine> all = lineRepository.findByPayoutIdOrderByCreatedAtAscIdAsc(p.getId());
        List<UUID> paymentIds = all.stream().filter(l -> l.getPayment() != null).map(l -> l.getPayment().getId())
                .toList();
        Set<UUID> reversedPayments = paymentIds.isEmpty() ? Set.of()
                : new HashSet<>(paymentRepository.reversedAmong(paymentIds));
        List<PayoutLineView> active = new ArrayList<>();
        List<PayoutLineView> released = new ArrayList<>();
        List<String> reversedSources = new ArrayList<>();
        BigDecimal receiptsTotal = BigDecimal.ZERO.setScale(2);
        BigDecimal adjustmentsTotal = BigDecimal.ZERO.setScale(2);
        for (PayoutLine l : all) {
            boolean sourceReversed = l.getPayment() != null && reversedPayments.contains(l.getPayment().getId());
            PayoutLineView view = l.getLineType() == LineType.ADJUSTMENT
                    ? adjustmentView(l.getId(), l.getAdjustment(), l.getReleasedAt(), l.getReleaseReason())
                    : new PayoutLineView(l.getId(), l.getLineType().name(), l.getPayment().getId(),
                    l.getPayment().getPaymentNumber(), l.getInvoice().getId(), l.getInvoice().getInvoiceNumber(),
                    l.getPaymentDate(), l.getBaseAmount(), l.getCommissionPercent(), l.getAmount(), null, null, null,
                    null, sourceReversed, l.getReleasedAt(),
                    l.getReleaseReason() == null ? null : l.getReleaseReason().name());
            // Lines released when the payout closed still make up its (frozen) totals; only lines a recalculation
            // took out are excluded, so receipts + adjustments always equal the stored commission.
            if (l.getReleasedAt() == null || l.getReleaseReason() != ReleaseReason.RECALCULATED) {
                if (l.getLineType() == LineType.ADJUSTMENT) {
                    adjustmentsTotal = adjustmentsTotal.add(l.getAmount());
                } else {
                    receiptsTotal = receiptsTotal.add(l.getAmount());
                }
            }
            if (l.getReleasedAt() == null) {
                active.add(view);
                if (sourceReversed) {
                    reversedSources.add(l.getPayment().getPaymentNumber());
                }
            } else {
                released.add(view);
            }
        }
        List<PayoutTdsCalculation> calculations = calculationRepository.findByPayoutIdOrderByCreatedAtDescIdDesc(p.getId());
        Set<UUID> actors = new HashSet<>(Arrays.asList(p.getCreatedBy(), p.getSubmittedBy(), p.getApprovedBy(),
                p.getProcessingBy(), p.getPaidBy(), p.getClosedBy()));
        calculations.forEach(c -> actors.add(c.getCreatedBy()));
        Map<UUID, String> names = userNames.of(actors);

        PayoutTdsCalculation latest = calculations.isEmpty() ? null : calculations.get(0);
        TdsConfiguration config = p.getTdsConfiguration();
        PayoutTds tds = new PayoutTds(p.getTdsStatus(),
                TdsStatus.CALCULATED.name().equals(p.getTdsStatus()) || p.isLegacy() ? null : tdsMessage(p.getTdsStatus()),
                config == null ? null : config.getId(), p.getTdsSection(), p.getTdsRate(),
                config == null ? null : config.getFyThreshold(),
                latest == null ? partner.isPanVerified() : latest.isPanVerified(),
                latest == null ? null : latest.getPriorFyCommission(), latest == null ? null : latest.getPriorFyTdsBase(),
                p.getTdsBase(), latest == null ? p.getTdsAmount() : latest.getComputedTds(), p.getTdsAmount(),
                p.isTdsAdjusted());

        boolean sensitive = PartnerService.canSeeSensitive(user);
        PayoutResponse.PartnerInfo partnerInfo = new PayoutResponse.PartnerInfo(partner.getId(), partner.getName(),
                partner.getPartnerCode(), partner.getPan() != null,
                sensitive ? partner.getPan() : PartnerMapper.mask(partner.getPan()), partner.isPanVerified(),
                partner.getBankName(), sensitive ? partner.getBankAccount() : PartnerMapper.mask(partner.getBankAccount()),
                partner.getIfsc(), partner.getBankAccount() != null, sensitive);

        PayoutResponse.Workflow workflow = new PayoutResponse.Workflow(p.getCreatedAt(), names.get(p.getCreatedBy()),
                p.getSubmittedAt(), names.get(p.getSubmittedBy()), p.getApprovedAt(), names.get(p.getApprovedBy()),
                p.getApprovalNote(), p.getProcessingAt(), names.get(p.getProcessingBy()),
                p.getBankAccountLast4() == null ? null : "••" + p.getBankAccountLast4(), p.getPaidDate(),
                p.getPaymentReference(), p.getPaidAt(), names.get(p.getPaidBy()), p.getClosedAt(),
                names.get(p.getClosedBy()), p.getStatusReason());

        PayoutStatus s = p.getStatus();
        boolean legacy = p.isLegacy();
        boolean secondPerson = !Objects.equals(p.getCreatedBy(), user.id())
                && !Objects.equals(p.getSubmittedBy(), user.id());
        // A manual TDS change needs a second person too: its author may reject the payout but not approve it.
        boolean tdsBySelf = Objects.equals(tdsAdjustedBy(p, calculations), user.id());
        boolean blocked = !reversedSources.isEmpty();
        PayoutResponse.Actions actions = new PayoutResponse.Actions(
                !legacy && s.isOpen() && canWrite(user),
                !legacy && s.isOpen() && canWrite(user) && TdsStatus.CALCULATED.name().equals(p.getTdsStatus()),
                !legacy && s == PayoutStatus.DRAFT && canWrite(user),
                !legacy && s == PayoutStatus.PENDING_APPROVAL && canApprove(user) && secondPerson && !tdsBySelf,
                !legacy && s == PayoutStatus.PENDING_APPROVAL && canApprove(user) && secondPerson,
                !legacy && ((s.isOpen() && canWrite(user)) || (s == PayoutStatus.APPROVED && canApprove(user))),
                !legacy && s == PayoutStatus.APPROVED && canMarkPaid(user) && !blocked,
                !legacy && s == PayoutStatus.PROCESSING && canMarkPaid(user),
                !legacy && s == PayoutStatus.PROCESSING && canMarkPaid(user));

        List<String> blockers = new ArrayList<>();
        if (!legacy) {
            String reversedPhrase = reversedSources.size() == 1
                    ? "Payment " + reversedSources.get(0) + " was reversed"
                    : "Payments " + String.join(", ", reversedSources) + " were reversed";
            if (blocked && s.isOpen()) {
                blockers.add(reversedPhrase + ". Recalculate the payout to remove "
                        + (reversedSources.size() == 1 ? "it." : "them."));
            } else if (blocked && (s == PayoutStatus.APPROVED || s == PayoutStatus.PROCESSING)) {
                blockers.add(reversedPhrase + " after approval. "
                        + (s == PayoutStatus.APPROVED ? "Cancel this payout and generate a new one."
                        : "Mark it failed, or if the transfer was made, mark it paid and confirm the reversal."));
            }
            if (s.isOpen()) {
                TdsOutcome fresh = computeTds(partner, p.getAsOfDate(), p.getFyStart(), p.getId(), p.getCommissionTotal());
                if (!blocked && !matches(p, fresh)) {
                    blockers.add("TDS inputs changed since the last calculation. Recalculate before approval.");
                }
                blockers.addAll(approvalWarnings(partner,
                        new TdsResult(TdsStatus.valueOf(p.getTdsStatus()), p.getTdsRate(), p.getTdsBase(),
                                p.getTdsAmount()), p.getCommissionTotal()));
                if (p.getCommissionTotal().signum() <= 0) {
                    blockers.add("This payout has nothing to pay. Cancel it.");
                }
                if (s == PayoutStatus.PENDING_APPROVAL && canApprove(user) && !secondPerson) {
                    blockers.add("You created or submitted this payout, so someone else must approve it.");
                } else if (s == PayoutStatus.PENDING_APPROVAL && canApprove(user) && tdsBySelf) {
                    blockers.add(TDS_SELF_APPROVAL);
                }
            }
        }
        return new PayoutResponse(p.getId(), p.getPayoutNumber(), s.name(), s.getLabel(), legacy, partnerInfo,
                p.getAsOfDate(), p.getFyStart(), PayoutRules.fyLabel(p.getFyStart()), receiptsTotal, adjustmentsTotal,
                p.getCommissionTotal(), tds, p.getNetAmount(), p.getNotes(), active, released,
                calculations.stream().map(c -> new PayoutResponse.Calculation(c.getId(), c.getEvent().name(),
                        c.getStatus(), c.getSectionCode(), c.getRatePercent(), c.getFyThreshold(),
                        PayoutRules.fyLabel(c.getFyStart()), c.isPanVerified(), c.getPriorFyCommission(),
                        c.getPriorFyTdsBase(), c.getCommissionTotal(), c.getTdsBase(), c.getComputedTds(),
                        c.getTdsAmount(), c.getAdjustmentReason(), c.getCreatedAt(),
                        names.get(c.getCreatedBy()))).toList(),
                workflow, reversedSources, blockers, actions, p.getVersion());
    }

    private static PayoutLineView adjustmentView(UUID lineId, PartnerAdjustment a, java.time.Instant releasedAt,
                                                 ReleaseReason reason) {
        return new PayoutLineView(lineId, LineType.ADJUSTMENT.name(), null, null, null, null, null, null, null,
                a.getAmount(), a.getId(), a.getAdjustmentNumber(), a.getKind().getLabel(), a.getReason(), false,
                releasedAt, reason == null ? null : reason.name());
    }

    private static Map<String, Object> amounts(Payout p) {
        return AuditService.values("commissionTotal", p.getCommissionTotal(), "tdsStatus", p.getTdsStatus(),
                "tdsAmount", p.getTdsAmount(), "netAmount", p.getNetAmount());
    }

    private static String paymentNumbers(List<PayoutLine> lines) {
        return lines.stream().map(l -> l.getPayment().getPaymentNumber()).collect(Collectors.joining(", "));
    }

    private static BigDecimal sum(List<PayoutQueries.Balance> rows,
                                  java.util.function.Function<PayoutQueries.Balance, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static PayoutTds tdsView(TdsOutcome o, BigDecimal amount, boolean adjusted, boolean panVerified) {
        TdsResult r = o.result();
        return new PayoutTds(r.status().name(), r.status() == TdsStatus.CALCULATED ? null : tdsMessage(r.status().name()),
                o.config() == null ? null : o.config().getId(), o.config() == null ? null : o.config().getSectionCode(),
                r.ratePercent(), o.config() == null ? null : o.config().getFyThreshold(), panVerified,
                PayoutRules.r2(o.prior().commission()), PayoutRules.r2(o.prior().taxedBase()), r.tdsBase(), r.tds(),
                amount, adjusted);
    }

    static String rupees(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return (amount.signum() < 0 ? "−₹" : "₹") + format.format(amount.abs());
    }

    private List<PayoutHistoryEntry.Change> changes(AuditLog entry) {
        JsonNode before = parse(entry.getOldValues());
        JsonNode after = parse(entry.getNewValues());
        Set<String> fields = new LinkedHashSet<>();
        before.propertyNames().forEach(fields::add);
        after.propertyNames().forEach(fields::add);
        return fields.stream().map(field -> new PayoutHistoryEntry.Change(field,
                before.path(field).isMissingNode() ? null : before.get(field),
                after.path(field).isMissingNode() ? null : after.get(field))).toList();
    }

    private JsonNode parse(String json) {
        return json == null ? objectMapper.createObjectNode() : objectMapper.readTree(json);
    }

    // ------------------------------------------------------------------------------------------- search

    private Specification<Payout> spec(Filters filters, AuthenticatedUser user) {
        return dataScopeService.resolve(user, ModuleCode.PAYOUTS).<Payout>toSpecification().and(matching(filters));
    }

    private static Specification<Payout> matching(Filters f) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Payout, Partner> partner = root.join("partner", JoinType.INNER);
            if (f.query() != null && !f.query().isBlank()) {
                String like = PayoutRules.like(f.query());
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("payoutNumber"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("paymentReference"), "")), like, '\\'),
                        cb.like(cb.lower(partner.get("name")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(partner.get("partnerCode"), "")), like, '\\')));
            }
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.partnerId() != null) {
                predicates.add(cb.equal(partner.get("id"), f.partnerId()));
            }
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("asOfDate"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("asOfDate"), f.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
