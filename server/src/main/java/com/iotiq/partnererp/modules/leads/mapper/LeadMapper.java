package com.iotiq.partnererp.modules.leads.mapper;

import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.leads.dto.LeadResponse;
import com.iotiq.partnererp.modules.leads.dto.LeadSummary;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;
import com.iotiq.partnererp.modules.leads.dto.SlaStatus;
import com.iotiq.partnererp.modules.leads.entity.Lead;
import com.iotiq.partnererp.modules.leads.service.LeadSla;

import java.time.LocalDate;
import java.util.Map;

/**
 * Entity → DTO conversion for leads, and the audit snapshot.
 */
public final class LeadMapper {

    private LeadMapper() {
    }

    public static SlaStatus toSla(LeadSla.Result sla, LeadSla.Targets targets, Lead lead, LocalDate today) {
        return new SlaStatus(sla.ackHours(), sla.ackBreach(), sla.estimateDays(), sla.estimateBreach(),
                sla.hasEstimate(), targets.ackHours(), targets.estimateDays(),
                LeadSla.protectionDaysLeft(lead.getProtectionUntil(), today));
    }

    public static LeadSummary toSummary(Lead l, SlaStatus sla, LocalDate today) {
        return new LeadSummary(l.getId(), l.getClientName(), l.getClientPhone(), l.getCity(), l.getProjectType(),
                l.getProjectType().getLabel(), l.getUnits(), l.getBudget(), l.getStage(), l.getStage().getLabel(),
                partner(l), l.getAcknowledgedAt(), l.getLockedFeePercent(), l.getProtectionUntil(),
                LeadSla.protectionActive(l.getProtectionUntil(), today), l.isConflict(), sla, l.getCreatedAt(),
                l.getVersion());
    }

    public static LeadResponse toResponse(Lead l, SlaStatus sla, LocalDate today, String acknowledgedByName,
                                          String conflictPartnerName) {
        return new LeadResponse(l.getId(), l.getClientName(), l.getClientPhone(), l.getCity(), partner(l),
                l.getProjectType(), l.getProjectType().getLabel(), l.getUnits(), l.getBudget(), l.getStage(),
                l.getStage().getLabel(), l.getPresalesOwner(), l.getExpectedClose(), l.getSource(), l.getNotes(),
                l.getAcknowledgedAt(), acknowledgedByName, l.getLockedFeePercent(), l.getProtectionUntil(),
                LeadSla.protectionActive(l.getProtectionUntil(), today), l.isConflict(), conflictPartnerName, sla,
                l.getCreatedAt(), l.getUpdatedAt(), l.getVersion());
    }

    /** Editable fields plus the conflict flag, for change-key audit entries. */
    public static Map<String, Object> auditSnapshot(Lead l) {
        return AuditService.values(
                "clientName", l.getClientName(),
                "clientPhone", l.getClientPhone(),
                "city", l.getCity(),
                "partnerId", l.getPartner().getId(),
                "projectType", l.getProjectType(),
                "units", l.getUnits(),
                "budget", l.getBudget(),
                "stage", l.getStage(),
                "presalesOwner", l.getPresalesOwner(),
                "expectedClose", l.getExpectedClose(),
                "source", l.getSource(),
                "notes", l.getNotes(),
                "conflict", l.isConflict());
    }

    private static PartnerRef partner(Lead l) {
        return new PartnerRef(l.getPartner().getId(), l.getPartner().getName());
    }
}
