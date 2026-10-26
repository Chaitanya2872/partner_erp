package com.iotiq.partnererp.modules.payments.service;

import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.common.enums.PaymentStatus;
import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.audit.entity.AuditLog;
import com.iotiq.partnererp.modules.audit.repository.AuditLogRepository;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.DataScope;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.auth.service.DataScopeService;
import com.iotiq.partnererp.modules.invoices.entity.Invoice;
import com.iotiq.partnererp.modules.invoices.repository.InvoiceRepository;
import com.iotiq.partnererp.modules.invoices.service.InvoiceRules;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import com.iotiq.partnererp.modules.payments.dto.PaymentHistoryEntry;
import com.iotiq.partnererp.modules.payments.dto.PaymentInvoiceOption;
import com.iotiq.partnererp.modules.payments.dto.PaymentMeta;
import com.iotiq.partnererp.modules.payments.dto.PaymentReport;
import com.iotiq.partnererp.modules.payments.dto.PaymentRequest;
import com.iotiq.partnererp.modules.payments.dto.PaymentResponse;
import com.iotiq.partnererp.modules.payments.dto.PaymentSummary;
import com.iotiq.partnererp.modules.payments.dto.ReversePaymentRequest;
import com.iotiq.partnererp.modules.payments.entity.Payment;
import com.iotiq.partnererp.modules.payments.entity.PaymentEntryType;
import com.iotiq.partnererp.modules.payments.entity.PaymentMethod;
import com.iotiq.partnererp.modules.payments.repository.PaymentRepository;
import com.iotiq.partnererp.modules.payouts.service.PartnerAdjustmentService;
import com.iotiq.partnererp.modules.products.service.ProductCsv;
import com.iotiq.partnererp.modules.sequences.service.DocumentNumberService;
import com.iotiq.partnererp.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import java.util.EnumMap;
import java.util.HashMap;
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
 * Payments received (Phase 10, DECISIONS.md §K).
 *
 * <ul>
 *   <li>The ledger is append-only. A receipt is recorded against an issued invoice ({@code PAYMENTS:WRITE}); a
 *   mistaken receipt is reversed ({@code PAYMENTS:REVERSE_PAYMENT}, with a reason) by a new negative entry, so the
 *   original is kept and the reversal is auditable. Nothing is edited or deleted.</li>
 *   <li>Invoices are never written: the balance is derived as grand total − Σ amounts, so issued invoices and their
 *   tax snapshots stay exactly as issued.</li>
 *   <li>A receipt must be dated between the invoice date and today, and can't exceed the outstanding balance.
 *   Resubmitting a form (same idempotency key) returns the entry already recorded; a reference already used, or the
 *   same amount on the same day, needs an explicit confirmation.</li>
 *   <li>Partner commission is paid by payouts (Phase 11). Reversing a receipt whose commission was already paid
 *   leaves the paid payout unchanged and carries a negative partner adjustment into the next payout (D-12); an
 *   unpaid payout holding the receipt must be recalculated, or is blocked once approved.</li>
 *   <li>Refunds, credit notes, advances, overpayments and customer TDS deductions are not supported (P10-G).</li>
 * </ul>
 */
@Service
public class PaymentService {

    public static final String REVERSE = "REVERSE_PAYMENT";
    static final int EXPORT_LIMIT = 5_000;
    static final int OPTION_LIMIT = 50;

    private final PaymentRepository repository;
    private final InvoiceRepository invoiceRepository;
    private final DocumentNumberService numbers;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final PartnerAdjustmentService partnerAdjustments;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final Clock clock;

    public PaymentService(PaymentRepository repository, InvoiceRepository invoiceRepository,
                          DocumentNumberService numbers, DataScopeService dataScopeService, AuditService auditService,
                          AuditLogRepository auditLogRepository, UserRepository userRepository,
                          PartnerAdjustmentService partnerAdjustments, ObjectMapper objectMapper,
                          EntityManager entityManager, Clock clock) {
        this.repository = repository;
        this.invoiceRepository = invoiceRepository;
        this.numbers = numbers;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.partnerAdjustments = partnerAdjustments;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** ACTIVE: receipts not reversed; REVERSED: receipts that were reversed. */
    public enum State { ACTIVE, REVERSED }

    public record Filters(String query, UUID invoiceId, UUID partnerId, PaymentMethod method, PaymentEntryType type,
                          State state, LocalDate from, LocalDate to) {
    }

    /** @param created false when the request resubmitted an idempotency key and the existing entry is returned */
    public record Recorded(PaymentResponse payment, boolean created) {
    }

    // ------------------------------------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<PaymentSummary> search(Filters filters, Pageable pageable, AuthenticatedUser user) {
        Page<Payment> page = repository.findAll(spec(filters, user), pageable);
        Set<UUID> reversed = reversed(page.getContent());
        return page.map(p -> summary(p, reversed));
    }

