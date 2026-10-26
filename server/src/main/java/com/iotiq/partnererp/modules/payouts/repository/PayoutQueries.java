package com.iotiq.partnererp.modules.payouts.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Payout eligibility and balances (DECISIONS.md §L).
 *
 * <p>A receipt is eligible for commission while it is not reversed, its invoice carries a fee % above zero, it is dated
 * on or before the payout's as-of date and it is not allocated to a live payout. Refund rows recorded before Phase 10
 * are not receipts and never earn commission. The SQL commission ({@code round(net × fee / 100, 2)}) rounds half away
 * from zero, which for these positive amounts equals {@code PayoutRules.commission}.
 */
@Repository
public class PayoutQueries {

    private static final String ELIGIBLE = """
            FROM payments p
            JOIN invoices i ON i.id = p.invoice_id
           WHERE p.entry_type = 'RECEIPT' AND i.fee_percent > 0 AND p.payment_date <= :asOf
             AND NOT EXISTS (SELECT 1 FROM payments r WHERE r.reversed_payment_id = p.id)
             AND NOT EXISTS (SELECT 1 FROM payout_lines l WHERE l.payment_id = p.id AND l.released_at IS NULL)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PayoutQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record EligibleReceipt(UUID paymentId, String paymentNumber, UUID invoiceId, String invoiceNumber,
                                  LocalDate paymentDate, BigDecimal netAmount, BigDecimal feePercent) {
    }

    /** Eligible, unallocated receipts of the partner, oldest first. */
    public List<EligibleReceipt> eligibleReceipts(UUID partnerId, LocalDate asOf) {
        return jdbc.query("SELECT p.id, p.payment_number, p.invoice_id, i.invoice_number, p.payment_date, p.net_amount,"
                        + " i.fee_percent " + ELIGIBLE + " AND p.partner_id = :partnerId"
                        + " ORDER BY p.payment_date, p.created_at, p.id",
                new MapSqlParameterSource("partnerId", partnerId).addValue("asOf", asOf),
                (rs, i) -> new EligibleReceipt(rs.getObject("id", UUID.class), rs.getString("payment_number"),
                        rs.getObject("invoice_id", UUID.class), rs.getString("invoice_number"),
                        rs.getObject("payment_date", LocalDate.class), rs.getBigDecimal("net_amount"),
                        rs.getBigDecimal("fee_percent")));
    }

    /**
     * Commission of the partner's approved, processing and paid payouts in the financial year (excluding one payout),
     * and the part of it TDS was calculated on. A legacy payout counts as fully taxed when TDS was deducted from it.
     */
    public record PriorFy(BigDecimal commission, BigDecimal taxedBase) {
    }

    public PriorFy priorFy(UUID partnerId, LocalDate fyStart, UUID excludePayoutId) {
        return jdbc.queryForObject("""
                        SELECT COALESCE(SUM(commission_total), 0) AS commission,
                               COALESCE(SUM(CASE WHEN legacy THEN CASE WHEN tds_amount > 0 THEN commission_total ELSE 0 END
                                                 ELSE COALESCE(tds_base, 0) END), 0) AS taxed
                          FROM payouts
                         WHERE partner_id = :partnerId AND fy_start = :fyStart
                           AND status IN ('APPROVED', 'PROCESSING', 'PAID')
                           AND id <> COALESCE(CAST(:exclude AS uuid), '00000000-0000-0000-0000-000000000000'::uuid)
                        """,
                new MapSqlParameterSource("partnerId", partnerId).addValue("fyStart", fyStart)
                        .addValue("exclude", excludePayoutId),
                (rs, i) -> new PriorFy(rs.getBigDecimal("commission"), rs.getBigDecimal("taxed")));
    }

    /**
     * One partner's payout position.
     *
     * @param eligibleCommission commission on eligible receipts not yet allocated to a payout
     * @param carriedAdjustments approved adjustments not yet allocated (negative ones reduce the next payout)
     * @param inProgress         commission of draft, pending, approved and processing payouts
     * @param paidFy             commission of payouts paid in the financial year (gross of TDS)
     */
    public record Balance(UUID partnerId, String partnerName, String partnerCode, boolean panOnFile,
                          boolean panVerified, boolean bankOnFile, long eligibleReceipts,
                          BigDecimal eligibleCommission, BigDecimal carriedAdjustments, long pendingAdjustments,
                          BigDecimal pendingAdjustmentAmount, long inProgressPayouts, BigDecimal inProgress,
                          BigDecimal paidFy, BigDecimal tdsFy, BigDecimal paidTotal, LocalDate lastPaidDate) {

        /** What the next payout run would gross (before TDS): eligible commission plus carried adjustments. */
        public BigDecimal available() {
            return eligibleCommission.add(carriedAdjustments);
        }
    }

    /** Partners with any commission, adjustment or payout, by name; optionally one partner or a name/code search. */
    public List<Balance> balances(UUID partnerId, String like, LocalDate asOf, LocalDate fyStart) {
        MapSqlParameterSource params = new MapSqlParameterSource("asOf", asOf).addValue("fyStart", fyStart);
        List<String> where = new ArrayList<>();
        where.add("(e.partner_id IS NOT NULL OR a.partner_id IS NOT NULL OR po.partner_id IS NOT NULL)");
        if (partnerId != null) {
            where.add("pa.id = :partnerId");
            params.addValue("partnerId", partnerId);
        }
        if (like != null) {
            where.add("(lower(pa.name) LIKE :like ESCAPE '\\' OR lower(pa.partner_code) LIKE :like ESCAPE '\\')");
            params.addValue("like", like);
        }
        return jdbc.query("""
                        WITH e AS (
                            SELECT p.partner_id, COUNT(*) AS n, SUM(round(p.net_amount * i.fee_percent / 100, 2)) AS amount
                        """ + ELIGIBLE + """
                             GROUP BY p.partner_id),
                        a AS (
                            SELECT x.partner_id,
                                   SUM(x.amount) FILTER (WHERE x.status = 'APPROVED' AND NOT EXISTS (
                                       SELECT 1 FROM payout_lines l WHERE l.adjustment_id = x.id AND l.released_at IS NULL))
                                       AS carried,
                                   COUNT(*) FILTER (WHERE x.status = 'PENDING_APPROVAL') AS pending_n,
                                   SUM(x.amount) FILTER (WHERE x.status = 'PENDING_APPROVAL') AS pending
                              FROM partner_adjustments x GROUP BY x.partner_id),
                        po AS (
                            SELECT partner_id,
                                   COUNT(*) FILTER (WHERE status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'PROCESSING'))
                                       AS in_progress_n,
                                   SUM(commission_total) FILTER (WHERE status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED',
                                       'PROCESSING')) AS in_progress,
                                   SUM(commission_total) FILTER (WHERE status = 'PAID' AND fy_start = :fyStart) AS paid_fy,
                                   SUM(tds_amount) FILTER (WHERE status = 'PAID' AND fy_start = :fyStart) AS tds_fy,
                                   SUM(commission_total) FILTER (WHERE status = 'PAID') AS paid_total,
                                   MAX(paid_date) FILTER (WHERE status = 'PAID') AS last_paid
                              FROM payouts GROUP BY partner_id)
                        SELECT pa.id, pa.name, pa.partner_code, pa.pan IS NOT NULL AS pan_on_file,
                               (pa.pan IS NOT NULL AND pa.pan_verified_at IS NOT NULL) AS pan_verified,
                               pa.bank_account IS NOT NULL AS bank_on_file,
                               COALESCE(e.n, 0) AS eligible_n, COALESCE(e.amount, 0) AS eligible,
                               COALESCE(a.carried, 0) AS carried, COALESCE(a.pending_n, 0) AS pending_n,
                               COALESCE(a.pending, 0) AS pending, COALESCE(po.in_progress_n, 0) AS in_progress_n,
                               COALESCE(po.in_progress, 0) AS in_progress, COALESCE(po.paid_fy, 0) AS paid_fy,
                               COALESCE(po.tds_fy, 0) AS tds_fy, COALESCE(po.paid_total, 0) AS paid_total, po.last_paid
                          FROM partners pa
                          LEFT JOIN e ON e.partner_id = pa.id
                          LEFT JOIN a ON a.partner_id = pa.id
                          LEFT JOIN po ON po.partner_id = pa.id
                         WHERE """ + String.join(" AND ", where) + " ORDER BY pa.name, pa.id",
                params,
                (rs, i) -> new Balance(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("partner_code"), rs.getBoolean("pan_on_file"), rs.getBoolean("pan_verified"),
                        rs.getBoolean("bank_on_file"), rs.getLong("eligible_n"), money(rs.getBigDecimal("eligible")),
                        money(rs.getBigDecimal("carried")), rs.getLong("pending_n"), money(rs.getBigDecimal("pending")),
                        rs.getLong("in_progress_n"), money(rs.getBigDecimal("in_progress")),
                        money(rs.getBigDecimal("paid_fy")), money(rs.getBigDecimal("tds_fy")),
                        money(rs.getBigDecimal("paid_total")), rs.getObject("last_paid", LocalDate.class)));
    }

    public record StatusTotal(String status, long count, BigDecimal commission, BigDecimal tds, BigDecimal net) {
    }

    /** Count and amounts of non-legacy payouts per status. */
    public List<StatusTotal> statusTotals() {
        return jdbc.query("""
                        SELECT status, COUNT(*) AS n, COALESCE(SUM(commission_total), 0) AS commission,
                               COALESCE(SUM(tds_amount), 0) AS tds, COALESCE(SUM(net_amount), 0) AS net
                          FROM payouts WHERE NOT legacy GROUP BY status
                        """,
                new MapSqlParameterSource(),
                (rs, i) -> new StatusTotal(rs.getString("status"), rs.getLong("n"),
                        money(rs.getBigDecimal("commission")), money(rs.getBigDecimal("tds")),
                        money(rs.getBigDecimal("net"))));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
