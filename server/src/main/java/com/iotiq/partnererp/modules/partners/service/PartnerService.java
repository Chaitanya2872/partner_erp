package com.iotiq.partnererp.modules.partners.service;

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
import com.iotiq.partnererp.modules.partners.dto.PartnerMeta;
import com.iotiq.partnererp.modules.partners.dto.PartnerPerformance;
import com.iotiq.partnererp.modules.partners.dto.PartnerRequest;
import com.iotiq.partnererp.modules.partners.dto.PartnerResponse;
import com.iotiq.partnererp.modules.partners.dto.PartnerStatusRequest;
import com.iotiq.partnererp.modules.partners.dto.PartnerSummary;
import com.iotiq.partnererp.modules.partners.dto.TierRef;
import com.iotiq.partnererp.modules.partners.dto.TierReviewDtos.HistoryEntry;
import com.iotiq.partnererp.modules.partners.entity.AgreementStatus;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import com.iotiq.partnererp.modules.partners.entity.PartnerCategory;
import com.iotiq.partnererp.modules.partners.entity.PartnerTierReview;
import com.iotiq.partnererp.modules.partners.mapper.PartnerMapper;
import com.iotiq.partnererp.modules.partners.repository.PartnerMetricsRepository;
import com.iotiq.partnererp.modules.partners.repository.PartnerMetricsRepository.FeeTotals;
import com.iotiq.partnererp.modules.partners.repository.PartnerRepository;
import com.iotiq.partnererp.modules.partners.repository.PartnerTierReviewRepository;
import com.iotiq.partnererp.modules.settings.entity.PartnerTier;
import com.iotiq.partnererp.modules.settings.repository.PartnerTierRepository;
import com.iotiq.partnererp.security.AuthenticatedUser;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Partner master (BUSINESS_RULES §3). Partners are never deleted (BR-P10); "deactivate" is the Inactive status.
 * Fields that change commission rates (tier, programme) need {@code PARTNERS:APPROVE} on top of Write.
 */
@Service
public class PartnerService {

    /** BR-P12: the revenue window is the 365 calendar days before today. */
    public static final int REVENUE_WINDOW_DAYS = 365;
    public static final String DEFAULT_AGREEMENT_VERSION = "v2.1";
    public static final String SENSITIVE_ACTION = "VIEW_PARTNER_SENSITIVE";
    private static final List<String> SENSITIVE_FIELDS = List.of("pan", "gstin", "bankAccount");

