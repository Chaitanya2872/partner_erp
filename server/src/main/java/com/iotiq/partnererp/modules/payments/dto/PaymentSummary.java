package com.iotiq.partnererp.modules.payments.dto;

import com.iotiq.partnererp.modules.leads.dto.PartnerRef;
import com.iotiq.partnererp.modules.payments.entity.PaymentEntryType;
import com.iotiq.partnererp.modules.payments.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One ledger entry in the payment list. Amounts include GST; {@code netAmount} is the ex-GST part. Reversals carry
 * negative amounts, {@code reversalOf} and {@code reversalReason}; a receipt that has been reversed has
 * {@code reversed = true}.
 */
public record PaymentSummary(UUID id, String paymentNumber, PaymentEntryType entryType, String entryTypeLabel,
                             LocalDate paymentDate, BigDecimal amount, BigDecimal netAmount, PaymentMethod method,
                             String methodLabel, String reference, InvoiceRef invoice, String billingName,
                             PartnerRef partner, boolean reversed, PaymentRef reversalOf, String reversalReason,
                             Instant createdAt) {

    public record InvoiceRef(UUID id, String invoiceNumber) {
    }

    public record PaymentRef(UUID id, String paymentNumber) {
    }
}
