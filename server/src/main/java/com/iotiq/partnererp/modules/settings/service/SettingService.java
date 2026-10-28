package com.iotiq.partnererp.modules.settings.service;

import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.estimates.service.EstimateRules;
import com.iotiq.partnererp.modules.invoices.service.InvoiceRules;
import com.iotiq.partnererp.modules.settings.dto.SettingRequest;
import com.iotiq.partnererp.modules.settings.dto.SettingResponse;
import com.iotiq.partnererp.modules.settings.entity.CompanySettings;
import com.iotiq.partnererp.modules.settings.entity.PartnerTier;
import com.iotiq.partnererp.modules.settings.mapper.SettingMapper;
import com.iotiq.partnererp.modules.settings.repository.CompanySettingsRepository;
import com.iotiq.partnererp.modules.settings.repository.PartnerTierRepository;
import com.iotiq.partnererp.security.AuthenticatedUser;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Company profile, document defaults, lead SLA targets, commercial policy and the four partner tiers (Phase 13).
 *
 * <p>Changes apply from the next operation that reads them; nothing stored is recalculated. Estimates and invoices keep
 * their own dates, terms and fee %, acknowledged leads keep their locked fee % and protection date, and payouts keep
 * their line percentages (ADR-10). Every change is audited with the changed fields only.
 */
@Service
public class SettingService {

