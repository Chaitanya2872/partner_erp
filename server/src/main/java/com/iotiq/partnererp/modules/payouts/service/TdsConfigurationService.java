package com.iotiq.partnererp.modules.payouts.service;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.payouts.dto.TdsConfigurationRequest;
import com.iotiq.partnererp.modules.payouts.dto.TdsConfigurationResponse;
import com.iotiq.partnererp.modules.payouts.dto.TdsConfirmRequest;
import com.iotiq.partnererp.modules.payouts.entity.PayoutEnums.TdsConfigStatus;
import com.iotiq.partnererp.modules.payouts.entity.TdsConfiguration;
import com.iotiq.partnererp.modules.payouts.repository.TdsConfigurationRepository;
import com.iotiq.partnererp.security.AuthenticatedUser;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * TDS settings for partner payouts (D-08, DECISIONS.md §L). Nothing statutory is assumed or seeded: Finance enters the
 * section, rates, financial-year threshold and effective date ({@code SETTINGS:WRITE}), and a second person confirms
 * them with a note ({@code PAYOUTS:APPROVE}). Until a confirmed version is in force, payouts can be previewed and
 * drafted but not approved or paid. Confirmed versions never change; a change is a new version.
 */
@Service
public class TdsConfigurationService {

    static final int MAX_SECTION = 20;
    static final int MAX_DESCRIPTION = 200;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TdsConfigurationRepository repository;
    private final AuditService auditService;
    private final UserNames userNames;
    private final Clock clock;