    @Transactional(readOnly = true)
    public List<PaymentSummary> searchAll(Filters filters, Sort sort, AuthenticatedUser user) {
        List<Payment> rows = repository.findAll(spec(filters, user), PageRequest.of(0, EXPORT_LIMIT, sort)).getContent();
        Set<UUID> reversed = reversed(rows);
        return rows.stream().map(p -> summary(p, reversed)).toList();
    }

    /** CSV of the filtered ledger (at most 5,000 rows). References are included for reconciliation. */
    @Transactional(readOnly = true)
    public String exportCsv(Filters filters, Sort sort, AuthenticatedUser user) {
        List<List<String>> rows = searchAll(filters, sort, user).stream().map(p -> List.of(
                Objects.toString(p.paymentNumber(), ""), p.entryTypeLabel(), p.paymentDate().toString(),
                Objects.toString(p.invoice().invoiceNumber(), ""), Objects.toString(p.billingName(), ""),
                p.partner().name(), p.methodLabel(), Objects.toString(p.reference(), ""),
                p.amount().toPlainString(), p.netAmount().toPlainString(), p.reversed() ? "yes" : "",
                p.reversalOf() == null ? "" : Objects.toString(p.reversalOf().paymentNumber(), ""),
                Objects.toString(p.reversalReason(), ""),
                p.createdAt() == null ? "" : p.createdAt().toString())).toList();
        return ProductCsv.write(List.of("payment", "type", "date", "invoice", "billed_to", "partner", "method",
                "reference", "amount_incl_gst", "amount_ex_gst", "reversed", "reversal_of", "reversal_reason",
                "recorded_at"), rows);
    }

