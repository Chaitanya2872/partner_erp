package com.iotiq.partnererp.modules.payments.dto;

import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.common.enums.PaymentStatus;
import com.iotiq.partnererp.modules.leads.dto.PartnerRef;
import com.iotiq.partnererp.modules.payments.entity.PaymentEntryType;
import com.iotiq.partnererp.modules.payments.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One ledger entry with its invoice's current balance.
 *
 * @param reversal         for a receipt that was reversed: the reversal entry
 * @param reversalOf       for a reversal: the receipt it reverses
 * @param reversalReason   for a reversal: why the receipt was reversed
 * @param canReverse       the caller may reverse this entry now
 * @param reverseBlockedBy why this receipt can't be reversed now (null when it can, or when it isn't a receipt)
 */
public record PaymentResponse(UUID id, String paymentNumber, PaymentEntryType entryType, String entryTypeLabel,
                              LocalDate paymentDate, BigDecimal amount, BigDecimal netAmount, PaymentMethod method,
                              String methodLabel, String reference, String notes, PartnerRef partner,
                              InvoiceBalance invoice, Reversal reversal, PaymentSummary.PaymentRef reversalOf,
                              String reversalReason, boolean canReverse, String reverseBlockedBy, Instant createdAt,
                              String createdByName) {

    public record InvoiceBalance(UUID id, String invoiceNumber, InvoiceStatus status, String billingName,
                                 LocalDate invoiceDate, LocalDate dueDate, BigDecimal grandTotal,
                                 BigDecimal taxableAmount, BigDecimal paidAmount, BigDecimal balance,
                                 PaymentStatus paymentStatus, String paymentStatusLabel) {
    }

    public record Reversal(UUID id, String paymentNumber, LocalDate paymentDate, String reason, Instant createdAt,
                           String createdByName) {
    }
}
