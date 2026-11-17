package com.iotiq.partnererp.modules.invoices.dto;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.common.enums.PaymentStatus;
import com.iotiq.partnererp.modules.estimates.dto.EstimateLeadRef;
import com.iotiq.partnererp.modules.invoices.tax.PlaceOfSupplyBasis;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;

import com.iotiq.partnererp.common.persistence.LineKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Partner copy of an invoice (ADR-06): never B2C names, B2C prices, the twin number or the uplift.
 *
 * @param supplier          the issued snapshot; for a draft, the current registration (may be incomplete)
 * @param paymentStatus     derived from recorded payments; null for drafts
 * @param comparison        the source estimate's agreed values (converted invoices only)
 */
public record InvoiceResponse(UUID id, String invoiceNumber, InvoiceStatus status, String statusLabel,
                              LocalDate invoiceDate, LocalDate dueDate, int dueDays, PaymentStatus paymentStatus,
                              String paymentStatusLabel, BigDecimal paidAmount, BigDecimal balance,
                              EstimateLeadRef lead, PartnerRef partner, EstimateRef estimate, BillTo billTo,
                              String billToLabel, BigDecimal discountPercent, BigDecimal feePercent, String notes,
                              InvoiceParty supplier, InvoiceParty billing, String placeOfSupplyCode,
                              String placeOfSupplyName, PlaceOfSupplyBasis placeOfSupplyBasis,
                              String placeOfSupplyBasisLabel, TaxTreatmentResponse tax, List<Line> lines,
                              InvoiceTotals totals, EstimateComparison comparison, Instant issuedAt,
                              String issuedByName, Instant cancelledAt, String cancelledByName, String cancelReason,
                              Instant createdAt, String createdByName, Instant updatedAt, long version) {

    public record EstimateRef(UUID id, String estimateNumber) {
    }

    /**
     * @param amount        quantity × partner rate, before the document discount
     * @param lineTotal     taxable value + total tax
     * @param productActive false when the product was deactivated after the line was saved
     */
    public record Line(UUID id, int lineNo, LineKind kind, UUID productId, String sku, boolean productActive, String description,
                       String hsn, String unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal gstPercent,
                       BigDecimal amount, BigDecimal taxableValue, BigDecimal cgstAmount, BigDecimal sgstAmount,
                       BigDecimal igstAmount, BigDecimal taxAmount, BigDecimal lineTotal) {
    }
}