    /** Totals of the filtered ledger, and today's receivables (all live issued invoices in scope). */
    @Transactional(readOnly = true)
    public PaymentReport report(Filters filters, AuthenticatedUser user) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Payment> root = cq.from(Payment.class);
        Predicate where = spec(filters, user).toPredicate(root, cq, cb);
        cq.multiselect(root.get("entryType"), root.get("paymentMethod"), cb.count(root), cb.sum(root.get("amount")),
                        cb.sum(root.get("netAmount")))
                .where(where).groupBy(root.get("entryType"), root.get("paymentMethod"));
        long entries = 0;
        long receipts = 0;
        long reversals = 0;
        BigDecimal received = BigDecimal.ZERO.setScale(2);
        BigDecimal reversed = BigDecimal.ZERO.setScale(2);
        BigDecimal refunded = BigDecimal.ZERO.setScale(2);
        BigDecimal net = BigDecimal.ZERO.setScale(2);
        BigDecimal netExGst = BigDecimal.ZERO.setScale(2);
        Map<PaymentMethod, long[]> methodCounts = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, BigDecimal> methodNet = new EnumMap<>(PaymentMethod.class);
        for (Tuple t : entityManager.createQuery(cq).getResultList()) {
            PaymentEntryType type = t.get(0, PaymentEntryType.class);
            PaymentMethod method = t.get(1, PaymentMethod.class);
            long count = t.get(2, Long.class);
            BigDecimal amount = t.get(3, BigDecimal.class);
            BigDecimal amountExGst = t.get(4, BigDecimal.class);
            entries += count;
            net = net.add(amount);
            netExGst = netExGst.add(amountExGst);
            methodNet.merge(method, amount, BigDecimal::add);
            switch (type) {
                case RECEIPT -> {
                    receipts += count;
                    received = received.add(amount);
                    methodCounts.computeIfAbsent(method, m -> new long[1])[0] += count;
                }
                case REVERSAL -> {
                    reversals += count;
                    reversed = reversed.add(amount.negate());
                }
                case REFUND -> refunded = refunded.add(amount.negate());
            }
        }
        List<PaymentReport.MethodTotal> byMethod = Arrays.stream(PaymentMethod.values())
                .filter(methodNet::containsKey)
                .map(m -> new PaymentReport.MethodTotal(m.name(), m.getLabel(),
                        methodCounts.getOrDefault(m, new long[1])[0], methodNet.get(m)))
                .toList();
        return new PaymentReport(entries, receipts, reversals, received, reversed, refunded, net, netExGst, byMethod,
                receivables(user));
    }

    @Transactional(readOnly = true)
    public PaymentMeta meta(AuthenticatedUser user) {
        return new PaymentMeta(
                Arrays.stream(PaymentMethod.values()).map(m -> new PaymentMeta.Option(m.name(), m.getLabel())).toList(),
                Arrays.stream(PaymentEntryType.values()).map(t -> new PaymentMeta.Option(t.name(), t.getLabel())).toList(),
                List.of(new PaymentMeta.Option(State.ACTIVE.name(), "Active"),
                        new PaymentMeta.Option(State.REVERSED.name(), "Reversed")),
                PaymentRules.REFERENCE_OPTIONAL.stream().map(Enum::name).sorted().toList(),
                PaymentRules.MAX_REASON, PaymentRules.MAX_NOTES,
                user.permissions().canWrite(ModuleCode.PAYMENTS.name()), canReverse(user),
                user.permissions().canPerform(ModuleCode.PAYMENTS.name(), "EXPORT"));
    }

    /**
     * Restricted invoice picker for recording a receipt: live issued invoices with a balance above zero (or the one
     * asked for), at most 50, newest first. Searched on invoice number, billed-to name and partner name only.
     */
    @Transactional(readOnly = true)
    public List<PaymentInvoiceOption> invoiceOptions(String query, UUID invoiceId, AuthenticatedUser user) {
        if (!invoicesVisible(user)) {
            return List.of();
        }
        Specification<Invoice> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.equal(root.get("status"), InvoiceStatus.ISSUED));
            Join<Invoice, Partner> partner = root.join("partner", JoinType.INNER);
            if (invoiceId != null) {
                predicates.add(cb.equal(root.get("id"), invoiceId));
            } else {
                Subquery<BigDecimal> paid = cq.subquery(BigDecimal.class);
                Root<Payment> p = paid.from(Payment.class);
                paid.select(cb.coalesce(cb.sum(p.get("amount")), BigDecimal.ZERO)).where(cb.equal(p.get("invoice"), root));
                predicates.add(cb.greaterThan(cb.diff(root.get("totals").get("grandTotal"), paid), BigDecimal.ZERO));
            }
            if (query != null && !query.isBlank()) {
                String like = like(query);
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("invoiceNumber"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("billingName"), "")), like, '\\'),
                        cb.like(cb.lower(partner.get("name")), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<Invoice> invoices = invoiceRepository.findAll(spec,
                PageRequest.of(0, OPTION_LIMIT, Sort.by(Sort.Direction.DESC, "invoiceDate", "invoiceNumber"))).getContent();
        Map<UUID, BigDecimal> paid = invoices.isEmpty() ? Map.of() : invoiceRepository
                .paidAmounts(invoices.stream().map(Invoice::getId).toList()).stream()
                .collect(Collectors.toMap(InvoiceRepository.PaidAmount::getInvoiceId, InvoiceRepository.PaidAmount::getPaid));
        LocalDate today = LocalDate.now(clock);
        return invoices.stream().map(i -> {
            BigDecimal amount = paid.getOrDefault(i.getId(), BigDecimal.ZERO).setScale(2);
            BigDecimal total = i.getTotals().getGrandTotal();
            PaymentStatus status = InvoiceRules.paymentStatus(i.getStatus(), total, amount, i.getDueDate(), today);
            return new PaymentInvoiceOption(i.getId(), i.getInvoiceNumber(), i.getInvoiceDate(), i.getDueDate(),
                    i.getBillingName(), new PartnerRef(i.getPartner().getId(), i.getPartner().getName()), total, amount,
                    total.subtract(amount), status == null ? null : status.getLabel());
        }).toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id, AuthenticatedUser user) {
        return toResponse(load(id, user), user);
    }

    /** Audit events of the entry; for a reversal, those of the receipt it reverses (where the reversal is logged). */
    @Transactional(readOnly = true)
    public List<PaymentHistoryEntry> history(UUID id, AuthenticatedUser user) {
        Payment payment = load(id, user);
        UUID subject = payment.getReversedPayment() != null ? payment.getReversedPayment().getId() : payment.getId();
        List<AuditLog> entries = auditLogRepository.findTop200ByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                AuditActions.ENTITY_PAYMENT, subject);
        Map<UUID, String> names = names(entries.stream().map(AuditLog::getUserId).collect(Collectors.toSet()));
        return entries.stream().map(e -> new PaymentHistoryEntry(e.getId(), e.getCreatedAt(),
                e.getUserId() == null ? null : names.get(e.getUserId()), e.getAction(), changes(e))).toList();
    }

    // ------------------------------------------------------------------------------------------- commands

    /**
     * Record a receipt ({@code PAYMENTS:WRITE}, checked by the controller). The invoice row is locked, so receipts
     * for one invoice are checked against the balance one at a time and can never overpay it together.
     */
    @Transactional
    public Recorded record(PaymentRequest r, AuthenticatedUser user) {
        if (r == null) {
            throw ValidationException.of("invoiceId", "Choose the invoice.");
        }
        String reference = PaymentRules.normalizeReference(r.reference());
        String notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().strip();
        validateShape(r, reference, notes);
        BigDecimal amount = r.amount().setScale(2);
        String hash = PaymentRules.requestHash(r.invoiceId(), r.paymentDate(), amount, r.method(), reference, notes);
        Recorded replay = replay(r.idempotencyKey(), hash, user);
        if (replay != null) {
            return replay;
        }

        if (!invoicesVisible(user)) {
            throw new ResourceNotFoundException("Invoice", r.invoiceId());
        }
        Invoice invoice = invoiceRepository.lockLive(r.invoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", r.invoiceId()));
        // A resubmission that waited for the invoice lock finds the entry its first submission recorded.
        replay = replay(r.idempotencyKey(), hash, user);
        if (replay != null) {
            return replay;
        }
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw BusinessException.conflict("INVOICE_DRAFT",
                    "Payments are recorded against issued invoices only. Issue the invoice first.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw BusinessException.conflict("INVOICE_CANCELLED", "This invoice is cancelled, so it takes no payments.");
        }
        LocalDate today = LocalDate.now(clock);
        if (r.paymentDate().isAfter(today)) {
            throw ValidationException.of("paymentDate", "The payment date can't be in the future.");
        }
        if (r.paymentDate().isBefore(invoice.getInvoiceDate())) {
            throw ValidationException.of("paymentDate", "The payment date can't be before the invoice date ("
                    + invoice.getInvoiceDate() + "). Advance receipts aren't supported.");
        }
        BigDecimal total = invoice.getTotals().getGrandTotal();
        BigDecimal balance = total.subtract(repository.paidAmount(invoice.getId())).setScale(2);
        if (balance.signum() <= 0) {
            throw BusinessException.conflict("INVOICE_SETTLED", "This invoice has no outstanding balance.");
        }
        if (amount.compareTo(balance) > 0) {
            throw new BusinessException("OVERPAYMENT", "The amount is more than the outstanding balance of "
                    + rupees(balance) + ". Overpayments and advances aren't supported.");
        }
        boolean confirmed = Boolean.TRUE.equals(r.confirmDuplicate());
        checkDuplicates(invoice, r, amount, reference, confirmed);

        BigDecimal net = PaymentRules.netAmount(amount, invoice.getTotals().getTaxableAmount(), total, balance,
                repository.netAmount(invoice.getId()));
        Payment payment = new Payment();
        payment.setPaymentNumber(numbers.next(DocumentNumberService.PAYMENT));
        payment.setEntryType(PaymentEntryType.RECEIPT);
        payment.setInvoice(invoice);
        payment.setPartner(invoice.getPartner());
        payment.setLead(invoice.getLead());
        payment.setPaymentDate(r.paymentDate());
        payment.setAmount(amount);
        payment.setNetAmount(net);
        payment.setRefund(false);
        payment.setPaymentMethod(r.method());
        payment.setReferenceNumber(reference);
        payment.setNotes(notes);
        payment.setIdempotencyKey(r.idempotencyKey());
        payment.setRequestHash(hash);
        repository.saveAndFlush(payment);
        BigDecimal balanceAfter = balance.subtract(amount);
        auditService.record(AuditActions.PAYMENT_RECORDED, AuditActions.ENTITY_PAYMENT, payment.getId(), null,
                AuditService.values("paymentNumber", payment.getPaymentNumber(),
                        "invoiceNumber", invoice.getInvoiceNumber(), "paymentDate", payment.getPaymentDate(),
                        "amount", amount, "netAmount", net, "method", r.method(),
                        "reference", PaymentRules.maskReference(reference), "balanceAfter", balanceAfter,
                        "duplicateConfirmed", confirmed ? Boolean.TRUE : null));
        return new Recorded(toResponse(payment, user), true);
    }

    /**
     * Reverse a receipt ({@code PAYMENTS:REVERSE_PAYMENT}, checked by the controller) with a written reason. The receipt
     * stays as recorded; a reversal entry dated today mirrors it with negative amounts, which restores the invoice
     * balance. A receipt is reversed at most once and a reversal can't be reversed. When the receipt's commission is in
     * a paid payout, a negative partner adjustment is created (the payout itself never changes).
     */
    @Transactional
    public PaymentResponse reverse(UUID id, ReversePaymentRequest request, AuthenticatedUser user) {
        Payment current = load(id, user);
        String reason = request == null || request.reason() == null || request.reason().isBlank() ? null
                : request.reason().strip();
        if (reason == null) {
            throw ValidationException.of("reason", "Give the reason for reversing this payment.");
        }
        if (reason.length() > PaymentRules.MAX_REASON) {
            throw ValidationException.of("reason", "Must be at most " + PaymentRules.MAX_REASON + " characters.");
        }
        // Same lock order as recording (invoice, then entry), so reversals and receipts of one invoice queue.
        Invoice invoice = invoiceRepository.lockLive(current.getInvoice().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        Payment original = repository.lock(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        if (original.getEntryType() == PaymentEntryType.REVERSAL) {
            throw BusinessException.conflict("PAYMENT_IS_REVERSAL",
                    "This entry is itself a reversal. Record the payment again instead.");
        }
        if (original.getEntryType() != PaymentEntryType.RECEIPT) {
            throw BusinessException.conflict("PAYMENT_NOT_REVERSIBLE", "Only receipts can be reversed.");
        }
        if (repository.findByReversedPaymentId(id).isPresent()) {
            throw BusinessException.conflict("PAYMENT_ALREADY_REVERSED", "This payment has already been reversed.");
        }
        BigDecimal balanceBefore = invoice.getTotals().getGrandTotal().subtract(repository.paidAmount(invoice.getId()));
        Payment reversal = new Payment();
        reversal.setPaymentNumber(numbers.next(DocumentNumberService.PAYMENT));
        reversal.setEntryType(PaymentEntryType.REVERSAL);
        reversal.setReversedPayment(original);
        reversal.setReversalReason(reason);
        reversal.setInvoice(invoice);
        reversal.setPartner(original.getPartner());
        reversal.setLead(original.getLead());
        reversal.setPaymentDate(LocalDate.now(clock));
        reversal.setAmount(original.getAmount().negate());
        reversal.setNetAmount(original.getNetAmount().negate());
        reversal.setRefund(false);
        reversal.setPaymentMethod(original.getPaymentMethod());
        reversal.setReferenceNumber(original.getReferenceNumber());
        repository.saveAndFlush(reversal);
        // Lock order: invoice, receipt, then the payout holding the receipt's commission.
        PartnerAdjustmentService.ReversalEffect effect = partnerAdjustments.onReceiptReversed(original, reversal, reason);
        auditService.record(AuditActions.PAYMENT_REVERSED, AuditActions.ENTITY_PAYMENT, original.getId(),
                AuditService.values("reversed", false),
                AuditService.values("reversed", true, "reversalNumber", reversal.getPaymentNumber(),
                        "paymentNumber", original.getPaymentNumber(), "invoiceNumber", invoice.getInvoiceNumber(),
                        "amount", reversal.getAmount(), "netAmount", reversal.getNetAmount(),
                        "reversalDate", reversal.getPaymentDate(), "reason", reason,
                        "balanceAfter", balanceBefore.add(original.getAmount()),
                        "payout", effect == null ? null : effect.payoutNumber(),
                        "payoutStatus", effect == null ? null : effect.payoutStatus(),
                        "partnerAdjustment", effect == null ? null : effect.adjustmentNumber()));
        return toResponse(original, user);
    }

    // ------------------------------------------------------------------------------------------- permissions

    public static boolean canReverse(AuthenticatedUser user) {
        return user.permissions().canPerform(ModuleCode.PAYMENTS.name(), REVERSE);
    }

    // ------------------------------------------------------------------------------------------- rules

    private static void validateShape(PaymentRequest r, String reference, String notes) {
        List<FieldError> errors = new ArrayList<>();
        if (r.invoiceId() == null) {
            errors.add(new FieldError("invoiceId", "Choose the invoice."));
        }
        if (r.paymentDate() == null) {
            errors.add(new FieldError("paymentDate", "Enter the date the payment was received."));
        }
        if (r.amount() == null) {
            errors.add(new FieldError("amount", "Enter the amount received."));
        } else if (r.amount().signum() <= 0) {
            errors.add(new FieldError("amount", "Enter an amount above zero. Refunds aren't supported."));
        } else if (r.amount().stripTrailingZeros().scale() > 2) {
            errors.add(new FieldError("amount", "Use at most two decimal places."));
        } else if (r.amount().compareTo(PaymentRules.MAX_AMOUNT) > 0) {
            errors.add(new FieldError("amount", "The amount is too large."));
        }
        if (r.method() == null) {
            errors.add(new FieldError("method", "Choose how the payment was received."));
        }
        if (reference == null) {
            if (r.method() != null && !PaymentRules.REFERENCE_OPTIONAL.contains(r.method())) {
                errors.add(new FieldError("reference", "Enter the reference (UTR, cheque number or transaction id)."));
            }
        } else if (reference.length() > PaymentRules.MAX_REFERENCE) {
            errors.add(new FieldError("reference", "Must be at most " + PaymentRules.MAX_REFERENCE + " characters."));
        } else if (PaymentRules.looksLikeCardNumber(reference)) {
            errors.add(new FieldError("reference", "This looks like a card or account number. Enter the transaction "
                    + "or approval reference, or at most the last four digits."));
        }
        if (notes != null && notes.length() > PaymentRules.MAX_NOTES) {
            errors.add(new FieldError("notes", "Must be at most " + PaymentRules.MAX_NOTES + " characters."));
        }
        if (r.idempotencyKey() == null) {
            errors.add(new FieldError("idempotencyKey", "Reload the form and submit it again."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /** The entry already recorded for this key, or null. A key reused for different inputs is refused. */
    private Recorded replay(UUID key, String hash, AuthenticatedUser user) {
        Payment existing = repository.findByIdempotencyKey(key).orElse(null);
        if (existing == null) {
            return null;
        }
        if (!hash.equals(existing.getRequestHash()) || !Objects.equals(existing.getCreatedBy(), user.id())) {
            throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "This form was already submitted with different details. Reload it and enter the payment again.");
        }
        return new Recorded(toResponse(existing, user), false);
    }

    /**
     * Duplicate prevention (D-10, P10-04). The same reference twice on one invoice is refused outright. The same
     * reference on another invoice (one transfer paying two invoices) or the same amount, date and method on this
     * invoice needs the user's confirmation. Reversed receipts are ignored.
     */
    private void checkDuplicates(Invoice invoice, PaymentRequest r, BigDecimal amount, String reference,
                                 boolean confirmed) {
        String key = PaymentRules.referenceKey(reference);
        List<Payment> sameReference = key == null ? List.of() : repository.liveReceiptsWithReference(key);
        for (Payment p : sameReference) {
            if (p.getInvoice().getId().equals(invoice.getId())) {
                throw BusinessException.conflict("DUPLICATE_REFERENCE", "Payment " + p.getPaymentNumber()
                        + " on this invoice already has this reference. Reverse it first if it was wrong.");
            }
        }
        if (confirmed) {
            return;
        }
        if (!sameReference.isEmpty()) {
            Payment p = sameReference.get(0);
            throw BusinessException.conflict("POSSIBLE_DUPLICATE", "Payment " + p.getPaymentNumber() + " on invoice "
                    + p.getInvoice().getInvoiceNumber() + " has the same reference. Confirm that this is a "
                    + "separate payment.");
        }
        for (Payment p : repository.liveReceipts(invoice.getId())) {
            if (p.getAmount().compareTo(amount) == 0 && p.getPaymentDate().equals(r.paymentDate())
                    && p.getPaymentMethod() == r.method()) {
                throw BusinessException.conflict("POSSIBLE_DUPLICATE", "Payment " + p.getPaymentNumber()
                        + " on this invoice has the same amount, date and method. Confirm that this is a separate "
                        + "payment.");
            }
        }
    }

    private static String rupees(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "₹" + format.format(amount);
    }

    // ------------------------------------------------------------------------------------------- loading, scope

    private Payment load(UUID id, AuthenticatedUser user) {
        if (!dataScopeService.resolve(user, ModuleCode.PAYMENTS).unrestricted()) {
            throw new ResourceNotFoundException("Payment", id);
        }
        return repository.findWithInvoiceById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }

    /** The Payments scope must be unrestricted, and so must the Invoices scope when the caller has one (as P9-07). */
    private boolean invoicesVisible(AuthenticatedUser user) {
        return dataScopeService.resolve(user, ModuleCode.PAYMENTS).unrestricted()
                && user.permissions().scope(ModuleCode.INVOICES.name()).map(s -> s == DataScope.ALL).orElse(true);
    }

    private PaymentReport.Receivables receivables(AuthenticatedUser user) {
        if (!invoicesVisible(user)) {
            BigDecimal zero = BigDecimal.ZERO.setScale(2);
            return new PaymentReport.Receivables(0, zero, 0, zero);
        }
        PaymentRepository.Receivables r = repository.receivables(LocalDate.now(clock));
        return new PaymentReport.Receivables(r.getOutstandingInvoices(), r.getOutstandingAmount().setScale(2),
                r.getOverdueInvoices(), r.getOverdueAmount().setScale(2));
    }

    // ------------------------------------------------------------------------------------------- responses

    private Set<UUID> reversed(List<Payment> payments) {
        List<UUID> receipts = payments.stream().filter(p -> p.getEntryType() == PaymentEntryType.RECEIPT)
                .map(Payment::getId).toList();
        return receipts.isEmpty() ? Set.of() : new HashSet<>(repository.reversedAmong(receipts));
    }

    private static PaymentSummary summary(Payment p, Set<UUID> reversed) {
        Invoice i = p.getInvoice();
        Payment of = p.getReversedPayment();
        return new PaymentSummary(p.getId(), p.getPaymentNumber(), p.getEntryType(), p.getEntryType().getLabel(),
                p.getPaymentDate(), p.getAmount(), p.getNetAmount(), p.getPaymentMethod(),
                p.getPaymentMethod().getLabel(), p.getReferenceNumber(),
                new PaymentSummary.InvoiceRef(i.getId(), i.getInvoiceNumber()), i.getBillingName(),
                new PartnerRef(p.getPartner().getId(), p.getPartner().getName()), reversed.contains(p.getId()),
                of == null ? null : new PaymentSummary.PaymentRef(of.getId(), of.getPaymentNumber()),
                p.getReversalReason(), p.getCreatedAt());
    }

    private PaymentResponse toResponse(Payment p, AuthenticatedUser user) {
        Invoice i = p.getInvoice();
        LocalDate today = LocalDate.now(clock);
        BigDecimal paid = repository.paidAmount(i.getId()).setScale(2);
        BigDecimal total = i.getTotals().getGrandTotal();
        PaymentStatus status = InvoiceRules.paymentStatus(i.getStatus(), total, paid, i.getDueDate(), today);
        Payment reversal = p.getEntryType() == PaymentEntryType.RECEIPT
                ? repository.findByReversedPaymentId(p.getId()).orElse(null) : null;
        String blocked = null;
        if (p.getEntryType() == PaymentEntryType.REFUND) {
            blocked = "Refunds recorded before payment management can't be reversed.";
        }
        boolean canReverse = p.getEntryType() == PaymentEntryType.RECEIPT && reversal == null && blocked == null
                && canReverse(user);
        Set<UUID> actors = new HashSet<>();
        actors.add(p.getCreatedBy());
        if (reversal != null) {
            actors.add(reversal.getCreatedBy());
        }
        Map<UUID, String> names = names(actors);
        Payment of = p.getReversedPayment();
        return new PaymentResponse(p.getId(), p.getPaymentNumber(), p.getEntryType(), p.getEntryType().getLabel(),
                p.getPaymentDate(), p.getAmount(), p.getNetAmount(), p.getPaymentMethod(),
                p.getPaymentMethod().getLabel(), p.getReferenceNumber(), p.getNotes(),
                new PartnerRef(p.getPartner().getId(), p.getPartner().getName()),
                new PaymentResponse.InvoiceBalance(i.getId(), i.getInvoiceNumber(), i.getStatus(), i.getBillingName(),
                        i.getInvoiceDate(), i.getDueDate(), total, i.getTotals().getTaxableAmount(), paid,
                        total.subtract(paid), status, status == null ? null : status.getLabel()),
                reversal == null ? null : new PaymentResponse.Reversal(reversal.getId(), reversal.getPaymentNumber(),
                        reversal.getPaymentDate(), reversal.getReversalReason(), reversal.getCreatedAt(),
                        names.get(reversal.getCreatedBy())),
                of == null ? null : new PaymentSummary.PaymentRef(of.getId(), of.getPaymentNumber()),
                p.getReversalReason(), canReverse, blocked, p.getCreatedAt(), names.get(p.getCreatedBy()));
    }

    private Map<UUID, String> names(Set<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        Set<UUID> present = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (!present.isEmpty()) {
            userRepository.findAllById(present).forEach(u -> names.put(u.getId(), u.getFullName()));
        }
        return names;
    }

    private List<PaymentHistoryEntry.Change> changes(AuditLog entry) {
        JsonNode before = parse(entry.getOldValues());
        JsonNode after = parse(entry.getNewValues());
        Set<String> fields = new LinkedHashSet<>();
        before.propertyNames().forEach(fields::add);
        after.propertyNames().forEach(fields::add);
        return fields.stream().map(field -> new PaymentHistoryEntry.Change(field,
                before.path(field).isMissingNode() ? null : before.get(field),
                after.path(field).isMissingNode() ? null : after.get(field))).toList();
    }

    private JsonNode parse(String json) {
        return json == null ? objectMapper.createObjectNode() : objectMapper.readTree(json);
    }

    // ------------------------------------------------------------------------------------------- search

    private static String like(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private Specification<Payment> spec(Filters filters, AuthenticatedUser user) {
        return dataScopeService.resolve(user, ModuleCode.PAYMENTS).<Payment>toSpecification().and(matching(filters));
    }

    private static Specification<Payment> matching(Filters f) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Payment, Invoice> invoice = root.join("invoice", JoinType.INNER);
            Join<Payment, Partner> partner = root.join("partner", JoinType.INNER);
            if (f.query() != null && !f.query().isBlank()) {
                String like = like(f.query());
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("paymentNumber"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("referenceNumber"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(invoice.get("invoiceNumber"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(invoice.get("billingName"), "")), like, '\\'),
                        cb.like(cb.lower(partner.get("name")), like, '\\')));
            }
            if (f.invoiceId() != null) {
                predicates.add(cb.equal(invoice.get("id"), f.invoiceId()));
            }
            if (f.partnerId() != null) {
                predicates.add(cb.equal(partner.get("id"), f.partnerId()));
            }
            if (f.method() != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), f.method()));
            }
            if (f.type() != null) {
                predicates.add(cb.equal(root.get("entryType"), f.type()));
            }
            if (f.state() != null && cq != null) {
                Subquery<UUID> reversal = cq.subquery(UUID.class);
                Root<Payment> r = reversal.from(Payment.class);
                reversal.select(r.get("id")).where(cb.equal(r.get("reversedPayment"), root));
                predicates.add(cb.equal(root.get("entryType"), PaymentEntryType.RECEIPT));
                predicates.add(f.state() == State.REVERSED ? cb.exists(reversal) : cb.not(cb.exists(reversal)));
            }
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paymentDate"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("paymentDate"), f.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
