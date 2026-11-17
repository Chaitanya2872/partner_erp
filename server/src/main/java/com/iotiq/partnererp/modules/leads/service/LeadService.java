package com.iotiq.partnererp.modules.leads.service;

import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.common.enums.PartnerStatus;
import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.auth.service.DataScopeService;
import com.iotiq.partnererp.common.web.ArchiveRequest;
import com.iotiq.partnererp.modules.leads.dto.LeadArchiveDetail;
import com.iotiq.partnererp.modules.leads.dto.LeadArchiveEntry;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;
import com.iotiq.partnererp.modules.leads.dto.LeadHistoryEntry;
import com.iotiq.partnererp.modules.leads.dto.LeadMeta;
import com.iotiq.partnererp.modules.leads.dto.LeadRequest;
import com.iotiq.partnererp.modules.leads.dto.LeadResponse;
import com.iotiq.partnererp.modules.leads.dto.LeadStageRequest;
import com.iotiq.partnererp.modules.leads.dto.LeadSummary;
import com.iotiq.partnererp.modules.leads.dto.SlaStatus;
import com.iotiq.partnererp.modules.leads.entity.Lead;
import com.iotiq.partnererp.modules.leads.entity.LeadEventType;
import com.iotiq.partnererp.modules.leads.entity.LeadStageHistory;
import com.iotiq.partnererp.modules.leads.entity.ProjectType;
import com.iotiq.partnererp.modules.leads.mapper.LeadMapper;
import com.iotiq.partnererp.modules.leads.repository.LeadMetricsRepository;
import com.iotiq.partnererp.modules.leads.repository.LeadRepository;
import com.iotiq.partnererp.modules.leads.repository.LeadStageHistoryRepository;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import com.iotiq.partnererp.modules.partners.repository.PartnerRepository;
import com.iotiq.partnererp.modules.sequences.service.DocumentNumberService;
import com.iotiq.partnererp.modules.settings.entity.CompanySettings;
import com.iotiq.partnererp.modules.settings.repository.CompanySettingsRepository;
import com.iotiq.partnererp.security.AuthenticatedUser;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Leads and deal registration (BUSINESS_RULES §4).
 *
 * <ul>
 *   <li>Stage changes are free between stages, except that moving a Won or Lost lead to another stage (reopening)
 *       needs {@code LEADS:APPROVE}. Every change is written to the stage history (D-04).</li>
 *   <li>The phone conflict check runs on every save and only warns: the lead is saved with {@code conflict=true}
 *       (BR-L6, D-05).</li>
 *   <li>Acknowledging locks the partner's current fee rate and starts protection (BR-L5).</li>
 *   <li>Delete is a soft delete, refused while documents point at the lead (D-26).</li>
 *   <li>Every lead gets a LEAD-YYYY-NNNNNN number on create; the client email is optional (D-16).</li>
 * </ul>
 */
@Service
public class LeadService {

    private static final BigDecimal MAX_BUDGET = new BigDecimal("999999999999.99");
    private static final Pattern PHONE_CHARS = Pattern.compile("[0-9+()\\-\\s]+");
    /** Same rule as the database check {@code ck_leads_client_email}. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final LeadRepository leadRepository;
    private final LeadStageHistoryRepository historyRepository;
    private final LeadMetricsRepository metrics;
    private final PartnerRepository partnerRepository;
    private final CompanySettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;
    private final DocumentNumberService numbers;
    private final Clock clock;

    public LeadService(LeadRepository leadRepository, LeadStageHistoryRepository historyRepository,
                       LeadMetricsRepository metrics, PartnerRepository partnerRepository,
                       CompanySettingsRepository settingsRepository, UserRepository userRepository,
                       DataScopeService dataScopeService, AuditService auditService, DocumentNumberService numbers,
                       Clock clock) {
        this.leadRepository = leadRepository;
        this.historyRepository = historyRepository;
        this.metrics = metrics;
        this.partnerRepository = partnerRepository;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
        this.numbers = numbers;
        this.clock = clock;
    }

    /** List filters; any may be null. */
    public record Filters(String query, LeadStage stage, UUID partnerId, Boolean conflict, Boolean slaBreach) {
    }

