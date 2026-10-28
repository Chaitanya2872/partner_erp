package com.iotiq.partnererp.modules.sequences.service;

import com.iotiq.partnererp.common.exception.BusinessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Allocates {@code PREFIX-YYYY-NNNNNN} numbers (estimates, leads, invoices, payments) from {@code document_sequences}.
 *
 * <p>One upsert both creates the year's row and increments it. PostgreSQL holds the row lock until the calling
 * transaction ends, so concurrent saves queue on it and can never receive the same number. If the caller rolls
 * back, the increment rolls back with it, so committed numbers have no gaps.
 */
@Service
public class DocumentNumberService {

    public static final String ESTIMATE = "EST";
    public static final String LEAD = "LEAD";
    /** Invoices: allocated when a draft is issued (Phase 9 decision 4), never reused after cancellation. */
    public static final String INVOICE = "INV";
    /** Payment ledger entries, receipts and reversals alike (Phase 10). */
    public static final String PAYMENT = "PAY";
    /** Partner payouts (Phase 11); legacy payouts carried over from the accrual table have no number. */
    public static final String PAYOUT = "PO";
    /** Partner adjustments, manual and reversal alike (Phase 11). */
    public static final String ADJUSTMENT = "ADJ";
    public static final int MAX = 999_999;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public DocumentNumberService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Next number for the current calendar year in Asia/Kolkata. Must run inside the saving transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String prefix) {
        int year = LocalDate.now(clock).getYear();
        Integer value = jdbc.queryForObject("""
                INSERT INTO document_sequences (prefix, fy_tag, last_value) VALUES (:prefix, :tag, 1)
                ON CONFLICT (prefix, fy_tag) DO UPDATE SET last_value = document_sequences.last_value + 1
                RETURNING last_value
                """, new MapSqlParameterSource("prefix", prefix).addValue("tag", String.valueOf(year)), Integer.class);
        if (value == null || value > MAX) {
            throw new BusinessException("SEQUENCE_EXHAUSTED",
                    "All " + prefix + " numbers for " + year + " are used. Contact an administrator.");
        }
        return format(prefix, year, value);
    }

    public static String format(String prefix, int year, int value) {
        return String.format("%s-%04d-%06d", prefix, year, value);
    }
}
