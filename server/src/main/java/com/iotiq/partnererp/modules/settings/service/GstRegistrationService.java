package com.iotiq.partnererp.modules.settings.service;

import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceMeta;
import com.iotiq.partnererp.modules.invoices.mapper.InvoiceMapper;
import com.iotiq.partnererp.modules.invoices.tax.GstState;
import com.iotiq.partnererp.modules.invoices.tax.Gstin;
import com.iotiq.partnererp.modules.settings.dto.GstRegistrationRequest;
import com.iotiq.partnererp.modules.settings.dto.GstRegistrationResponse;
import com.iotiq.partnererp.modules.settings.entity.CompanySettings;
import com.iotiq.partnererp.modules.settings.repository.CompanySettingsRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The supplier's GST registration in Company Settings (Phase 9 decision 1): legal name, address, registered state and
 * GSTIN. Invoices copy these values when they are issued, so later changes never alter issued invoices.
 */
@Service
public class GstRegistrationService {

    static final int MAX_ADDRESS = 1000;

    private final CompanySettingsRepository repository;
    private final AuditService auditService;

    public GstRegistrationService(CompanySettingsRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public GstRegistrationResponse get() {
        return toResponse(settings());
    }

    @Transactional
    public GstRegistrationResponse update(GstRegistrationRequest r) {
        CompanySettings settings = settings();
        if (r == null || r.version() == null) {
            throw ValidationException.of("version", "Version is required.");
        }
        if (r.version() != settings.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(CompanySettings.class, settings.getId());
        }
        List<FieldError> errors = new ArrayList<>();
        String name = r.legalName() == null ? null : r.legalName().strip();
        if (name == null || name.isEmpty()) {
            errors.add(new FieldError("legalName", "Enter the registered legal name."));
        } else if (name.length() > 200) {
            errors.add(new FieldError("legalName", "Must be at most 200 characters."));
        }
        String address = r.address() == null ? null : r.address().strip();
        if (address == null || address.isEmpty()) {
            errors.add(new FieldError("address", "Enter the registered address of the place of business."));
        } else if (address.length() > MAX_ADDRESS) {
            errors.add(new FieldError("address", "Must be at most " + MAX_ADDRESS + " characters."));
        }
        GstState state = GstState.byCode(r.stateCode()).orElse(null);
        if (state == null) {
            errors.add(new FieldError("stateCode", "Choose the state of the GST registration."));
        }
        String gstin = Gstin.normalize(r.gstin());
        if (gstin == null) {
            errors.add(new FieldError("gstin", "Enter the GSTIN."));
        } else {
            String problem = Gstin.problem(gstin);
            if (problem != null) {
                errors.add(new FieldError("gstin", problem));
            } else if (state != null && !gstin.startsWith(state.getCode())) {
                errors.add(new FieldError("gstin", "This GSTIN belongs to state code " + gstin.substring(0, 2)
                        + ", not " + state.label() + "."));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        Map<String, Object> before = snapshot(settings);
        settings.setCompanyName(name);
        settings.setAddress(address);
        settings.setStateCode(state.getCode());
        settings.setGstin(gstin);
        Map<String, Object> after = snapshot(settings);
        if (!before.equals(after)) {
            repository.saveAndFlush(settings);
            Map<String, Object> oldValues = AuditService.values();
            Map<String, Object> newValues = AuditService.values();
            before.forEach((k, v) -> {
                if (!Objects.equals(v, after.get(k))) {
                    // GSTINs are recorded masked, as for partners and invoices.
                    oldValues.put(k, "gstin".equals(k) ? InvoiceMapper.maskGstin((String) v) : v);
                    newValues.put(k, "gstin".equals(k) ? InvoiceMapper.maskGstin((String) after.get(k)) : after.get(k));
                }
            });
            auditService.record(AuditActions.GST_REGISTRATION_UPDATED, AuditActions.ENTITY_SETTINGS, settings.getId(),
                    oldValues, newValues);
        }
        return toResponse(settings);
    }

    private static Map<String, Object> snapshot(CompanySettings s) {
        return AuditService.values("legalName", s.getCompanyName(), "address", s.getAddress(),
                "stateCode", s.getStateCode(), "gstin", s.getGstin());
    }

    private static GstRegistrationResponse toResponse(CompanySettings s) {
        GstState state = GstState.byCode(s.getStateCode()).orElse(null);
        boolean complete = state != null && s.getGstin() != null && s.getAddress() != null && !s.getAddress().isBlank();
        return new GstRegistrationResponse(s.getCompanyName(), s.getAddress(), s.getStateCode(),
                state == null ? null : state.getDisplayName(), s.getGstin(), complete, s.getVersion(),
                Arrays.stream(GstState.values()).map(g -> new InvoiceMeta.State(g.getCode(), g.getDisplayName(), g.isUtgst()))
                        .toList());
    }

    private CompanySettings settings() {
        return repository.findSingleton().orElseThrow(() -> new IllegalStateException("Company settings are missing"));
    }
}
