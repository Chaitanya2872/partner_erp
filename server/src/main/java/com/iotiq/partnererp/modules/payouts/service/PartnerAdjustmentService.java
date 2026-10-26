package com.iotiq.partnererp.modules.payouts.service;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.auth.service.DataScopeService;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import com.iotiq.partnererp.modules.partners.repository.PartnerRepository;
import com.iotiq.partnererp.modules.payments.entity.Payment;
import com.iotiq.partnererp.modules.payouts.dto.AdjustmentDecisionRequest;
import com.iotiq.partnererp.modules.payouts.dto.AdjustmentRequest;
import com.iotiq.partnererp.modules.payouts.dto.AdjustmentResponse;
import com.iotiq.partnererp.modules.payouts.entity.PartnerAdjustment;
import com.iotiq.partnererp.modules.payouts.entity.Payout;
import com.iotiq.partnererp.modules.payouts.entity.PayoutEnums.AdjustmentKind;
import com.iotiq.partnererp.modules.payouts.entity.PayoutEnums.AdjustmentStatus;
import com.iotiq.partnererp.modules.payouts.entity.PayoutLine;
import com.iotiq.partnererp.modules.payouts.entity.PayoutStatus;
import com.iotiq.partnererp.modules.payouts.repository.PartnerAdjustmentRepository;
import com.iotiq.partnererp.modules.payouts.repository.PayoutLineRepository;
import com.iotiq.partnererp.modules.payouts.repository.PayoutRepository;
import com.iotiq.partnererp.modules.sequences.service.DocumentNumberService;
import com.iotiq.partnererp.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Partner adjustments (DECISIONS.md §L).
 *
 * <ul>
 *   <li>MANUAL: entered with a reason ({@code PAYOUTS:WRITE}) and approved or rejected by someone else
 *   ({@code PAYOUTS:APPROVE}).</li>
 *   <li>REVERSAL: created by the system when a receipt whose commission was already paid is reversed. The paid payout
 *   stays as it was; the negative amount is carried into the partner's next payout. Nothing is debited from the
 *   partner's account.</li>
 * </ul>
 * Approved adjustments not yet in a live payout are picked up by the next payout run.
 */
@Service
public class PartnerAdjustmentService {

    private final PartnerAdjustmentRepository repository;
    private final PayoutRepository payoutRepository;
    private final PayoutLineRepository lineRepository;
    private final PartnerRepository partnerRepository;
    private final DocumentNumberService numbers;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;
    private final UserNames userNames;
    private final EntityManager entityManager;
    private final Clock clock;

    public PartnerAdjustmentService(PartnerAdjustmentRepository repository, PayoutRepository payoutRepository,
                                    PayoutLineRepository lineRepository, PartnerRepository partnerRepository,
                                    DocumentNumberService numbers, DataScopeService dataScopeService,
                                    AuditService auditService, UserNames userNames, EntityManager entityManager,
                                    Clock clock) {
        this.repository = repository;
        this.payoutRepository = payoutRepository;
        this.lineRepository = lineRepository;
        this.partnerRepository = partnerRepository;
        this.numbers = numbers;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
        this.userNames = userNames;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public record Filters(String query, UUID partnerId, AdjustmentStatus status, AdjustmentKind kind) {
    }

    /** @param created false when the idempotency key was already used and the existing adjustment is returned */
    public record Recorded(AdjustmentResponse adjustment, boolean created) {
    }

    /** What a payment reversal did to payouts: null fields when the receipt had no live payout line. */
    public record ReversalEffect(String payoutNumber, PayoutStatus payoutStatus, String adjustmentNumber) {
    }

    // ------------------------------------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<AdjustmentResponse> search(Filters filters, Pageable pageable, AuthenticatedUser user) {
        Specification<PartnerAdjustment> spec = dataScopeService.resolve(user, ModuleCode.PAYOUTS)
                .<PartnerAdjustment>toSpecification().and(matching(filters));
        Page<PartnerAdjustment> page = repository.findAll(spec, pageable);
        Map<UUID, PayoutLine> allocations = allocations(page.getContent());
        Map<UUID, String> names = userNames.of(page.getContent().stream()
                .flatMap(a -> java.util.stream.Stream.of(a.getCreatedBy(), a.getDecidedBy()))
                .collect(Collectors.toSet()));
        return page.map(a -> toResponse(a, allocations.get(a.getId()), names, user));
    }

