package com.iotiq.partnererp.modules.invoices.service;

import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.common.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pure invoice rules shared by the service and its unit tests.
 */
public final class InvoiceRules {

    public static final int MAX_LINES = 100;
    public static final int MAX_DUE_DAYS = 365;
    public static final int MAX_ADDRESS = 1000;
    public static final int MAX_REASON = 500;
    public static final int MIN_REASON = 10;
    /** BR-V6: a balance up to ₹0.50 counts as paid. */
    static final BigDecimal PAID_TOLERANCE = new BigDecimal("0.50");

    private InvoiceRules() {
    }

    /**
     * BR-V6 ({@code invStatus}), derived and never stored. Drafts have no payment status. Payments are only ever
     * recorded against issued invoices (Phase 10).
     */
    public static PaymentStatus paymentStatus(InvoiceStatus status, BigDecimal total, BigDecimal paid,
                                              LocalDate dueDate, LocalDate today) {
        if (status == InvoiceStatus.DRAFT) {
            return null;
        }
        if (status == InvoiceStatus.CANCELLED) {
            return PaymentStatus.CANCELLED;
        }
        if (paid.compareTo(total.subtract(PAID_TOLERANCE)) >= 0) {
            return PaymentStatus.PAID;
        }
        boolean overdue = dueDate != null && dueDate.isBefore(today);
        if (paid.signum() > 0) {
            return overdue ? PaymentStatus.PARTLY_PAID_OVERDUE : PaymentStatus.PARTLY_PAID;
        }
        return overdue ? PaymentStatus.OVERDUE : PaymentStatus.UNPAID;
    }
}