    public TdsConfigurationService(TdsConfigurationRepository repository, AuditService auditService,
                                   UserNames userNames, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.userNames = userNames;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TdsConfigurationResponse get(AuthenticatedUser user) {
        TdsConfiguration inForce = repository.inForce(LocalDate.now(clock)).orElse(null);
        TdsConfiguration draft = repository.findFirstByStatus(TdsConfigStatus.DRAFT).orElse(null);
        List<TdsConfiguration> history = repository.findByStatusOrderByEffectiveFromDesc(TdsConfigStatus.CONFIRMED);
        Set<UUID> ids = new HashSet<>();
        history.forEach(c -> {
            ids.add(c.getEditedBy());
            ids.add(c.getConfirmedBy());
        });
        if (draft != null) {
            ids.add(draft.getEditedBy());
        }
        Map<UUID, String> names = userNames.of(ids);
        boolean canConfirm = draft != null && canConfirm(user) && !Objects.equals(draft.getEditedBy(), user.id());
        return new TdsConfigurationResponse(inForce != null, view(inForce, names), view(draft, names),
                history.stream().map(c -> view(c, names)).toList(), canEdit(user), canConfirm);
    }

    /** Create or update the draft ({@code SETTINGS:WRITE}, checked by the controller). */
    @Transactional
    public TdsConfigurationResponse saveDraft(TdsConfigurationRequest r, AuthenticatedUser user) {
        if (r == null) {
            throw ValidationException.of("sectionCode", "Enter the TDS settings.");
        }
        String section = PayoutRules.clean(r.sectionCode());
        String description = PayoutRules.clean(r.description());
        List<FieldError> errors = new ArrayList<>();
        if (section != null && section.length() > MAX_SECTION) {
            errors.add(new FieldError("sectionCode", "Must be at most " + MAX_SECTION + " characters."));
        }
        if (description != null && description.length() > MAX_DESCRIPTION) {
            errors.add(new FieldError("description", "Must be at most " + MAX_DESCRIPTION + " characters."));
        }
        checkRate(r.ratePercent(), "ratePercent", errors);
        checkRate(r.rateWithoutPanPercent(), "rateWithoutPanPercent", errors);
        if (r.fyThreshold() != null) {
            if (r.fyThreshold().signum() < 0) {
                errors.add(new FieldError("fyThreshold", "The threshold can't be negative."));
            } else if (r.fyThreshold().stripTrailingZeros().scale() > 2) {
                errors.add(new FieldError("fyThreshold", "Use at most two decimal places."));
            } else if (r.fyThreshold().compareTo(PayoutRules.MAX_ADJUSTMENT) > 0) {
                errors.add(new FieldError("fyThreshold", "The threshold is too large."));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        TdsConfiguration draft = repository.findFirstByStatus(TdsConfigStatus.DRAFT)
                .flatMap(d -> repository.lock(d.getId())).orElse(null);
        Map<String, Object> before = null;
        if (draft == null) {
            draft = new TdsConfiguration();
            draft.setStatus(TdsConfigStatus.DRAFT);
        } else {
            if (r.version() == null) {
                throw ValidationException.of("version", "Version is required.");
            }
            if (r.version() != draft.getVersion()) {
                throw new ObjectOptimisticLockingFailureException(TdsConfiguration.class, draft.getId());
            }
            before = values(draft);
        }
        draft.setSectionCode(section);
        draft.setDescription(description);
        draft.setRatePercent(r.ratePercent());
        draft.setRateWithoutPanPercent(r.rateWithoutPanPercent());
        draft.setFyThreshold(r.fyThreshold() == null ? null : r.fyThreshold().setScale(2));
        draft.setEffectiveFrom(r.effectiveFrom());
        draft.setEditedBy(user.id());
        repository.saveAndFlush(draft);
        auditService.record(AuditActions.TDS_CONFIGURATION_UPDATED, AuditActions.ENTITY_TDS_CONFIGURATION,
                draft.getId(), before, values(draft));
        return get(user);
    }

    /**
     * Confirm the draft ({@code PAYOUTS:APPROVE}, checked by the controller). The confirmer can't be the person who
     * last edited it, every value must be present, and the effective date must be after the latest confirmed one.
     */
    @Transactional
    public TdsConfigurationResponse confirm(TdsConfirmRequest r, AuthenticatedUser user) {
        String note = r == null ? null : PayoutRules.clean(r.note());
        if (note == null) {
            throw ValidationException.of("note", "Record Finance's confirmation (who confirmed it and on what basis).");
        }
        if (note.length() > PayoutRules.MAX_NOTES) {
            throw ValidationException.of("note", "Must be at most " + PayoutRules.MAX_NOTES + " characters.");
        }
        TdsConfiguration draft = repository.findFirstByStatus(TdsConfigStatus.DRAFT)
                .flatMap(d -> repository.lock(d.getId()))
                .orElseThrow(() -> BusinessException.conflict("TDS_NO_DRAFT", "There is no draft TDS configuration."));
        if (r.version() == null) {
            throw ValidationException.of("version", "Version is required.");
        }
        if (r.version() != draft.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(TdsConfiguration.class, draft.getId());
        }
        if (!complete(draft)) {
            throw new BusinessException("TDS_INCOMPLETE",
                    "Enter the section, rate, financial-year threshold and effective date before confirming.");
        }
        if (Objects.equals(draft.getEditedBy(), user.id())) {
            throw BusinessException.conflict("SELF_APPROVAL",
                    "You edited this configuration, so someone else must confirm it.");
        }
        List<TdsConfiguration> confirmed = repository.findByStatusOrderByEffectiveFromDesc(TdsConfigStatus.CONFIRMED);
        if (!confirmed.isEmpty() && !draft.getEffectiveFrom().isAfter(confirmed.get(0).getEffectiveFrom())) {
            throw BusinessException.conflict("TDS_EFFECTIVE_DATE", "The effective date must be after "
                    + confirmed.get(0).getEffectiveFrom() + ", when the current version took effect.");
        }
        Map<String, Object> before = values(draft);
        draft.setStatus(TdsConfigStatus.CONFIRMED);
        draft.setConfirmedAt(clock.instant());
        draft.setConfirmedBy(user.id());
        draft.setConfirmationNote(note);
        repository.saveAndFlush(draft);
        Map<String, Object> after = values(draft);
        after.put("confirmationNote", note);
        auditService.record(AuditActions.TDS_CONFIGURATION_CONFIRMED, AuditActions.ENTITY_TDS_CONFIGURATION,
                draft.getId(), before, after);
        return get(user);
    }

    public static boolean canEdit(AuthenticatedUser user) {
        return user.permissions().canWrite(ModuleCode.SETTINGS.name());
    }

    public static boolean canConfirm(AuthenticatedUser user) {
        return user.permissions().canPerform(ModuleCode.PAYOUTS.name(), "APPROVE");
    }

    private static void checkRate(BigDecimal rate, String field, List<FieldError> errors) {
        if (rate == null) {
            return;
        }
        if (rate.signum() < 0 || rate.compareTo(HUNDRED) > 0) {
            errors.add(new FieldError(field, "Enter a rate between 0 and 100."));
        } else if (rate.stripTrailingZeros().scale() > 3) {
            errors.add(new FieldError(field, "Use at most three decimal places."));
        }
    }

    private static boolean complete(TdsConfiguration c) {
        return c.getSectionCode() != null && c.getRatePercent() != null && c.getFyThreshold() != null
                && c.getEffectiveFrom() != null;
    }

    private static Map<String, Object> values(TdsConfiguration c) {
        return AuditService.values("status", c.getStatus(), "sectionCode", c.getSectionCode(),
                "description", c.getDescription(), "ratePercent", c.getRatePercent(),
                "rateWithoutPanPercent", c.getRateWithoutPanPercent(), "fyThreshold", c.getFyThreshold(),
                "effectiveFrom", c.getEffectiveFrom());
    }

    private static TdsConfigurationResponse.Version view(TdsConfiguration c, Map<UUID, String> names) {
        if (c == null) {
            return null;
        }
        return new TdsConfigurationResponse.Version(c.getId(), c.getStatus().name(), c.getSectionCode(),
                c.getDescription(), c.getRatePercent(), c.getRateWithoutPanPercent(), c.getFyThreshold(),
                c.getEffectiveFrom(), names.get(c.getEditedBy()), c.getUpdatedAt(), c.getConfirmedAt(),
                names.get(c.getConfirmedBy()), c.getConfirmationNote(), complete(c), c.getVersion());
    }
}
