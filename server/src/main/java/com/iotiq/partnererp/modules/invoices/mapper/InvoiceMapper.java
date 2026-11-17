package com.iotiq.partnererp.modules.invoices.mapper;

import com.iotiq.partnererp.common.enums.PaymentStatus;
import com.iotiq.partnererp.common.persistence.LineKind;
import com.iotiq.partnererp.common.util.DocumentCalculator;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.b2c.entity.B2CDocument;
import com.iotiq.partnererp.modules.b2c.entity.B2CDocumentItem;
import com.iotiq.partnererp.modules.estimates.dto.EstimateLeadRef;
import com.iotiq.partnererp.modules.estimates.dto.TotalsResponse;
import com.iotiq.partnererp.modules.invoices.dto.InternalInvoiceDocument;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceParty;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceResponse;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceSummary;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceTotals;
import com.iotiq.partnererp.modules.invoices.entity.Invoice;
import com.iotiq.partnererp.modules.invoices.entity.InvoiceItem;
import com.iotiq.partnererp.modules.invoices.tax.GstState;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;
import com.iotiq.partnererp.modules.leads.entity.Lead;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Entity → DTO conversion for invoices. Partner-safe DTOs and audit snapshots are built only from partner-facing
 * columns; the B2C snapshot columns ({@code b2c_name}, {@code b2c_unit_price}) are read only by {@link #toInternal}.
 */
public final class InvoiceMapper {

    private InvoiceMapper() {
    }

    public static InvoiceSummary toSummary(Invoice i, PaymentStatus paymentStatus, BigDecimal paid) {
        Lead lead = i.getLead();
        boolean draft = paymentStatus == null;
        return new InvoiceSummary(i.getId(), i.getInvoiceNumber(), i.getStatus(), i.getStatus().getLabel(),
                i.getInvoiceDate(), i.getDueDate(), paymentStatus, paymentStatus == null ? null : paymentStatus.getLabel(),
                lead == null ? null : lead.getId(), lead == null ? null : lead.getLeadNumber(),
                lead == null ? null : lead.getClientName(), i.getBillingName(), partner(i),
                i.getEstimate() == null ? null : i.getEstimate().getEstimateNumber(), i.getTaxType(),
                i.getTotals().getTaxableAmount(), i.getTotals().getGrandTotal(), draft ? null : paid,
                draft ? null : i.getTotals().getGrandTotal().subtract(paid), i.getCreatedAt(), i.getVersion());
    }

    public static InvoiceTotals totals(Invoice i) {
        return new InvoiceTotals(i.getTotals().getSubtotal(), i.getTotals().getDiscountAmount(),
                i.getTotals().getTaxableAmount(), i.getCgstAmount(), i.getSgstAmount(), i.getIgstAmount(),
                i.getTotals().getGstAmount(), i.getTotals().getGrandTotal());
    }

    public static InvoiceResponse.Line toLine(InvoiceItem i) {
        return new InvoiceResponse.Line(i.getId(), i.getLineNo(), i.getKind(),
                i.getProduct() == null ? null : i.getProduct().getId(),
                i.getProduct() == null ? null : i.getProduct().getSku(),
                i.isInstallation() || (i.getProduct() != null && i.getProduct().isActive()),
                i.getDescription(), i.getHsn(), i.getUnit(), i.getQuantity(), i.getUnitPrice(), i.getGstPercent(),
                i.getLineTotal(), i.getTaxableValue(), i.getCgstAmount(), i.getSgstAmount(), i.getIgstAmount(),
                i.getTaxAmount(), i.getTotalAmount());
    }

    public static InvoiceParty party(String name, String address, String stateCode, String gstin) {
        return new InvoiceParty(name, address, stateCode,
                GstState.byCode(stateCode).map(GstState::getDisplayName).orElse(null), gstin);
    }

    public static InvoiceParty billing(Invoice i) {
        return party(i.getBillingName(), i.getBillingAddress(), i.getBillingStateCode(), i.getBillingGstin());
    }

    public static InternalInvoiceDocument toInternal(Invoice i, B2CDocument twin) {
        List<InternalInvoiceDocument.Line> lines = twin.getItems().stream().map(InvoiceMapper::toInternalLine).toList();
        int missing = (int) lines.stream().filter(InternalInvoiceDocument.Line::b2cPriceMissing).count();
        return new InternalInvoiceDocument(i.getId(), twin.getInternalNumber(), twin.getParentNumber(),
                twin.getDocumentDate(), i.getStatus(), i.getStatus().getLabel(), twin.getDiscountPercent(),
                twin.isIgst(), lines, TotalsResponse.of(twin.getB2cTotals()), TotalsResponse.of(twin.getPartnerTotals()),
                twin.getUplift(), twin.isB2cIncomplete(), missing, twin.getUpdatedAt());
    }

    /** Audit form of a GSTIN: only the 3 trailing non-PAN characters, as for partners (Phase 5). */
    public static String maskGstin(String gstin) {
        return gstin == null ? null : "••" + gstin.substring(Math.max(0, gstin.length() - 3));
    }

    /** The audit snapshot as recorded: GSTINs masked. */
    public static Map<String, Object> auditSnapshot(Invoice i) {
        return auditSnapshot(i, true);
    }

    /**
     * Partner-safe audit snapshot: header, billing details, place of supply, tax treatment, partner-facing lines with
     * their tax and the totals. Never B2C names, B2C rates or the uplift (Phase 8 decision 6, Phase 9 decision 6).
     *
     * @param masked false only to detect changes; audit rows always get the masked GSTIN
     */
    public static Map<String, Object> auditSnapshot(Invoice i, boolean masked) {
        return AuditService.values(
                "leadId", i.getLead() == null ? null : i.getLead().getId(),
                "partnerId", i.getPartner().getId(),
                "billTo", i.getBillTo(),
                "discountPercent", i.getDiscountPercent(),
                "feePercent", i.getFeePercent(),
                "dueDays", i.getDueDays(),
                "notes", i.getNotes(),
                "billingName", i.getBillingName(),
                "billingAddress", i.getBillingAddress(),
                "billingStateCode", i.getBillingStateCode(),
                "billingGstin", masked ? maskGstin(i.getBillingGstin()) : i.getBillingGstin(),
                "placeOfSupplyCode", i.getPlaceOfSupplyCode(),
                "placeOfSupplyBasis", i.getPlaceOfSupplyBasis(),
                "taxType", i.getTaxType(),
                "lines", i.getItems().stream().map(l -> auditLine(l.getLineNo(), l.getKind(),
                        l.getProduct() == null ? null : l.getProduct().getId(), l.getDescription(), l.getUnit(),
                        l.getQuantity(), l.getUnitPrice(), l.getGstPercent(), l.getTaxableValue(), l.getTaxAmount())).toList(),
                "taxableAmount", i.getTotals().getTaxableAmount(),
                "gstAmount", i.getTotals().getGstAmount(),
                "grandTotal", i.getTotals().getGrandTotal());
    }

    public static Map<String, Object> auditLine(int lineNo, LineKind kind, Object productId, String description,
                                                String unit, Object quantity, Object unitPrice, Object gstPercent,
                                                Object taxableValue, Object taxAmount) {
        if (kind == LineKind.INSTALLATION) {
            return AuditService.values("lineNo", lineNo, "kind", kind, "description", description, "unit", unit,
                    "quantity", quantity, "unitPrice", unitPrice, "gstPercent", gstPercent,
                    "taxableValue", taxableValue, "taxAmount", taxAmount);
        }
        return AuditService.values("lineNo", lineNo, "productId", productId, "description", description,
                "quantity", quantity, "unitPrice", unitPrice, "gstPercent", gstPercent, "taxableValue", taxableValue,
                "taxAmount", taxAmount);
    }

    public static EstimateLeadRef lead(Lead l) {
        if (l == null) {
            return null;
        }
        return new EstimateLeadRef(l.getId(), l.getLeadNumber(), l.getClientName(), l.getClientPhone(), l.getCity(),
                l.getProjectTypeDisplay(), l.getStage(), l.getStage().getLabel(), l.getDeletedAt() != null);
    }

    public static PartnerRef partner(Invoice i) {
        return new PartnerRef(i.getPartner().getId(), i.getPartner().getName());
    }

    private static InternalInvoiceDocument.Line toInternalLine(B2CDocumentItem i) {
        return new InternalInvoiceDocument.Line(i.getLineNo(), i.getKind(), i.getDescription(), i.getPartnerName(), i.getHsn(),
                i.getUnit(), i.getQuantity(), i.getUnitPrice(), i.getGstPercent(),
                DocumentCalculator.lineAmount(i.getQuantity(), i.getUnitPrice()),
                i.getKind() == LineKind.PRODUCT && i.getUnitPrice().signum() == 0);
    }
}
