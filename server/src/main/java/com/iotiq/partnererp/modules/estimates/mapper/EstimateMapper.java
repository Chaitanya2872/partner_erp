package com.iotiq.partnererp.modules.estimates.mapper;

import com.iotiq.partnererp.common.util.DocumentCalculator;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.b2c.entity.B2CDocument;
import com.iotiq.partnererp.modules.b2c.entity.B2CDocumentItem;
import com.iotiq.partnererp.modules.estimates.dto.EstimateLeadRef;
import com.iotiq.partnererp.modules.estimates.dto.EstimateResponse;
import com.iotiq.partnererp.modules.estimates.dto.EstimateSummary;
import com.iotiq.partnererp.modules.estimates.dto.InternalEstimateDocument;
import com.iotiq.partnererp.modules.estimates.dto.TotalsResponse;
import com.iotiq.partnererp.modules.estimates.entity.Estimate;
import com.iotiq.partnererp.modules.estimates.entity.EstimateItem;
import com.iotiq.partnererp.modules.estimates.service.EstimateRules;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;
import com.iotiq.partnererp.modules.leads.entity.Lead;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Entity → DTO conversion for estimates. Partner-safe DTOs are built only from partner-facing columns; the B2C
 * snapshot columns ({@code b2c_name}, {@code b2c_unit_price}) are read only by {@link #toInternal}.
 */
public final class EstimateMapper {

    private EstimateMapper() {
    }

    public static EstimateSummary toSummary(Estimate e, LocalDate today) {
        Lead lead = e.getLead();
        return new EstimateSummary(e.getId(), e.getEstimateNumber(), e.getEstimateDate(), e.getValidUntil(),
                e.getStatus(), e.getStatus().getLabel(), EstimateRules.expired(e.getStatus(), e.getValidUntil(), today),
                lead == null ? null : lead.getId(), lead == null ? null : lead.getLeadNumber(),
                lead == null ? null : lead.getClientName(), partner(e), e.getBillTo(),
                e.getTotals().getTaxableAmount(), e.getTotals().getGrandTotal(), e.getCreatedAt(), e.getVersion());
    }

    public static EstimateResponse toResponse(Estimate e, LocalDate today, String createdByName) {
        List<EstimateResponse.Line> lines = e.getItems().stream().map(EstimateMapper::toLine).toList();
        return new EstimateResponse(e.getId(), e.getEstimateNumber(), e.getEstimateDate(), e.getValidUntil(),
                e.getStatus(), e.getStatus().getLabel(), EstimateRules.expired(e.getStatus(), e.getValidUntil(), today),
                e.getSentOn(), lead(e.getLead()), partner(e), e.getBillTo(), e.getBillTo().getLabel(),
                e.getDiscountPercent(), e.isIgst(), e.getFeePercent(), e.getNotes(), lines,
                TotalsResponse.of(e.getTotals()), e.getCreatedAt(), createdByName, e.getUpdatedAt(), e.getVersion());
    }

    public static InternalEstimateDocument toInternal(Estimate e, B2CDocument twin) {
        List<InternalEstimateDocument.Line> lines = twin.getItems().stream().map(EstimateMapper::toInternalLine).toList();
        int missing = (int) lines.stream().filter(InternalEstimateDocument.Line::b2cPriceMissing).count();
        return new InternalEstimateDocument(e.getId(), twin.getInternalNumber(), twin.getParentNumber(),
                twin.getDocumentDate(), e.getStatus(), e.getStatus().getLabel(), twin.getDiscountPercent(),
                twin.isIgst(), lines, TotalsResponse.of(twin.getB2cTotals()), TotalsResponse.of(twin.getPartnerTotals()),
                twin.getUplift(), twin.isB2cIncomplete(), missing, twin.getUpdatedAt());
    }

    /**
     * Partner-safe audit snapshot: header, partner-facing lines and the grand total. Never B2C names, B2C rates or
     * the uplift (decision 6).
     */
    public static Map<String, Object> auditSnapshot(Estimate e) {
        return AuditService.values(
                "leadId", e.getLead() == null ? null : e.getLead().getId(),
                "partnerId", e.getPartner().getId(),
                "estimateDate", e.getEstimateDate(),
                "validUntil", e.getValidUntil(),
                "discountPercent", e.getDiscountPercent(),
                "igst", e.isIgst(),
                "billTo", e.getBillTo(),
                "feePercent", e.getFeePercent(),
                "notes", e.getNotes(),
                "lines", e.getItems().stream().map(i -> auditLine(i.getLineNo(),
                        i.getProduct() == null ? null : i.getProduct().getId(), i.getDescription(), i.getQuantity(),
                        i.getUnitPrice(), i.getGstPercent())).toList(),
                "grandTotal", e.getTotals().getGrandTotal());
    }

    public static Map<String, Object> auditLine(int lineNo, Object productId, String description, Object quantity,
                                                Object unitPrice, Object gstPercent) {
        return AuditService.values("lineNo", lineNo, "productId", productId, "description", description,
                "quantity", quantity, "unitPrice", unitPrice, "gstPercent", gstPercent);
    }

    private static EstimateResponse.Line toLine(EstimateItem i) {
        return new EstimateResponse.Line(i.getId(), i.getLineNo(), i.getProduct() == null ? null : i.getProduct().getId(),
                i.getProduct() == null ? null : i.getProduct().getSku(), i.getProduct() != null && i.getProduct().isActive(),
                i.getDescription(), i.getHsn(), i.getUnit(), i.getQuantity(), i.getUnitPrice(), i.getGstPercent(),
                i.getLineTotal());
    }

    private static InternalEstimateDocument.Line toInternalLine(B2CDocumentItem i) {
        return new InternalEstimateDocument.Line(i.getLineNo(), i.getDescription(), i.getPartnerName(), i.getHsn(),
                i.getUnit(), i.getQuantity(), i.getUnitPrice(), i.getGstPercent(),
                DocumentCalculator.lineAmount(i.getQuantity(), i.getUnitPrice()), i.getUnitPrice().signum() == 0);
    }

    private static EstimateLeadRef lead(Lead l) {
        if (l == null) {
            return null;
        }
        return new EstimateLeadRef(l.getId(), l.getLeadNumber(), l.getClientName(), l.getClientPhone(), l.getCity(),
                l.getProjectType().getLabel(), l.getStage(), l.getStage().getLabel(), l.getDeletedAt() != null);
    }

    private static PartnerRef partner(Estimate e) {
        return new PartnerRef(e.getPartner().getId(), e.getPartner().getName());
    }
}