    // ------------------------------------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<LeadSummary> search(Filters filters, Pageable pageable, AuthenticatedUser user) {
        LeadSla.Targets targets = targets();
        Specification<Lead> spec = dataScopeService.resolve(user, ModuleCode.LEADS).<Lead>toSpecification()
                .and(matching(filters, false));
        if (filters.slaBreach() != null) {
            Set<UUID> breached = metrics.slaBreachedIds(clock.instant(), clock.getZone(), targets.ackHours(),
                    targets.estimateDays());
            spec = spec.and(idIn(breached, filters.slaBreach()));
        }
        Page<Lead> page = leadRepository.findAll(spec, pageable);
        Map<UUID, LocalDate> estimates = metrics.earliestEstimateDates(page.getContent().stream().map(Lead::getId).toList());
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        return page.map(l -> LeadMapper.toSummary(l, sla(l, estimates.get(l.getId()), targets, now, today), today));
    }

    @Transactional(readOnly = true)
    public LeadResponse get(UUID id, AuthenticatedUser user) {
        Lead lead = load(id, user);
        return toResponse(lead, protectingPartner(lead));
    }

    /** The archive: soft-deleted leads, newest archived first, searched like the main list; same scope rules. */
    @Transactional(readOnly = true)
    public Page<LeadArchiveEntry> archive(String query, Pageable pageable, AuthenticatedUser user) {
        Specification<Lead> spec = dataScopeService.resolve(user, ModuleCode.LEADS).<Lead>toSpecification()
                .and(matching(new Filters(query, null, null, null, null), true));
        Page<Lead> page = leadRepository.findAll(spec, pageable);
        Map<UUID, String> names = actorNames(page.getContent().stream().map(Lead::getDeletedBy).toList());
        return page.map(l -> archiveEntry(l, names.get(l.getDeletedBy())));
    }

    /** One archived lead, read-only, with the reason, who archived it and when. */
    @Transactional(readOnly = true)
    public LeadArchiveDetail archived(UUID id, AuthenticatedUser user) {
        requireScope(id, user);
        Lead lead = leadRepository.findByIdAndDeletedAtIsNotNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Archived lead", id));
        return new LeadArchiveDetail(toResponse(lead, null), lead.getDeletedAt(),
                actorNames(List.of(lead.getDeletedBy())).get(lead.getDeletedBy()), lead.getDeleteReason());
    }

    private static LeadArchiveEntry archiveEntry(Lead l, String deletedByName) {
        return new LeadArchiveEntry(l.getId(), l.getLeadNumber(), l.getClientName(), l.getClientPhone(), l.getCity(),
                l.getProjectTypeDisplay(), l.getStage(), l.getStage().getLabel(),
                new PartnerRef(l.getPartner().getId(), l.getPartner().getName()), l.getCreatedAt(), l.getDeletedAt(),
                deletedByName, l.getDeleteReason());
    }

    private Map<UUID, String> actorNames(List<UUID> ids) {
        Set<UUID> actorIds = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    @Transactional(readOnly = true)
    public List<LeadHistoryEntry> history(UUID id, AuthenticatedUser user) {
        load(id, user);
        List<LeadStageHistory> entries = historyRepository.findByLeadIdOrderByOccurredAtAsc(id);
        Set<UUID> actorIds = entries.stream().map(LeadStageHistory::getActorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return entries.stream().map(h -> new LeadHistoryEntry(h.getId(), h.getEvent(), h.getEvent().getLabel(),
                h.getFromStage(), h.getFromStage() == null ? null : h.getFromStage().getLabel(), h.getToStage(),
                h.getToStage() == null ? null : h.getToStage().getLabel(), h.getReason(), h.getActorId(),
                h.getActorId() == null ? null : names.get(h.getActorId()), h.getOccurredAt())).toList();
    }

    @Transactional(readOnly = true)
    public LeadMeta meta() {
        CompanySettings settings = settings();
        return new LeadMeta(
                Arrays.stream(LeadStage.values()).map(s -> new LeadMeta.Option(s.name(), s.getLabel())).toList(),
                Arrays.stream(ProjectType.values()).map(t -> new LeadMeta.Option(t.name(), t.getLabel())).toList(),
                partnerRepository.findAllByOrderByNameAsc().stream().map(p -> new LeadMeta.PartnerOption(p.getId(),
                        p.getName(), p.getStatus().getLabel(), p.getStatus() != PartnerStatus.INACTIVE,
                        p.getTier().getName(), p.getTier().getCommissionPercent())).toList(),
                settings.getAcknowledgementHours(), settings.getEstimateSlaDays(), settings.getLeadProtectionDays());
    }

    // ------------------------------------------------------------------------------------------- commands

    @Transactional
    public LeadResponse create(LeadRequest request, AuthenticatedUser user) {
        Normalized input = normalize(request);
        Partner partner = partner(input.partnerId());
        Lead lead = new Lead();
        lead.setLeadNumber(numbers.next(DocumentNumberService.LEAD));
        apply(lead, input, partner);
        lead.setStage(input.stage() == null ? LeadStage.NEW : input.stage());
        String protecting = protectingPartner(lead.getClientPhone(), partner.getId(), null);
        lead.setConflict(protecting != null);
        leadRepository.saveAndFlush(lead);
        history(lead, LeadEventType.CREATED, null, lead.getStage(), input.stageReason(), user);
        auditService.record(AuditActions.LEAD_CREATED, AuditActions.ENTITY_LEAD, lead.getId(), null,
                LeadMapper.auditSnapshot(lead));
        return toResponse(lead, protecting);
    }

    @Transactional
    public LeadResponse update(UUID id, LeadRequest request, AuthenticatedUser user) {
        Lead lead = load(id, user);
        Normalized input = normalize(request);
        if (request.version() == null) {
            throw ValidationException.of("version", "Version is required.");
        }
        if (request.version() != lead.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Lead.class, id);
        }
        Partner partner = partner(input.partnerId());
        if (lead.getAcknowledgedAt() != null && !partner.getId().equals(lead.getPartner().getId())) {
            throw ValidationException.of("partnerId", "The partner can't change after the registration is acknowledged.");
        }
        LeadStage from = lead.getStage();
        LeadStage to = input.stage() == null ? from : input.stage();
        if (to != from) {
            checkTransition(from, to, user);
        }

        Map<String, Object> before = LeadMapper.auditSnapshot(lead);
        apply(lead, input, partner);
        lead.setStage(to);
        String protecting = protectingPartner(lead.getClientPhone(), partner.getId(), id);
        lead.setConflict(protecting != null);
        Map<String, Object> after = LeadMapper.auditSnapshot(lead);
        Set<String> changedKeys = new LinkedHashSet<>();
        before.keySet().forEach(k -> {
            if (!Objects.equals(before.get(k), after.get(k))) {
                changedKeys.add(k);
            }
        });
        if (!changedKeys.isEmpty()) {
            leadRepository.saveAndFlush(lead);
            if (to != from) {
                history(lead, LeadEventType.STAGE_CHANGED, from, to, input.stageReason(), user);
            }
            auditService.record(AuditActions.LEAD_UPDATED, AuditActions.ENTITY_LEAD, id,
                    only(before, changedKeys), only(after, changedKeys));
        }
        return toResponse(lead, protecting);
    }

    @Transactional
    public LeadResponse changeStage(UUID id, LeadStageRequest request, AuthenticatedUser user) {
        Lead lead = lock(id, user);
        if (request.version() != lead.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Lead.class, id);
        }
        String reason = reason(request.reason());
        LeadStage from = lead.getStage();
        if (from != request.stage()) {
            checkTransition(from, request.stage(), user);
            lead.setStage(request.stage());
            leadRepository.saveAndFlush(lead);
            history(lead, LeadEventType.STAGE_CHANGED, from, request.stage(), reason, user);
            auditService.record(AuditActions.LEAD_STAGE_CHANGED, AuditActions.ENTITY_LEAD, id,
                    AuditService.values("stage", from), AuditService.values("stage", request.stage(), "reason", reason));
        }
        return toResponse(lead, protectingPartner(lead));
    }

    /** BR-L5. Callers need {@code LEADS:APPROVE}; open leads only (422 for Won/Lost); acknowledged once (409 afterwards). */
    @Transactional
    public LeadResponse acknowledge(UUID id, AuthenticatedUser user) {
        Lead lead = lock(id, user);
        if (lead.getAcknowledgedAt() != null) {
            throw BusinessException.conflict("LEAD_ALREADY_ACKNOWLEDGED", "This lead is already acknowledged.");
        }
        // P6-13: a closed lead is reopened first (D-04), so the fee lock and protection start on an open deal.
        if (!LeadSla.isOpen(lead.getStage())) {
            throw new BusinessException("LEAD_CLOSED",
                    "Won and Lost leads can't be acknowledged. Reopen the lead first.");
        }
        LeadStage from = lead.getStage();
        lead.setAcknowledgedAt(clock.instant());
        lead.setAcknowledgedBy(user.id());
        lead.setLockedFeePercent(lead.getPartner().getTier().getCommissionPercent());
        lead.setProtectionUntil(LocalDate.now(clock).plusDays(settings().getLeadProtectionDays()));
        if (from == LeadStage.NEW) {
            lead.setStage(LeadStage.REGISTERED);
        }
        leadRepository.saveAndFlush(lead);
        history(lead, LeadEventType.ACKNOWLEDGED, from, lead.getStage(), null, user);
        auditService.record(AuditActions.LEAD_ACKNOWLEDGED, AuditActions.ENTITY_LEAD, id,
                AuditService.values("stage", from),
                AuditService.values("stage", lead.getStage(), "lockedFeePercent", lead.getLockedFeePercent(),
                        "protectionUntil", lead.getProtectionUntil()));
        return toResponse(lead, protectingPartner(lead));
    }

    /**
     * D-26: archive (soft delete) with a mandatory reason, refused while estimates, invoices, payments or internal
     * documents point at the lead. Nothing is removed; the lead moves to the archive and can be restored.
     */
    @Transactional
    public void delete(UUID id, ArchiveRequest request, AuthenticatedUser user) {
        String reason = ArchiveRequest.requireReason(request);
        Lead lead = lock(id, user);
        if (metrics.hasDocuments(id)) {
            throw BusinessException.conflict("LEAD_HAS_DOCUMENTS",
                    "This lead has estimates, invoices or payments, so it can't be deleted. Mark it Lost instead.");
        }
        Map<String, Object> before = LeadMapper.auditSnapshot(lead);
        lead.setDeletedAt(clock.instant());
        lead.setDeletedBy(user.id());
        lead.setDeleteReason(reason);
        leadRepository.saveAndFlush(lead);
        history(lead, LeadEventType.ARCHIVED, lead.getStage(), lead.getStage(), reason, user);
        auditService.record(AuditActions.LEAD_DELETED, AuditActions.ENTITY_LEAD, id, before,
                AuditService.values("reason", reason));
    }

    /**
     * Brings an archived lead back. The phone is checked again against protected leads of other partners; as for any
     * save that only sets the {@code conflict} warning (BR-L6), it never blocks the restore.
     */
    @Transactional
    public LeadResponse restore(UUID id, AuthenticatedUser user) {
        requireScope(id, user);
        Lead lead = leadRepository.lockArchived(id).orElseThrow(() -> new ResourceNotFoundException("Archived lead", id));
        String reason = lead.getDeleteReason();
        lead.setDeletedAt(null);
        lead.setDeletedBy(null);
        lead.setDeleteReason(null);
        String protecting = protectingPartner(lead.getClientPhone(), lead.getPartner().getId(), id);
        lead.setConflict(protecting != null);
        leadRepository.saveAndFlush(lead);
        history(lead, LeadEventType.RESTORED, lead.getStage(), lead.getStage(), null, user);
        auditService.record(AuditActions.LEAD_RESTORED, AuditActions.ENTITY_LEAD, id,
                AuditService.values("archiveReason", reason), AuditService.values("restored", true));
        return toResponse(lead, protecting);
    }

    /**
     * Automatic stage change caused by a document (BUSINESS_RULES §4.3), e.g. an estimate marked sent. {@code rule}
     * maps the current stage to the new one. It runs in the caller's transaction and needs no Leads permission,
     * except that reopening a Won or Lost lead still needs {@code LEADS:APPROVE} (D-04). Deleted leads are skipped.
     * Writes an AUTO_STAGE history entry and a LEAD_STAGE_CHANGED audit entry with {@code reason}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyAutomaticStage(UUID leadId, UnaryOperator<LeadStage> rule, String reason, AuthenticatedUser user) {
        Lead lead = leadRepository.lockLive(leadId).orElse(null);
        if (lead == null) {
            return;
        }
        LeadStage from = lead.getStage();
        LeadStage to = rule.apply(from);
        if (to == from) {
            return;
        }
        checkTransition(from, to, user);
        lead.setStage(to);
        leadRepository.saveAndFlush(lead);
        history(lead, LeadEventType.AUTO_STAGE, from, to, reason, user);
        auditService.record(AuditActions.LEAD_STAGE_CHANGED, AuditActions.ENTITY_LEAD, leadId,
                AuditService.values("stage", from), AuditService.values("stage", to, "reason", reason));
    }

    // ------------------------------------------------------------------------------------------- helpers

    /** D-04: any stage to any stage, except that leaving Won or Lost needs {@code LEADS:APPROVE}. */
    static void checkTransition(LeadStage from, LeadStage to, AuthenticatedUser user) {
        if (!LeadSla.isOpen(from) && from != to && !canApprove(user)) {
            throw new BusinessException("STAGE_REOPEN_NOT_PERMITTED",
                    "Reopening a Won or Lost lead needs the Leads approve permission.", HttpStatus.FORBIDDEN);
        }
    }

    static boolean canApprove(AuthenticatedUser user) {
        return user.permissions().canPerform(ModuleCode.LEADS.name(), "APPROVE");
    }

    /** Last ten digits of a phone number, as the database computes {@code client_phone_digits}. */
    static String phoneDigits(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    private Lead load(UUID id, AuthenticatedUser user) {
        Lead lead = leadRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Lead", id));
        requireScope(id, user);
        return lead;
    }

    private Lead lock(UUID id, AuthenticatedUser user) {
        requireScope(id, user);
        return leadRepository.lockLive(id).orElseThrow(() -> new ResourceNotFoundException("Lead", id));
    }

    /** Records outside the caller's data scope are reported as missing, like records that do not exist. */
    private void requireScope(UUID id, AuthenticatedUser user) {
        if (!dataScopeService.resolve(user, ModuleCode.LEADS).unrestricted()) {
            throw new ResourceNotFoundException("Lead", id);
        }
    }

    private Partner partner(UUID id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> ValidationException.of("partnerId", "Choose the referring partner."));
    }

    private String protectingPartner(Lead lead) {
        return protectingPartner(lead.getClientPhone(), lead.getPartner().getId(), lead.getId());
    }

    private String protectingPartner(String phone, UUID partnerId, UUID excludeLeadId) {
        String digits = phoneDigits(phone);
        if (digits.isEmpty()) {
            return null;
        }
        return metrics.protectingPartnerName(digits, partnerId, excludeLeadId, LocalDate.now(clock)).orElse(null);
    }

    private void history(Lead lead, LeadEventType event, LeadStage from, LeadStage to, String reason,
                         AuthenticatedUser user) {
        LeadStageHistory entry = new LeadStageHistory();
        entry.setLead(lead);
        entry.setEvent(event);
        entry.setFromStage(from);
        entry.setToStage(to);
        entry.setReason(reason);
        entry.setActorId(user.id());
        entry.setOccurredAt(clock.instant());
        historyRepository.save(entry);
    }

    private LeadResponse toResponse(Lead lead, String conflictPartnerName) {
        LeadSla.Targets targets = targets();
        LocalDate today = LocalDate.now(clock);
        LocalDate estimate = metrics.earliestEstimateDates(List.of(lead.getId())).get(lead.getId());
        String ackBy = lead.getAcknowledgedBy() == null ? null
                : userRepository.findById(lead.getAcknowledgedBy()).map(User::getFullName).orElse(null);
        return LeadMapper.toResponse(lead, sla(lead, estimate, targets, clock.instant(), today), today, ackBy,
                conflictPartnerName);
    }

    private SlaStatus sla(Lead lead, LocalDate earliestEstimate, LeadSla.Targets targets, Instant now, LocalDate today) {
        LeadSla.Result result = LeadSla.evaluate(lead.getCreatedAt(), lead.getAcknowledgedAt(), lead.getStage(),
                earliestEstimate, now, clock.getZone(), targets);
        return LeadMapper.toSla(result, targets, lead, today);
    }

    private LeadSla.Targets targets() {
        CompanySettings settings = settings();
        return new LeadSla.Targets(settings.getAcknowledgementHours(), settings.getEstimateSlaDays());
    }

    private CompanySettings settings() {
        return settingsRepository.findSingleton().orElseThrow(() -> new IllegalStateException("Company settings are missing"));
    }

    private static void apply(Lead lead, Normalized input, Partner partner) {
        lead.setClientName(input.clientName());
        lead.setClientPhone(input.clientPhone());
        lead.setClientEmail(input.clientEmail());
        lead.setCity(input.city());
        lead.setPartner(partner);
        lead.setProjectType(input.projectType());
        lead.setProjectTypeOther(input.projectTypeOther());
        lead.setUnits(input.units());
        lead.setBudget(input.budget());
        lead.setPresalesOwner(input.presalesOwner());
        lead.setExpectedClose(input.expectedClose());
        lead.setSource(input.source());
        lead.setNotes(input.notes());
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

    private static Specification<Lead> matching(Filters f, boolean archived) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(archived ? cb.isNotNull(root.get("deletedAt")) : cb.isNull(root.get("deletedAt")));
            boolean counting = cq != null && Long.class == cq.getResultType();
            // One join to the partner serves the search and, for page queries, fetches it for the rows.
            @SuppressWarnings("unchecked")
            From<Lead, Partner> partner = counting ? root.join("partner", JoinType.INNER)
                    : (From<Lead, Partner>) (Object) root.<Lead, Partner>fetch("partner", JoinType.INNER);
            if (f.query() != null && !f.query().isBlank()) {
                String q = f.query().trim().toLowerCase(Locale.ROOT);
                String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("clientName")), like, '\\'),
                        cb.like(cb.lower(root.get("leadNumber")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("clientEmail"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("city"), "")), like, '\\'),
                        cb.like(cb.lower(partner.get("name")), like, '\\')));
            }
            if (f.stage() != null) {
                predicates.add(cb.equal(root.get("stage"), f.stage()));
            }
            if (f.partnerId() != null) {
                predicates.add(cb.equal(partner.get("id"), f.partnerId()));
            }
            if (f.conflict() != null) {
                predicates.add(cb.equal(root.get("conflict"), f.conflict()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Specification<Lead> idIn(Set<UUID> ids, boolean inside) {
        return (root, cq, cb) -> {
            if (ids.isEmpty()) {
                return inside ? cb.disjunction() : cb.conjunction();
            }
            return inside ? root.get("id").in(ids) : cb.not(root.get("id").in(ids));
        };
    }

    // ------------------------------------------------------------------------------------------- validation

    /** Trimmed input; blanks become null. */
    record Normalized(String clientName, String clientPhone, String clientEmail, String city, UUID partnerId, ProjectType projectType,
                      String projectTypeOther, Integer units, BigDecimal budget, LeadStage stage, String stageReason, String presalesOwner,
                      LocalDate expectedClose, String source, String notes) {
    }

    private Normalized normalize(LeadRequest r) {
        List<FieldError> errors = new ArrayList<>();
        String clientName = text(r.clientName(), "clientName", 200, errors);
        if (clientName == null) {
            errors.add(new FieldError("clientName", "Client name is required."));
        }
        String phone = text(r.clientPhone(), "clientPhone", 30, errors);
        if (phone != null) {
            long digits = phone.chars().filter(Character::isDigit).count();
            if (!PHONE_CHARS.matcher(phone).matches() || digits < 7 || digits > 15) {
                errors.add(new FieldError("clientPhone", "Enter a valid phone number."));
            }
        }
        String email = text(r.clientEmail(), "clientEmail", 254, errors);
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
            if (!EMAIL.matcher(email).matches()) {
                errors.add(new FieldError("clientEmail", "Enter a valid email address, or leave it blank."));
            }
        }
        String city = text(r.city(), "city", 150, errors);
        if (r.partnerId() == null) {
            errors.add(new FieldError("partnerId", "Choose the referring partner."));
        }
        if (r.projectType() == null) {
            errors.add(new FieldError("projectType", "Choose a project type."));
        }
        String projectTypeOther = text(r.projectTypeOther(), "projectTypeOther", 100, errors);
        if (r.projectType() == ProjectType.OTHER && projectTypeOther == null) {
            errors.add(new FieldError("projectTypeOther", "Say what kind of project it is."));
        }
        if (r.projectType() != ProjectType.OTHER) {
            projectTypeOther = null;
        }
        if (r.units() != null && r.units() < 0) {
            errors.add(new FieldError("units", "Units can't be negative."));
        }
        BigDecimal budget = r.budget();
        if (budget != null) {
            if (budget.signum() < 0) {
                errors.add(new FieldError("budget", "Budget can't be negative."));
            } else if (budget.scale() > 2 || budget.compareTo(MAX_BUDGET) > 0) {
                errors.add(new FieldError("budget", "Enter an amount in rupees with at most 2 decimal places."));
            }
        }
        String stageReason = text(r.stageReason(), "stageReason", 500, errors);
        String presalesOwner = text(r.presalesOwner(), "presalesOwner", 150, errors);
        String source = text(r.source(), "source", 200, errors);
        String notes = text(r.notes(), "notes", 2000, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Normalized(clientName, phone, email, city, r.partnerId(), r.projectType(), projectTypeOther, r.units(), budget, r.stage(),
                stageReason, presalesOwner, r.expectedClose(), source, notes);
    }

    private static String reason(String value) {
        List<FieldError> errors = new ArrayList<>();
        String reason = text(value, "reason", 500, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return reason;
    }

    private static String text(String value, String field, int max, List<FieldError> errors) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            errors.add(new FieldError(field, "Must be at most " + max + " characters."));
        }
        return trimmed;
    }
}