    @Transactional(readOnly = true)
    public AdjustmentResponse get(UUID id, AuthenticatedUser user) {
        return toResponse(load(id, user), user);
    }

    // ------------------------------------------------------------------------------------------- manual

    /** Enter a manual adjustment ({@code PAYOUTS:WRITE}, checked by the controller). */
    @Transactional
    public Recorded create(AdjustmentRequest r, AuthenticatedUser user) {
        requireScope(user);
        String reason = r == null ? null : PayoutRules.clean(r.reason());
        List<FieldError> errors = new ArrayList<>();
        if (r == null || r.partnerId() == null) {
            errors.add(new FieldError("partnerId", "Choose the partner."));
        }
        if (r == null || r.amount() == null) {
            errors.add(new FieldError("amount", "Enter the amount: positive to add to the next payout, negative to "
                    + "deduct from it."));
        } else if (r.amount().signum() == 0) {
            errors.add(new FieldError("amount", "The amount can't be zero."));
        } else if (r.amount().stripTrailingZeros().scale() > 2) {
            errors.add(new FieldError("amount", "Use at most two decimal places."));
        } else if (r.amount().abs().compareTo(PayoutRules.MAX_ADJUSTMENT) > 0) {
            errors.add(new FieldError("amount", "The amount is too large."));
        }
        if (reason == null) {
            errors.add(new FieldError("reason", "Give the reason for this adjustment."));
        } else if (reason.length() > PayoutRules.MAX_REASON) {
            errors.add(new FieldError("reason", "Must be at most " + PayoutRules.MAX_REASON + " characters."));
        }
        if (r == null || r.idempotencyKey() == null) {
            errors.add(new FieldError("idempotencyKey", "Reload the form and submit it again."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        BigDecimal amount = r.amount().setScale(2);
        PartnerAdjustment existing = repository.findByIdempotencyKey(r.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (!existing.getPartner().getId().equals(r.partnerId()) || existing.getAmount().compareTo(amount) != 0
                    || !existing.getReason().equals(reason) || !Objects.equals(existing.getCreatedBy(), user.id())) {
                throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED",
                        "This form was already submitted with different details. Reload it and try again.");
            }
            return new Recorded(toResponse(existing, user), false);
        }
        Partner partner = partnerRepository.findById(r.partnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Partner", r.partnerId()));
        PartnerAdjustment a = new PartnerAdjustment();
        a.setAdjustmentNumber(numbers.next(DocumentNumberService.ADJUSTMENT));
        a.setPartner(partner);
        a.setKind(AdjustmentKind.MANUAL);
        a.setAmount(amount);
        a.setReason(reason);
        a.setStatus(AdjustmentStatus.PENDING_APPROVAL);
        a.setIdempotencyKey(r.idempotencyKey());
        repository.saveAndFlush(a);
        auditService.record(AuditActions.PARTNER_ADJUSTMENT_CREATED, AuditActions.ENTITY_PARTNER_ADJUSTMENT, a.getId(),
                null, AuditService.values("adjustmentNumber", a.getAdjustmentNumber(), "partner", partner.getName(),
                        "kind", a.getKind(), "amount", amount, "reason", reason, "status", a.getStatus()));
        return new Recorded(toResponse(a, user), true);
    }

    /** Approve a manual adjustment ({@code PAYOUTS:APPROVE}); the person who entered it can't approve it. */
    @Transactional
    public AdjustmentResponse approve(UUID id, AdjustmentDecisionRequest request, AuthenticatedUser user) {
        return decide(id, true, request == null ? null : request.note(), user);
    }

    /** Reject a manual adjustment with a reason ({@code PAYOUTS:APPROVE}). */
    @Transactional
    public AdjustmentResponse reject(UUID id, AdjustmentDecisionRequest request, AuthenticatedUser user) {
        return decide(id, false, request == null ? null : request.note(), user);
    }

    private AdjustmentResponse decide(UUID id, boolean approve, String rawNote, AuthenticatedUser user) {
        requireScope(user);
        String note = PayoutRules.clean(rawNote);
        if (!approve && note == null) {
            throw ValidationException.of("note", "Give the reason for rejecting this adjustment.");
        }
        if (note != null && note.length() > PayoutRules.MAX_NOTES) {
            throw ValidationException.of("note", "Must be at most " + PayoutRules.MAX_NOTES + " characters.");
        }
        PartnerAdjustment a = repository.lock(id).orElseThrow(() -> new ResourceNotFoundException("Adjustment", id));
        if (a.getStatus() != AdjustmentStatus.PENDING_APPROVAL) {
            throw BusinessException.conflict("ADJUSTMENT_DECIDED",
                    "This adjustment is already " + a.getStatus().getLabel().toLowerCase() + ".");
        }
        if (Objects.equals(a.getCreatedBy(), user.id())) {
            throw BusinessException.conflict("SELF_APPROVAL",
                    "You entered this adjustment, so someone else must " + (approve ? "approve" : "reject") + " it.");
        }
        AdjustmentStatus before = a.getStatus();
        a.setStatus(approve ? AdjustmentStatus.APPROVED : AdjustmentStatus.REJECTED);
        a.setDecidedAt(clock.instant());
        a.setDecidedBy(user.id());
        a.setDecisionNote(note);
        repository.saveAndFlush(a);
        auditService.record(approve ? AuditActions.PARTNER_ADJUSTMENT_APPROVED : AuditActions.PARTNER_ADJUSTMENT_REJECTED,
                AuditActions.ENTITY_PARTNER_ADJUSTMENT, a.getId(), AuditService.values("status", before),
                AuditService.values("status", a.getStatus(), "adjustmentNumber", a.getAdjustmentNumber(),
                        "amount", a.getAmount(), "note", note));
        return toResponse(a, user);
    }

    // ------------------------------------------------------------------------------------------- reversals

    /**
     * Called by the payment reversal, after the reversal entry is saved and with the invoice and receipt locked. Locks
     * the payout holding the receipt's commission. A paid payout (legacy included) stays unchanged and a negative
     * adjustment is created; a draft or pending payout must be recalculated, and an approved or processing one is
     * blocked until it is cancelled, failed or paid with acknowledgement (handled by the payout workflow).
     */
    @Transactional
    public ReversalEffect onReceiptReversed(Payment receipt, Payment reversal, String reason) {
        PayoutLine line = lineRepository.activeForPayment(receipt.getId()).orElse(null);
        if (line == null) {
            return null;
        }
        Payout payout = payoutRepository.lock(line.getPayout().getId())
                .orElseThrow(() -> new IllegalStateException("Payout of line " + line.getId() + " is missing"));
        entityManager.refresh(payout);
        String adjustmentNumber = null;
        if (payout.getStatus() == PayoutStatus.PAID) {
            PartnerAdjustment adjustment = reversalAdjustment(payout, line, reversal,
                    "Payment " + receipt.getPaymentNumber() + " reversed after payout " + payoutLabel(payout)
                            + " was paid: " + reason);
            adjustmentNumber = adjustment == null ? null : adjustment.getAdjustmentNumber();
        }
        return new ReversalEffect(payoutLabel(payout), payout.getStatus(), adjustmentNumber);
    }

    /**
     * The negative adjustment for a paid line whose receipt was reversed. Created once per line (unique
     * {@code source_line_id}); returns null when it already exists or the line had no positive commission.
     */
    PartnerAdjustment reversalAdjustment(Payout payout, PayoutLine line, Payment reversal, String reason) {
        if (line.getAmount().signum() <= 0 || repository.existsBySourceLineId(line.getId())) {
            return null;
        }
        PartnerAdjustment a = new PartnerAdjustment();
        a.setAdjustmentNumber(numbers.next(DocumentNumberService.ADJUSTMENT));
        a.setPartner(payout.getPartner());
        a.setKind(AdjustmentKind.REVERSAL);
        a.setAmount(line.getAmount().negate());
        a.setReason(PayoutRules.truncate(reason, PayoutRules.MAX_REASON));
        a.setStatus(AdjustmentStatus.APPROVED);
        a.setSourcePayment(reversal);
        a.setSourceLine(line);
        a.setDecidedAt(clock.instant());
        repository.saveAndFlush(a);
        auditService.record(AuditActions.PARTNER_ADJUSTMENT_CREATED, AuditActions.ENTITY_PARTNER_ADJUSTMENT, a.getId(),
                null, AuditService.values("adjustmentNumber", a.getAdjustmentNumber(),
                        "partner", payout.getPartner().getName(), "kind", a.getKind(), "amount", a.getAmount(),
                        "reason", a.getReason(), "status", a.getStatus(), "payout", payoutLabel(payout),
                        "reversalNumber", reversal.getPaymentNumber()));
        return a;
    }

    static String payoutLabel(Payout payout) {
        return payout.getPayoutNumber() != null ? payout.getPayoutNumber() : "(legacy payout)";
    }

    // ------------------------------------------------------------------------------------------- helpers

    private void requireScope(AuthenticatedUser user) {
        if (!dataScopeService.resolve(user, ModuleCode.PAYOUTS).unrestricted()) {
            throw BusinessException.conflict("PAYOUTS_SCOPE",
                    "Payouts need access to all records. Ask an administrator to set the Payouts scope to All.");
        }
    }

    private PartnerAdjustment load(UUID id, AuthenticatedUser user) {
        if (!dataScopeService.resolve(user, ModuleCode.PAYOUTS).unrestricted()) {
            throw new ResourceNotFoundException("Adjustment", id);
        }
        return repository.findWithPartnerById(id).orElseThrow(() -> new ResourceNotFoundException("Adjustment", id));
    }

    private Map<UUID, PayoutLine> allocations(List<PartnerAdjustment> adjustments) {
        if (adjustments.isEmpty()) {
            return Map.of();
        }
        return lineRepository.activeForAdjustments(adjustments.stream().map(PartnerAdjustment::getId).toList())
                .stream().collect(Collectors.toMap(l -> l.getAdjustment().getId(), l -> l, (a, b) -> a));
    }

    AdjustmentResponse toResponse(PartnerAdjustment a, AuthenticatedUser user) {
        Set<UUID> ids = new HashSet<>();
        ids.add(a.getCreatedBy());
        ids.add(a.getDecidedBy());
        return toResponse(a, allocations(List.of(a)).get(a.getId()), userNames.of(ids), user);
    }

    private static AdjustmentResponse toResponse(PartnerAdjustment a, PayoutLine allocation, Map<UUID, String> names,
                                                 AuthenticatedUser user) {
        Payment source = a.getSourcePayment();
        boolean canDecide = a.getStatus() == AdjustmentStatus.PENDING_APPROVAL
                && user.permissions().canPerform(ModuleCode.PAYOUTS.name(), "APPROVE")
                && !Objects.equals(a.getCreatedBy(), user.id());
        Payout payout = allocation == null ? null : allocation.getPayout();
        return new AdjustmentResponse(a.getId(), a.getAdjustmentNumber(),
                new PartnerRef(a.getPartner().getId(), a.getPartner().getName()), a.getKind().name(),
                a.getKind().getLabel(), a.getAmount(), a.getReason(), a.getStatus().name(), a.getStatus().getLabel(),
                source == null ? null : source.getId(), source == null ? null : source.getPaymentNumber(),
                a.getCreatedAt(), names.get(a.getCreatedBy()), a.getDecidedAt(), names.get(a.getDecidedBy()),
                a.getDecisionNote(),
                payout == null ? null : new AdjustmentResponse.PayoutRef(payout.getId(), payout.getPayoutNumber(),
                        payout.getStatus().name()),
                canDecide);
    }

    private static Specification<PartnerAdjustment> matching(Filters f) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<PartnerAdjustment, Partner> partner = root.join("partner", JoinType.INNER);
            if (f.query() != null && !f.query().isBlank()) {
                String like = PayoutRules.like(f.query());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("adjustmentNumber")), like, '\\'),
                        cb.like(cb.lower(root.get("reason")), like, '\\'),
                        cb.like(cb.lower(partner.get("name")), like, '\\')));
            }
            if (f.partnerId() != null) {
                predicates.add(cb.equal(partner.get("id"), f.partnerId()));
            }
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.kind() != null) {
                predicates.add(cb.equal(root.get("kind"), f.kind()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
