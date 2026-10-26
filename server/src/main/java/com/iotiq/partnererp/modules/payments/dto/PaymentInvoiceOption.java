package com.iotiq.partnererp.modules.payments.dto;

import com.iotiq.partnererp.modules.leads.dto.PartnerRef;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Restricted invoice picker row for recording a receipt (Payments write, no Invoices read needed): issued invoices
 * with an outstanding balance, with the minimum needed to choose one.
 */
public record PaymentInvoiceOption(UUID id, String invoiceNumber, LocalDate invoiceDate, LocalDate dueDate,
                                   String billingName, PartnerRef partner, BigDecimal grandTotal, BigDecimal paidAmount,
                                   BigDecimal balance, String paymentStatusLabel) {
}