    private static final Pattern PAN = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");
    private static final Pattern GSTIN = Pattern.compile("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]");
    private static final Pattern IFSC = Pattern.compile("[A-Z]{4}0[A-Z0-9]{6}");
    private static final Pattern BANK_ACCOUNT = Pattern.compile("[0-9]{9,18}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PHONE_CHARS = Pattern.compile("[0-9+()\\-\\s]+");

    private final PartnerRepository partnerRepository;
    private final PartnerTierRepository tierRepository;
    private final PartnerTierReviewRepository reviewRepository;
    private final PartnerMetricsRepository metrics;
    private final UserRepository userRepository;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;
    private final Clock clock;

    public PartnerService(PartnerRepository partnerRepository, PartnerTierRepository tierRepository,
                          PartnerTierReviewRepository reviewRepository, PartnerMetricsRepository metrics,
                          UserRepository userRepository, DataScopeService dataScopeService, AuditService auditService,
                          Clock clock) {
        this.partnerRepository = partnerRepository;
        this.tierRepository = tierRepository;
        this.reviewRepository = reviewRepository;
        this.metrics = metrics;
        this.userRepository = userRepository;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** List filters; any may be null. */
    public record Filters(String query, PartnerStatus status, PartnerCategory category, Integer tierLevel,
                          AgreementStatus agreementStatus) {
    }

    // ------------------------------------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<PartnerSummary> search(Filters filters, Pageable pageable, AuthenticatedUser user) {
        Specification<Partner> spec = dataScopeService.resolve(user, ModuleCode.PARTNERS).<Partner>toSpecification()
                .and(matching(filters));
        Page<Partner> page = partnerRepository.findAll(spec, pageable);
        List<UUID> ids = page.getContent().stream().map(Partner::getId).toList();
        Map<UUID, BigDecimal> revenue = metrics.paidRevenueSince(ids, revenueSince());
        Map<UUID, Long> leads = metrics.leadCounts(ids);
        TierTable tiers = tierTable();
        return page.map(p -> {
            BigDecimal rev = revenueOf(revenue, p.getId());
            return PartnerMapper.toSummary(p, tiers.calculated(rev, p.isStrategicProgramme()), rev,
                    leads.getOrDefault(p.getId(), 0L));
        });
    }

    @Transactional(readOnly = true)
    public PartnerResponse get(UUID id, AuthenticatedUser user) {
        return toResponse(load(id, user), user);
    }

    @Transactional(readOnly = true)
    public PartnerPerformance performance(UUID id, AuthenticatedUser user) {
        Partner partner = load(id, user);
        LocalDate since = revenueSince();
        BigDecimal revenue = revenueOf(metrics.paidRevenueSince(List.of(id), since), id);
        TierTable tiers = tierTable();
        int current = partner.getTier().getTierLevel();
        TierRules.Threshold next = TierRules.nextTier(tiers.thresholds(), current).orElse(null);
        FeeTotals fees = metrics.feeTotals(id);
        return new PartnerPerformance(TierRef.of(partner.getTier()),
                tiers.calculated(revenue, partner.isStrategicProgramme()), revenue, since,
                next == null ? null : tiers.ref(next.level()),
                next == null ? null : next.minimumRevenue(),
                next == null ? null : TierRules.progressPercent(revenue, next.minimumRevenue()),
                next == null ? null : TierRules.remaining(revenue, next.minimumRevenue()),
                fees.unpaid(), fees.paid(), metrics.leadCounts(List.of(id)).getOrDefault(id, 0L),
                partner.getAgreementStatus(), partner.getPan() != null && partner.getBankAccount() != null);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> tierHistory(UUID id, AuthenticatedUser user) {
        load(id, user);
        List<PartnerTierReview> reviews = reviewRepository.findByPartnerIdOrderByReviewedAtDesc(id);
        Set<UUID> actorIds = reviews.stream().map(PartnerTierReview::getActorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return reviews.stream().map(r -> new HistoryEntry(r.getId(), r.getAction(), r.getAction().getLabel(),
                r.getFromLevel(), r.getToLevel(), r.getRevenue12m(), r.isProgramme(), r.getActorId(),
                r.getActorId() == null ? null : names.get(r.getActorId()), r.getReviewedAt())).toList();
    }

    @Transactional(readOnly = true)
    public PartnerMeta meta() {
        return new PartnerMeta(
                Arrays.stream(PartnerCategory.values()).map(c -> new PartnerMeta.Option(c.name(), c.getLabel())).toList(),
                Arrays.stream(PartnerStatus.values()).map(s -> new PartnerMeta.Option(s.name(), s.getLabel())).toList(),
                Arrays.stream(AgreementStatus.values()).map(a -> new PartnerMeta.Option(a.name(), a.getLabel())).toList(),
                tierRepository.findAllByOrderByTierLevelAsc().stream().map(t -> new PartnerMeta.Tier(t.getTierLevel(),
                        t.getName(), t.getCommissionPercent(), t.getMinimumRevenue(), t.getPayoutBusinessDays())).toList(),
                DEFAULT_AGREEMENT_VERSION);
    }

    // ------------------------------------------------------------------------------------------- commands

    @Transactional
    public PartnerResponse create(PartnerRequest request, AuthenticatedUser user) {
        Normalized input = normalize(request, true);
        int level = input.tierLevel() == null ? TierRules.MIN_LEVEL : input.tierLevel();
        boolean programme = Boolean.TRUE.equals(input.programme());
        if ((level != TierRules.MIN_LEVEL || programme) && !canApprove(user)) {
            throw tierChangeDenied();
        }
        Partner partner = new Partner();
        apply(partner, input);
        partner.setStatus(input.status() == null ? PartnerStatus.PROSPECT : input.status());
        partner.setAgreementStatus(input.agreementStatus() == null ? AgreementStatus.NOT_SENT : input.agreementStatus());
        partner.setAgreementVersion(input.agreementVersion() == null ? DEFAULT_AGREEMENT_VERSION : input.agreementVersion());
        partner.setStrategicProgramme(programme);
        // BR-P5: the programme forces Strategic on save.
        partner.setTier(tier(programme ? TierRules.PROGRAMME_LEVEL : level));
        partner.setPan(emptyToNull(input.pan()));
        partner.setGstin(emptyToNull(input.gstin()));
        partner.setBankAccount(emptyToNull(input.bankAccount()));
        partnerRepository.saveAndFlush(partner);
        auditService.record(AuditActions.PARTNER_CREATED, AuditActions.ENTITY_PARTNER, partner.getId(), null,
                PartnerMapper.auditSnapshot(partner));
        return toResponse(partner, user);
    }

    @Transactional
    public PartnerResponse update(UUID id, PartnerRequest request, AuthenticatedUser user) {
        Partner partner = load(id, user);
        Normalized input = normalize(request, false);
        if (request.version() == null) {
            throw ValidationException.of("version", "Version is required.");
        }
        if (request.version() != partner.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Partner.class, id);
        }
        int currentLevel = partner.getTier().getTierLevel();
        int level = input.tierLevel() == null ? currentLevel : input.tierLevel();
        boolean programme = input.programme() == null ? partner.isStrategicProgramme() : input.programme();
        if ((level != currentLevel || programme != partner.isStrategicProgramme()) && !canApprove(user)) {
            throw tierChangeDenied();
        }

        Map<String, Object> before = PartnerMapper.auditSnapshot(partner);
        List<String> sensitiveBefore = sensitiveValues(partner);
        apply(partner, input);
        if (input.status() != null) {
            partner.setStatus(input.status());
        }
        if (input.agreementStatus() != null) {
            partner.setAgreementStatus(input.agreementStatus());
        }
        partner.setAgreementVersion(input.agreementVersion());
        boolean programmeChanged = programme != partner.isStrategicProgramme();
        partner.setStrategicProgramme(programme);
        int newLevel = programme ? TierRules.PROGRAMME_LEVEL : level;
        if (newLevel != currentLevel) {
            partner.setTier(tier(newLevel));
        }
        // A grace period belongs to the tier it was granted on (§3.1); a manual tier or programme change starts afresh.
        if (newLevel != currentLevel || programmeChanged) {
            partner.setDowngradeGrace(false);
        }
        if (input.pan() != null) {
            partner.setPan(emptyToNull(input.pan()));
        }
        if (input.gstin() != null) {
            partner.setGstin(emptyToNull(input.gstin()));
        }
        if (input.bankAccount() != null) {
            partner.setBankAccount(emptyToNull(input.bankAccount()));
        }
        Map<String, Object> after = PartnerMapper.auditSnapshot(partner);
        // Masked values can be equal although the stored value changed (same last characters), so the sensitive
        // fields are compared unmasked; the audit row still records only the masked forms.
        Set<String> changedKeys = new LinkedHashSet<>();
        before.keySet().forEach(k -> {
            if (!Objects.equals(before.get(k), after.get(k))) {
                changedKeys.add(k);
            }
        });
        List<String> sensitiveAfter = sensitiveValues(partner);
        for (int i = 0; i < SENSITIVE_FIELDS.size(); i++) {
            if (!Objects.equals(sensitiveBefore.get(i), sensitiveAfter.get(i))) {
                changedKeys.add(SENSITIVE_FIELDS.get(i));
            }
        }
        if (!changedKeys.isEmpty()) {
            partnerRepository.saveAndFlush(partner);
            auditService.record(AuditActions.PARTNER_UPDATED, AuditActions.ENTITY_PARTNER, id,
                    only(before, changedKeys), only(after, changedKeys));
        }
        return toResponse(partner, user);
    }

    @Transactional
    public PartnerResponse changeStatus(UUID id, PartnerStatusRequest request, AuthenticatedUser user) {
        Partner partner = load(id, user);
        if (request.version() != partner.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Partner.class, id);
        }
        PartnerStatus from = partner.getStatus();
        if (from != request.status()) {
            partner.setStatus(request.status());
            partnerRepository.saveAndFlush(partner);
            auditService.record(AuditActions.PARTNER_STATUS_CHANGED, AuditActions.ENTITY_PARTNER, id,
                    AuditService.values("status", from), AuditService.values("status", request.status()));
        }
        return toResponse(partner, user);
    }

    // ------------------------------------------------------------------------------------------- helpers

    /** Today in the business time zone minus 365 days (BR-P12, D-24). */
    public LocalDate revenueSince() {
        return LocalDate.now(clock).minusDays(REVENUE_WINDOW_DAYS);
    }

    TierTable tierTable() {
        return new TierTable(tierRepository.findAllByOrderByTierLevelAsc());
    }

    Partner load(UUID id, AuthenticatedUser user) {
        Partner partner = partnerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Partner", id));
        // Records outside the caller's data scope are reported as missing, like records that do not exist.
        if (!dataScopeService.resolve(user, ModuleCode.PARTNERS).unrestricted()) {
            throw new ResourceNotFoundException("Partner", id);
        }
        return partner;
    }

    static boolean canApprove(AuthenticatedUser user) {
        return user.permissions().canPerform(ModuleCode.PARTNERS.name(), "APPROVE");
    }

    static boolean canSeeSensitive(AuthenticatedUser user) {
        return user.permissions().canPerform(ModuleCode.PARTNERS.name(), SENSITIVE_ACTION);
    }

    static BigDecimal revenueOf(Map<UUID, BigDecimal> revenue, UUID id) {
        return revenue.getOrDefault(id, BigDecimal.ZERO).setScale(2);
    }

    private PartnerResponse toResponse(Partner partner, AuthenticatedUser user) {
        BigDecimal revenue = revenueOf(metrics.paidRevenueSince(List.of(partner.getId()), revenueSince()), partner.getId());
        return PartnerMapper.toResponse(partner, tierTable().calculated(revenue, partner.isStrategicProgramme()),
                revenue, canSeeSensitive(user));
    }

    private PartnerTier tier(int level) {
        return tierRepository.findByTierLevel((short) level)
                .orElseThrow(() -> new IllegalStateException("Tier " + level + " is missing"));
    }

    private static BusinessException tierChangeDenied() {
        return new BusinessException("TIER_CHANGE_NOT_PERMITTED",
                "Changing a partner's tier or programme needs the Partners approve permission.", HttpStatus.FORBIDDEN);
    }

    private static void apply(Partner partner, Normalized input) {
        partner.setName(input.name());
        partner.setCategory(input.category());
        partner.setContactName(input.contactName());
        partner.setPhone(input.phone());
        partner.setEmail(input.email());
        partner.setCity(input.city());
        partner.setAgreementDate(input.agreementDate());
        partner.setLicenceNo(input.licenceNo());
        partner.setBankName(input.bankName());
        partner.setIfsc(input.ifsc());
        partner.setNotes(input.notes());
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

    /** Unmasked PAN, GSTIN and bank account, in {@link #SENSITIVE_FIELDS} order; for change detection only. */
    private static List<String> sensitiveValues(Partner partner) {
        return Arrays.asList(partner.getPan(), partner.getGstin(), partner.getBankAccount());
    }

    private static Specification<Partner> matching(Filters f) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.query() != null && !f.query().isBlank()) {
                String q = f.query().trim().toLowerCase(Locale.ROOT);
                String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                List<Predicate> any = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("city"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("contactName"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like, '\\')));
                // BR-P11: the search also covers the category, by its displayed label.
                List<PartnerCategory> categories = Arrays.stream(PartnerCategory.values())
                        .filter(c -> c.getLabel().toLowerCase(Locale.ROOT).contains(q)).toList();
                if (!categories.isEmpty()) {
                    any.add(root.get("category").in(categories));
                }
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.category() != null) {
                predicates.add(cb.equal(root.get("category"), f.category()));
            }
            if (f.tierLevel() != null) {
                predicates.add(cb.equal(root.join("tier", JoinType.INNER).get("tierLevel"), f.tierLevel().shortValue()));
            }
            if (f.agreementStatus() != null) {
                predicates.add(cb.equal(root.get("agreementStatus"), f.agreementStatus()));
            }
            if (cq != null && Long.class != cq.getResultType()) {
                root.fetch("tier", JoinType.INNER);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // ------------------------------------------------------------------------------------------- validation

    /** Trimmed, upper/lower-cased input; blanks become null (except PAN/bank, where "" means "remove"). */
    record Normalized(String name, PartnerCategory category, PartnerStatus status, String contactName, String phone,
                      String email, String city, Integer tierLevel, Boolean programme, AgreementStatus agreementStatus,
                      String agreementVersion, LocalDate agreementDate, String pan, String gstin, String licenceNo,
                      String bankName, String bankAccount, String ifsc, String notes) {
    }

    private Normalized normalize(PartnerRequest r, boolean creating) {
        List<FieldError> errors = new ArrayList<>();
        String name = text(r.name(), "name", 200, errors);
        if (name == null) {
            errors.add(new FieldError("name", "Name is required."));
        }
        if (r.category() == null) {
            errors.add(new FieldError("category", "Category is required."));
        }
        String contactName = text(r.contactName(), "contactName", 150, errors);
        String phone = text(r.phone(), "phone", 30, errors);
        if (phone != null) {
            long digits = phone.chars().filter(Character::isDigit).count();
            if (!PHONE_CHARS.matcher(phone).matches() || digits < 7 || digits > 15) {
                errors.add(new FieldError("phone", "Enter a valid phone number."));
            }
        }
        String email = lower(text(r.email(), "email", 254, errors));
        if (email != null && !EMAIL.matcher(email).matches()) {
            errors.add(new FieldError("email", "Enter a valid email address."));
        }
        String city = text(r.city(), "city", 100, errors);
        if (r.tierLevel() != null && (r.tierLevel() < TierRules.MIN_LEVEL || r.tierLevel() > TierRules.MAX_LEVEL)) {
            errors.add(new FieldError("tierLevel", "Tier must be between 1 and 4."));
        }
        String agreementVersion = text(r.agreementVersion(), "agreementVersion", 20, errors);
        if (agreementVersion == null && !creating) {
            agreementVersion = DEFAULT_AGREEMENT_VERSION;
        }
        if (r.agreementDate() != null && r.agreementDate().isAfter(LocalDate.now(clock))) {
            errors.add(new FieldError("agreementDate", "The agreement date can't be in the future."));
        }
        String pan = sensitive(r.pan(), "pan", PAN, "Enter a valid PAN (e.g. ABCDE1234F).", errors);
        String gstin = sensitive(r.gstin(), "gstin", GSTIN, "Enter a valid 15-character GSTIN.", errors);
        String licenceNo = text(r.licenceNo(), "licenceNo", 100, errors);
        String bankName = text(r.bankName(), "bankName", 150, errors);
        String bankAccount = r.bankAccount() == null ? null : r.bankAccount().replaceAll("[\\s-]", "");
        bankAccount = sensitive(bankAccount, "bankAccount", BANK_ACCOUNT, "Enter a bank account number of 9 to 18 digits.", errors);
        String ifsc = upper(text(r.ifsc(), "ifsc", 11, errors));
        if (ifsc != null && !IFSC.matcher(ifsc).matches()) {
            errors.add(new FieldError("ifsc", "Enter a valid IFSC (e.g. HDFC0001234)."));
        }
        String notes = text(r.notes(), "notes", 2000, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Normalized(name, r.category(), r.status(), contactName, phone, email, city, r.tierLevel(),
                r.strategicProgramme(), r.agreementStatus(), agreementVersion, r.agreementDate(), pan, gstin,
                licenceNo, bankName, bankAccount, ifsc, notes);
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

    /** null = keep, "" = remove, otherwise a validated, upper-cased value. */
    private static String sensitive(String value, String field, Pattern pattern, String message, List<FieldError> errors) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        if (!trimmed.isEmpty() && !pattern.matcher(trimmed).matches()) {
            errors.add(new FieldError(field, message));
        }
        return trimmed;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /** The four tiers, as thresholds for the rules and as references for responses. */
    record TierTable(List<PartnerTier> tiers) {

        List<TierRules.Threshold> thresholds() {
            return tiers.stream().map(t -> new TierRules.Threshold(t.getTierLevel(), t.getMinimumRevenue())).toList();
        }

        TierRef ref(int level) {
            return tiers.stream().filter(t -> t.getTierLevel() == level).findFirst().map(TierRef::of)
                    .orElseThrow(() -> new IllegalStateException("Tier " + level + " is missing"));
        }

        TierRef calculated(BigDecimal revenue, boolean programme) {
            return ref(TierRules.calculatedLevel(thresholds(), revenue, programme));
        }
    }
}
