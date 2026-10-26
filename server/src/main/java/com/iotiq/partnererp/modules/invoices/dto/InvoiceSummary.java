package com.iotiq.partnererp.modules.invoices.dto;

import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.common.enums.PaymentStatus;
import com.iotiq.partnererp.modules.invoices.tax.TaxType;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Invoice list row (source vInvoices). Partner-safe. Drafts have no number, date or payment status. */
public record InvoiceSummary(UUID id, String invoiceNumber, InvoiceStatus status, String statusLabel,
                             LocalDate invoiceDate, LocalDate dueDate, PaymentStatus paymentStatus,
                             String paymentStatusLabel, UUID leadId, String leadNumber, String clientName,
                             String billingName, PartnerRef partner, String estimateNumber, TaxType taxType,
                             BigDecimal taxableAmount, BigDecimal grandTotal, BigDecimal paidAmount,
                             BigDecimal balance, Instant createdAt, long version) {
}
