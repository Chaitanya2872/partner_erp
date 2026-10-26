package com.iotiq.partnererp.modules.payments.dto;

import com.iotiq.partnererp.modules.payments.entity.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

/**
 * Totals of the filtered ledger plus current receivables.
 *
 * @param received         Σ receipts (incl. GST)
 * @param reversed         Σ reversals, as a positive number
 * @param refunded         Σ refunds carried over from before Phase 10, as a positive number
 * @param netReceived      received − reversed − refunded
 * @param netReceivedExGst the ex-GST part of {@code netReceived}
 * @param byMethod         net received per payment method
 * @param receivables      outstanding balances of issued invoices today (not limited by the filters)
 */
public record PaymentReport(long entries, long receipts, long reversals, BigDecimal received, BigDecimal reversed,
                            BigDecimal refunded, BigDecimal netReceived, BigDecimal netReceivedExGst,
                            List<MethodTotal> byMethod, Receivables receivables) {

    public record MethodTotal(String method, String label, long receipts, BigDecimal netReceived) {
    }

    public record Receivables(long outstandingInvoices, BigDecimal outstandingAmount, long overdueInvoices,
                              BigDecimal overdueAmount) {
    }
}