    static final int MAX_TERMS = EstimateRules.MAX_NOTES;
    static final int MAX_VALIDITY_DAYS = 365;
    static final int MAX_ACK_HOURS = 720;
    static final int MAX_SLA_DAYS = 365;
    static final int MAX_PROTECTION_DAYS = 730;
    static final int MAX_PAYOUT_DAYS = 365;
    static final BigDecimal MAX_REVENUE = new BigDecimal("999999999999.99");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\- ]{6,30}$");

    private final CompanySettingsRepository settingsRepository;
    private final PartnerTierRepository tierRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public SettingService(CompanySettingsRepository settingsRepository, PartnerTierRepository tierRepository,
                          UserRepository userRepository, AuditService auditService) {
        this.settingsRepository = settingsRepository;
        this.tierRepository = tierRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /** Settings write. */
    public static boolean canEdit(AuthenticatedUser user) {
        return user.permissions().canWrite(ModuleCode.SETTINGS.name());
    }

    /** Settings write plus MANAGE_SETTINGS: lead protection and the partner tiers (P3-06). */
    public static boolean canManageCommercial(AuthenticatedUser user) {
        return canEdit(user) && user.permissions().canPerform(ModuleCode.SETTINGS.name(), "MANAGE_SETTINGS");
    }

    @Transactional(readOnly = true)
    public SettingResponse.Company company(AuthenticatedUser user) {
        return toCompany(settings(), user);
    }

    @Transactional
    public SettingResponse.Company updateCompany(SettingRequest.Company r, AuthenticatedUser user) {
        CompanySettings s = settings();
        checkVersion(s, r == null ? null : r.version());
        List<FieldError> errors = new ArrayList<>();
        String phone = blankToNull(r.phone());
        if (phone != null && (!PHONE.matcher(phone).matches() || digits(phone) < 6 || digits(phone) > 15)) {
            errors.add(new FieldError("phone", "Enter a phone number of 6 to 15 digits (spaces, +, - and brackets allowed)."));
        }
        String email = blankToNull(r.email());
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
            if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
                errors.add(new FieldError("email", "Enter a valid email address."));
            }
        }
        String terms = blankToNull(r.terms());
        if (terms != null && terms.length() > MAX_TERMS) {
            errors.add(new FieldError("terms", "Use at most " + MAX_TERMS + " characters."));
        }
        int validity = range(errors, "estimateValidityDays", r.estimateValidityDays(), 1, MAX_VALIDITY_DAYS, "Estimate validity");
        int due = range(errors, "invoiceDueDays", r.invoiceDueDays(), 0, InvoiceRules.MAX_DUE_DAYS, "Invoice due days");
        BigDecimal gst = percent(errors, "defaultGstPercent", r.defaultGstPercent(), "Default GST %");
        int ack = range(errors, "acknowledgementHours", r.acknowledgementHours(), 1, MAX_ACK_HOURS, "Acknowledgement target");
        int sla = range(errors, "estimateSlaDays", r.estimateSlaDays(), 1, MAX_SLA_DAYS, "Estimate target");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        Map<String, Object> before = companySnapshot(s);
        s.setPhone(phone);
        s.setEmail(email);
        s.setTerms(terms);
        s.setEstimateValidityDays(validity);
        s.setInvoiceDueDays(due);
        s.setDefaultGstPercent(gst);
        s.setAcknowledgementHours(ack);
        s.setEstimateSlaDays(sla);
        save(s, before, companySnapshot(s), AuditActions.COMPANY_SETTINGS_UPDATED);
        return toCompany(s, user);
    }

    /** Lead protection (MANAGE_SETTINGS). Applies to leads acknowledged afterwards; set protection dates stay. */
    @Transactional
    public SettingResponse.Company updateCommercial(SettingRequest.Commercial r, AuthenticatedUser user) {
        requireCommercial(user);
        CompanySettings s = settings();
        checkVersion(s, r == null ? null : r.version());
        List<FieldError> errors = new ArrayList<>();
        int protection = range(errors, "leadProtectionDays", r.leadProtectionDays(), 1, MAX_PROTECTION_DAYS, "Lead protection");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        Map<String, Object> before = AuditService.values("leadProtectionDays", s.getLeadProtectionDays());
        s.setLeadProtectionDays(protection);
        save(s, before, AuditService.values("leadProtectionDays", s.getLeadProtectionDays()),
                AuditActions.COMMERCIAL_SETTINGS_UPDATED);
        return toCompany(s, user);
    }

    @Transactional(readOnly = true)
    public SettingResponse.Tiers tiers(AuthenticatedUser user) {
        return toTiers(tierRepository.findAllByOrderByTierLevelAsc(), user);
    }

    /**
     * Replaces the four tiers' names, fee %, thresholds and payout days. The fee % reaches leads acknowledged afterwards
     * and documents created afterwards without a locked rate; thresholds reach the next manual tier review. Nothing
     * already stored changes and no partner changes tier (no automatic upgrade, D-06 / L-4).
     */
    @Transactional
    public SettingResponse.Tiers updateTiers(SettingRequest.Tiers r, AuthenticatedUser user) {
        requireCommercial(user);
        List<PartnerTier> tiers = tierRepository.lockAll();
        List<FieldError> errors = new ArrayList<>();
        Map<Integer, SettingRequest.Tier> byLevel = new HashMap<>();
        if (r == null || r.tiers() == null || r.tiers().size() != tiers.size()) {
            throw ValidationException.of("tiers", "Send all " + tiers.size() + " tiers.");
        }
        for (SettingRequest.Tier t : r.tiers()) {
            if (t == null || t.level() == null || t.level() < 1 || t.level() > tiers.size()
                    || byLevel.putIfAbsent(t.level(), t) != null) {
                throw ValidationException.of("tiers", "Send each tier level 1 to " + tiers.size() + " exactly once.");
            }
        }
        Set<String> names = new HashSet<>();
        BigDecimal previousMinimum = null;
        List<Validated> validated = new ArrayList<>();
        for (PartnerTier tier : tiers) {
            SettingRequest.Tier t = byLevel.get((int) tier.getTierLevel());
            String f = "tiers[" + tier.getTierLevel() + "]";
            if (t.version() == null || t.version() != tier.getVersion()) {
                throw new ObjectOptimisticLockingFailureException(PartnerTier.class, tier.getId());
            }
            String name = t.name() == null ? "" : t.name().strip().replaceAll("\\s+", " ");
            if (name.length() < 2 || name.length() > 60) {
                errors.add(new FieldError(f + ".name", "Tier name must be 2 to 60 characters."));
            } else if (!names.add(name.toLowerCase(Locale.ROOT))) {
                errors.add(new FieldError(f + ".name", "Another tier already has this name."));
            }
            BigDecimal pct = percent(errors, f + ".commissionPercent", t.commissionPercent(), "Fee %");
            BigDecimal minimum = t.minimumRevenue();
            if (minimum == null) {
                errors.add(new FieldError(f + ".minimumRevenue", "Minimum revenue is required."));
            } else if (minimum.signum() < 0 || minimum.compareTo(MAX_REVENUE) > 0) {
                errors.add(new FieldError(f + ".minimumRevenue", "Minimum revenue must be between 0 and 999,999,999,999.99."));
            } else if (minimum.stripTrailingZeros().scale() > 2) {
                errors.add(new FieldError(f + ".minimumRevenue", "Use at most 2 decimal places."));
            } else if (tier.getTierLevel() == 1 && minimum.signum() != 0) {
                // tierForRev picks the highest tier whose minimum is met, so the first tier must take every partner.
                errors.add(new FieldError(f + ".minimumRevenue", "The first tier's minimum must be 0, so every partner qualifies for a tier."));
            } else if (previousMinimum != null && minimum.compareTo(previousMinimum) <= 0) {
                errors.add(new FieldError(f + ".minimumRevenue", "Must be higher than the previous tier's minimum."));
            }
            if (minimum != null) {
                previousMinimum = minimum;
            }
            int days = range(errors, f + ".payoutBusinessDays", t.payoutBusinessDays(), 1, MAX_PAYOUT_DAYS, "Payout days");
            validated.add(new Validated(tier, name, pct, minimum, days));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        for (Validated v : validated) {
            PartnerTier tier = v.tier();
            Map<String, Object> before = tierSnapshot(tier);
            tier.setName(v.name());
            tier.setCommissionPercent(v.percent().setScale(3, RoundingMode.UNNECESSARY));
            tier.setMinimumRevenue(v.minimum().setScale(2, RoundingMode.UNNECESSARY));
            tier.setPayoutBusinessDays((short) v.payoutDays());
            Map<String, Object> after = tierSnapshot(tier);
            if (!before.equals(after)) {
                tierRepository.saveAndFlush(tier);
                Map<String, Object> oldValues = AuditService.values("level", (int) tier.getTierLevel());
                Map<String, Object> newValues = AuditService.values("level", (int) tier.getTierLevel());
                before.forEach((k, value) -> {
                    if (!Objects.equals(value, after.get(k))) {
                        oldValues.put(k, value);
                        newValues.put(k, after.get(k));
                    }
                });
                auditService.record(AuditActions.PARTNER_TIER_UPDATED, AuditActions.ENTITY_PARTNER_TIER, tier.getId(),
                        oldValues, newValues);
            }
        }
        return toTiers(tierRepository.findAllByOrderByTierLevelAsc(), user);
    }

    private SettingResponse.Tiers toTiers(List<PartnerTier> tiers, AuthenticatedUser user) {
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : tierRepository.countPartnersByLevel()) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        Map<UUID, String> names = names(tiers.stream().map(PartnerTier::getUpdatedBy).toList());
        return new SettingResponse.Tiers(tiers.stream().map(t -> SettingMapper.tier(t,
                counts.getOrDefault((int) t.getTierLevel(), 0L), names.get(t.getUpdatedBy()))).toList(),
                canManageCommercial(user));
    }

    private SettingResponse.Company toCompany(CompanySettings s, AuthenticatedUser user) {
        String updatedBy = names(Collections.singletonList(s.getUpdatedBy())).get(s.getUpdatedBy());
        return SettingMapper.company(s, updatedBy, canEdit(user), canManageCommercial(user),
                new SettingResponse.Limits(MAX_TERMS, MAX_VALIDITY_DAYS, InvoiceRules.MAX_DUE_DAYS, MAX_ACK_HOURS,
                        MAX_SLA_DAYS, MAX_PROTECTION_DAYS));
    }

    private Map<UUID, String> names(List<UUID> ids) {
        List<UUID> known = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> names = new HashMap<>();
        if (!known.isEmpty()) {
            userRepository.findAllById(known).forEach(u -> names.put(u.getId(), u.getFullName()));
        }
        return names;
    }

    private void save(CompanySettings s, Map<String, Object> before, Map<String, Object> after, String action) {
        if (before.equals(after)) {
            return;
        }
        settingsRepository.saveAndFlush(s);
        Map<String, Object> oldValues = AuditService.values();
        Map<String, Object> newValues = AuditService.values();
        before.forEach((k, v) -> {
            if (!Objects.equals(v, after.get(k))) {
                oldValues.put(k, v);
                newValues.put(k, after.get(k));
            }
        });
        auditService.record(action, AuditActions.ENTITY_SETTINGS, s.getId(), oldValues, newValues);
    }

    private static Map<String, Object> companySnapshot(CompanySettings s) {
        return AuditService.values("phone", s.getPhone(), "email", s.getEmail(), "terms", s.getTerms(),
                "estimateValidityDays", s.getEstimateValidityDays(), "invoiceDueDays", s.getInvoiceDueDays(),
                "defaultGstPercent", plain(s.getDefaultGstPercent()), "acknowledgementHours", s.getAcknowledgementHours(),
                "estimateSlaDays", s.getEstimateSlaDays());
    }

    private static Map<String, Object> tierSnapshot(PartnerTier t) {
        return AuditService.values("name", t.getName(), "commissionPercent", plain(t.getCommissionPercent()),
                "minimumRevenue", plain(t.getMinimumRevenue()), "payoutBusinessDays", (int) t.getPayoutBusinessDays());
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static void requireCommercial(AuthenticatedUser user) {
        if (!canManageCommercial(user)) {
            throw new AccessDeniedException("Settings write and Manage commercial settings are required");
        }
    }

    private static void checkVersion(CompanySettings s, Long version) {
        if (version == null) {
            throw ValidationException.of("version", "Version is required.");
        }
        if (version != s.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(CompanySettings.class, s.getId());
        }
    }

    private static int range(List<FieldError> errors, String field, Integer value, int min, int max, String label) {
        if (value == null) {
            errors.add(new FieldError(field, label + " is required."));
            return 0;
        }
        if (value < min || value > max) {
            errors.add(new FieldError(field, label + " must be between " + min + " and " + max + "."));
        }
        return value;
    }

    private static BigDecimal percent(List<FieldError> errors, String field, BigDecimal value, String label) {
        if (value == null) {
            errors.add(new FieldError(field, label + " is required."));
        } else if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            errors.add(new FieldError(field, label + " must be between 0 and 100."));
        } else if (value.stripTrailingZeros().scale() > 3) {
            errors.add(new FieldError(field, "Use at most 3 decimal places."));
        }
        return value;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static long digits(String value) {
        return value.chars().filter(Character::isDigit).count();
    }

    private CompanySettings settings() {
        return settingsRepository.findSingleton().orElseThrow(() -> new IllegalStateException("Company settings are missing"));
    }

    private record Validated(PartnerTier tier, String name, BigDecimal percent, BigDecimal minimum, int payoutDays) {
    }
}
