package com.iotiq.partnererp.modules.dashboard.repository;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.modules.reports.service.ReportPeriod;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Server-side aggregation for the dashboard and analytics (Phase 12). Every figure is one SQL aggregate over the
 * ledger tables, never a loop over rows. Shared rules (DECISIONS.md §M):
 * <ul>
 *   <li>Live records only: soft-deleted leads, estimates and invoices are left out; drafts never count as invoiced.</li>
 *   <li>Collections come from the payment ledger: a receipt counts unless a reversal points at it, and reversal
 *       entries are reported separately ({@link #STANDING}, as {@code PartnerMetricsRepository#paidRevenueSince}).
 *       Invoice payment status (the ₹0.50 tolerance) is never used as a collection.</li>
 *   <li>Commission uses the invoice's fee % snapshot, rounded per receipt (HALF_UP, 2 decimals), never the partner's
 *       current tier.</li>
 *   <li>Won and lost come from the stage history: the latest move into the lead's current closed stage, so a lead
 *       that was reopened and closed again counts once, and a reopened lead not at all.</li>
 * </ul>
 */
@Repository
public class DashboardQueries {

    /** A ledger entry that still stands: not a reversal, and not reversed. Legacy refunds stay and reduce totals. */
    public static final String STANDING = "p.entry_type <> 'REVERSAL' AND NOT EXISTS "
            + "(SELECT 1 FROM payments r WHERE r.reversed_payment_id = p.id)";

    /** Live issued invoices with the Σ of their ledger entries (reversals are negative), as {@code v_invoice_balance}. */
    private static final String BALANCES = """
            SELECT i.id, i.invoice_number, i.partner_id, i.invoice_date, i.due_date, i.grand_total,
                   COALESCE(pd.paid, 0) AS paid, i.grand_total - COALESCE(pd.paid, 0) AS balance
              FROM invoices i
              LEFT JOIN (SELECT invoice_id, SUM(amount) AS paid FROM payments GROUP BY invoice_id) pd ON pd.invoice_id = i.id
             WHERE i.status = 'ISSUED' AND i.deleted_at IS NULL""";

    /** Closed (Won/Lost) live leads with the time of their latest move into that stage. */
    private static final String CLOSED_LEADS = """
            SELECT l.id AS lead_id, l.partner_id, l.stage, COALESCE(c.closed_at, l.created_at) AS closed_at
              FROM leads l
              LEFT JOIN LATERAL (SELECT MAX(h.occurred_at) AS closed_at FROM lead_stage_history h
                                  WHERE h.lead_id = l.id AND h.to_stage = l.stage
                                    AND h.from_stage IS DISTINCT FROM h.to_stage) c ON true
             WHERE l.deleted_at IS NULL AND l.stage IN ('WON', 'LOST')""";

    /** A live lead that is neither Won nor Lost. */
    public static final String OPEN_LEAD = "l.deleted_at IS NULL AND l.stage NOT IN ('WON', 'LOST')";

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static MapSqlParameterSource params(ReportPeriod p, LocalDate today) {
        return new MapSqlParameterSource("from", p.from()).addValue("to", p.to())
                .addValue("prevFrom", p.previousFrom()).addValue("prevTo", p.previousTo())
                .addValue("tsFrom", ts(p.fromInstant())).addValue("tsTo", ts(p.toInstant()))
                .addValue("prevTsFrom", ts(p.previousFromInstant()))
                .addValue("today", today).addValue("zone", AppConstants.BUSINESS_ZONE.getId());
    }

    public static OffsetDateTime ts(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    // ------------------------------------------------------------------------------------------------ partners

    public record PartnerCounts(long total, long active, long inactive, long prospect, long onboarding, long created,
                                long createdPrevious) {
    }

    public PartnerCounts partnerCounts(MapSqlParameterSource params) {
        return jdbc.queryForObject("""
                        SELECT COUNT(*) AS total,
                               COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active,
                               COUNT(*) FILTER (WHERE status = 'INACTIVE') AS inactive,
                               COUNT(*) FILTER (WHERE status = 'PROSPECT') AS prospect,
                               COUNT(*) FILTER (WHERE status = 'ONBOARDING') AS onboarding,
                               COUNT(*) FILTER (WHERE created_at >= :tsFrom AND created_at < :tsTo) AS created,
                               COUNT(*) FILTER (WHERE created_at >= :prevTsFrom AND created_at < :tsFrom) AS created_prev
                          FROM partners
                        """, params, (rs, i) -> new PartnerCounts(rs.getLong("total"), rs.getLong("active"),
                rs.getLong("inactive"), rs.getLong("prospect"), rs.getLong("onboarding"), rs.getLong("created"),
                rs.getLong("created_prev")));
    }

    public record Row(String key, String label, long count, BigDecimal amount) {
    }

    /** Partners that aren't Inactive, by tier (source tier mix); partners without a tier are "NONE". */
    public List<Row> tierMix() {
        return jdbc.query("""
                        SELECT COALESCE(t.tier_level::text, 'NONE') AS key, COALESCE(t.name, 'No tier') AS label,
                               COUNT(*) AS n
                          FROM partners p LEFT JOIN partner_tiers t ON t.id = p.tier_id
                         WHERE p.status <> 'INACTIVE'
                         GROUP BY t.tier_level, t.name
                         ORDER BY t.tier_level NULLS LAST
                        """, (rs, i) -> new Row(rs.getString("key"), rs.getString("label"), rs.getLong("n"), null));
    }

    /** New partners per creation day in the period. */
    public List<Daily> partnersCreatedDaily(MapSqlParameterSource params) {
        return jdbc.query("""
                        SELECT (created_at AT TIME ZONE :zone)::date AS d, COUNT(*) AS a, 0 AS b, 0 AS c
                          FROM partners WHERE created_at >= :tsFrom AND created_at < :tsTo GROUP BY 1
                        """, params, DashboardQueries::daily);
    }

    // ------------------------------------------------------------------------------------------------ leads

    public record LeadCounts(long total, long created, long createdPrevious, long open, BigDecimal pipeline,
                             long openWithoutBudget, long wonTotal, long wonInPeriod, long lostInPeriod) {
    }

    public LeadCounts leadCounts(MapSqlParameterSource params) {
        return jdbc.queryForObject("""
                        WITH closed AS (""" + CLOSED_LEADS + """
                        )
                        SELECT COUNT(*) AS total,
                               COUNT(*) FILTER (WHERE l.created_at >= :tsFrom AND l.created_at < :tsTo) AS created,
                               COUNT(*) FILTER (WHERE l.created_at >= :prevTsFrom AND l.created_at < :tsFrom) AS created_prev,
                               COUNT(*) FILTER (WHERE l.stage NOT IN ('WON', 'LOST')) AS open,
                               COALESCE(SUM(l.budget) FILTER (WHERE l.stage NOT IN ('WON', 'LOST')), 0) AS pipeline,
                               COUNT(*) FILTER (WHERE l.stage NOT IN ('WON', 'LOST') AND l.budget IS NULL) AS no_budget,
                               COUNT(*) FILTER (WHERE l.stage = 'WON') AS won_total,
                               (SELECT COUNT(*) FROM closed WHERE stage = 'WON' AND closed_at >= :tsFrom AND closed_at < :tsTo) AS won,
                               (SELECT COUNT(*) FROM closed WHERE stage = 'LOST' AND closed_at >= :tsFrom AND closed_at < :tsTo) AS lost
                          FROM leads l WHERE l.deleted_at IS NULL
                        """, params, (rs, i) -> new LeadCounts(rs.getLong("total"), rs.getLong("created"),
                rs.getLong("created_prev"), rs.getLong("open"), rs.getBigDecimal("pipeline"), rs.getLong("no_budget"),
                rs.getLong("won_total"), rs.getLong("won"), rs.getLong("lost")));
    }

    /** Current live leads by stage, with budget. */
    public List<Row> leadFunnel() {
        return jdbc.query("""
                        SELECT stage, COUNT(*) AS n, COALESCE(SUM(budget), 0) AS budget
                          FROM leads WHERE deleted_at IS NULL GROUP BY stage
                        """, (rs, i) -> new Row(rs.getString("stage"), null, rs.getLong("n"), rs.getBigDecimal("budget")));
    }

    /** Leads created (a), won (b) and lost (c) per day of the period. */
    public List<Daily> leadsDaily(MapSqlParameterSource params) {
        return jdbc.query("""
                        WITH closed AS (""" + CLOSED_LEADS + """
                        )
                        SELECT d, SUM(a) AS a, SUM(b) AS b, SUM(c) AS c FROM (
                            SELECT (created_at AT TIME ZONE :zone)::date AS d, 1 AS a, 0 AS b, 0 AS c
                              FROM leads WHERE deleted_at IS NULL AND created_at >= :tsFrom AND created_at < :tsTo
                            UNION ALL
                            SELECT (closed_at AT TIME ZONE :zone)::date, 0, CASE WHEN stage = 'WON' THEN 1 ELSE 0 END,
                                   CASE WHEN stage = 'LOST' THEN 1 ELSE 0 END
                              FROM closed WHERE closed_at >= :tsFrom AND closed_at < :tsTo) x
                         GROUP BY d
                        """, params, DashboardQueries::daily);
    }

    /** Open leads by days since creation. */
    public List<Row> openLeadAging(MapSqlParameterSource params) {
        return jdbc.query("""
                        SELECT CASE WHEN age <= 7 THEN 'D0_7' WHEN age <= 30 THEN 'D8_30' WHEN age <= 60 THEN 'D31_60'
                                    WHEN age <= 90 THEN 'D61_90' ELSE 'D90_PLUS' END AS key,
                               COUNT(*) AS n, COALESCE(SUM(budget), 0) AS budget
                          FROM (SELECT CAST(:today AS date) - (l.created_at AT TIME ZONE :zone)::date AS age, l.budget
                                  FROM leads l WHERE""" + " " + OPEN_LEAD + """
                        ) x
                         GROUP BY 1
                        """, params, (rs, i) -> new Row(rs.getString("key"), null, rs.getLong("n"), rs.getBigDecimal("budget")));
    }

    public record FollowUpCounts(long pastExpectedClose, long unacknowledged, long awaitingEstimate) {
    }

    /**
     * Open leads needing follow-up today: past their expected close date, not acknowledged within the SLA, or
     * acknowledged without an estimate within the SLA. The SLA tests are those of {@code LeadSla} for open leads.
     */
    public FollowUpCounts followUps(MapSqlParameterSource params, Instant now, int ackHours, int estimateDays) {
        MapSqlParameterSource p = withSla(params, now, ackHours, estimateDays);
        return jdbc.queryForObject("""
                        SELECT COUNT(*) FILTER (WHERE l.expected_close < :today) AS past_close,
                               COUNT(*) FILTER (WHERE""" + " " + UNACKNOWLEDGED + """
                        ) AS unack,
                               COUNT(*) FILTER (WHERE""" + " " + AWAITING_ESTIMATE + """
                        ) AS awaiting
                          FROM leads l WHERE""" + " " + OPEN_LEAD,
                p, (rs, i) -> new FollowUpCounts(rs.getLong("past_close"), rs.getLong("unack"), rs.getLong("awaiting")));
    }

    public static final String UNACKNOWLEDGED = "l.acknowledged_at IS NULL AND CAST(:now AS timestamptz) - l.created_at "
            + ">= make_interval(secs => :ackSecs)";
    public static final String AWAITING_ESTIMATE = "l.acknowledged_at IS NOT NULL AND NOT EXISTS (SELECT 1 FROM estimates e "
            + "WHERE e.lead_id = l.id AND e.deleted_at IS NULL) AND CAST(:today AS date) "
            + "- (l.acknowledged_at AT TIME ZONE :zone)::date > :estDays";

    public static MapSqlParameterSource withSla(MapSqlParameterSource params, Instant now, int ackHours,
                                                int estimateDays) {
        // As LeadMetricsRepository#slaBreachedIds: rounded hours exceed the target from target + 0.5 h.
        return new MapSqlParameterSource(params.getValues()).addValue("now", ts(now))
                .addValue("ackSecs", ackHours * 3600L + 1800L).addValue("estDays", estimateDays);
    }

    /** How many of {@code ids} were created in the period. */
    public long createdInPeriod(Collection<UUID> ids, MapSqlParameterSource params) {
        if (ids.isEmpty()) {
            return 0;
        }
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM leads WHERE id IN (:ids) AND created_at >= :tsFrom "
                + "AND created_at < :tsTo", new MapSqlParameterSource(params.getValues()).addValue("ids", ids), Long.class);
        return n == null ? 0 : n;
    }

    public record PartnerLeadRow(UUID partnerId, String name, long created, long open, long won, long lost,
                                 BigDecimal pipeline) {
    }

    /** Lead activity per partner: created, won and lost in the period; open and pipeline now. Busiest first. */
    public List<PartnerLeadRow> leadsByPartner(MapSqlParameterSource params, int limit) {
        return jdbc.query("""
                        WITH closed AS (""" + CLOSED_LEADS + """
                        ),
                        agg AS (
                            SELECT l.partner_id,
                                   COUNT(*) FILTER (WHERE l.created_at >= :tsFrom AND l.created_at < :tsTo) AS created,
                                   COUNT(*) FILTER (WHERE l.stage NOT IN ('WON', 'LOST')) AS open,
                                   COALESCE(SUM(l.budget) FILTER (WHERE l.stage NOT IN ('WON', 'LOST')), 0) AS pipeline,
                                   COUNT(c.lead_id) FILTER (WHERE c.stage = 'WON' AND c.closed_at >= :tsFrom AND c.closed_at < :tsTo) AS won,
                                   COUNT(c.lead_id) FILTER (WHERE c.stage = 'LOST' AND c.closed_at >= :tsFrom AND c.closed_at < :tsTo) AS lost
                              FROM leads l LEFT JOIN closed c ON c.lead_id = l.id
                             WHERE l.deleted_at IS NULL
                             GROUP BY l.partner_id)
                        SELECT p.id, p.name, agg.created, agg.open, agg.won, agg.lost, agg.pipeline
                          FROM agg JOIN partners p ON p.id = agg.partner_id
                         WHERE agg.created > 0 OR agg.open > 0 OR agg.won > 0 OR agg.lost > 0
                         ORDER BY agg.created DESC, agg.won DESC, agg.open DESC, p.name
                         LIMIT :limit
                        """, new MapSqlParameterSource(params.getValues()).addValue("limit", limit),
                (rs, i) -> new PartnerLeadRow(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getLong("created"), rs.getLong("open"), rs.getLong("won"), rs.getLong("lost"),
                        rs.getBigDecimal("pipeline")));
    }

    /** Leads created in the period, and how many of them have a live estimate. */
    public long[] leadToEstimate(MapSqlParameterSource params) {
        return jdbc.queryForObject("""
                        SELECT COUNT(*) AS created,
                               COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM estimates e
                                                               WHERE e.lead_id = l.id AND e.deleted_at IS NULL)) AS with_estimate
                          FROM leads l
                         WHERE l.deleted_at IS NULL AND l.created_at >= :tsFrom AND l.created_at < :tsTo
                        """, params, (rs, i) -> new long[] {rs.getLong("created"), rs.getLong("with_estimate")});
    }

    // ------------------------------------------------------------------------------------------------ estimates

    public record EstimateCounts(long total, long draft, long sent, long accepted, long rejected, long invoiced,
                                 BigDecimal value, long converted) {
    }

    /**
     * Live estimates dated in the period by current status. Value is the grand total of non-draft estimates;
     * converted counts non-draft estimates with a live issued invoice.
     */
    public EstimateCounts estimateCounts(MapSqlParameterSource params) {
        return jdbc.queryForObject("""
                        SELECT COUNT(*) AS total,
                               COUNT(*) FILTER (WHERE e.status = 'DRAFT') AS draft,
                               COUNT(*) FILTER (WHERE e.status = 'SENT') AS sent,
                               COUNT(*) FILTER (WHERE e.status = 'ACCEPTED') AS accepted,
                               COUNT(*) FILTER (WHERE e.status = 'REJECTED') AS rejected,
                               COUNT(*) FILTER (WHERE e.status = 'INVOICED') AS invoiced,
                               COALESCE(SUM(e.grand_total) FILTER (WHERE e.status <> 'DRAFT'), 0) AS value,
                               COUNT(*) FILTER (WHERE e.status <> 'DRAFT' AND EXISTS (
                                   SELECT 1 FROM invoices i WHERE i.estimate_id = e.id AND i.status = 'ISSUED'
                                      AND i.deleted_at IS NULL)) AS converted
                          FROM estimates e
                         WHERE e.deleted_at IS NULL AND e.estimate_date BETWEEN :from AND :to
                        """, params, (rs, i) -> new EstimateCounts(rs.getLong("total"), rs.getLong("draft"),
                rs.getLong("sent"), rs.getLong("accepted"), rs.getLong("rejected"), rs.getLong("invoiced"),
                rs.getBigDecimal("value"), rs.getLong("converted")));
    }

    // ------------------------------------------------------------------------------------------------ invoices

    public record InvoiceTotals(long issued, BigDecimal gross, BigDecimal taxable, BigDecimal gst,
                                BigDecimal taxablePrevious, long cancelled, BigDecimal cancelledValue) {
    }

    /** Issued (live, not cancelled) invoices dated in the period; cancelled ones separately; drafts never. */
    public InvoiceTotals invoiceTotals(MapSqlParameterSource params) {
        return jdbc.queryForObject("""
                        SELECT COUNT(*) FILTER (WHERE status = 'ISSUED' AND invoice_date >= :from) AS issued,
                               COALESCE(SUM(grand_total) FILTER (WHERE status = 'ISSUED' AND invoice_date >= :from), 0) AS gross,
                               COALESCE(SUM(taxable_amount) FILTER (WHERE status = 'ISSUED' AND invoice_date >= :from), 0) AS taxable,
                               COALESCE(SUM(gst_amount) FILTER (WHERE status = 'ISSUED' AND invoice_date >= :from), 0) AS gst,
                               COALESCE(SUM(taxable_amount) FILTER (WHERE status = 'ISSUED' AND invoice_date <= :prevTo), 0) AS taxable_prev,
                               COUNT(*) FILTER (WHERE status = 'CANCELLED' AND invoice_date >= :from) AS cancelled,
                               COALESCE(SUM(grand_total) FILTER (WHERE status = 'CANCELLED' AND invoice_date >= :from), 0) AS cancelled_value
                          FROM invoices
                         WHERE deleted_at IS NULL AND status IN ('ISSUED', 'CANCELLED') AND invoice_date BETWEEN :prevFrom AND :to
                        """, params, (rs, i) -> new InvoiceTotals(rs.getLong("issued"), rs.getBigDecimal("gross"),
                rs.getBigDecimal("taxable"), rs.getBigDecimal("gst"), rs.getBigDecimal("taxable_prev"),
                rs.getLong("cancelled"), rs.getBigDecimal("cancelled_value")));
    }

    public record Receivables(long count, BigDecimal outstanding, long overdueCount, BigDecimal overdue,
                              long toleranceCount, BigDecimal toleranceResidual, long partlyPaid,
                              BigDecimal partlyPaidOutstanding) {
    }

    /**
     * Today's receivables over every live issued invoice (not limited to the period). A balance above ₹0.50 is
     * outstanding; a balance of ₹0.50 or less is the BR-V6 tolerance residual, reported separately and never as
     * collected. Overdue: due before today.
     */
    public Receivables receivables(MapSqlParameterSource params) {
        return jdbc.queryForObject("WITH b AS (" + BALANCES + """
                        )
                        SELECT COUNT(*) FILTER (WHERE balance > 0.50) AS n,
                               COALESCE(SUM(balance) FILTER (WHERE balance > 0.50), 0) AS outstanding,
                               COUNT(*) FILTER (WHERE balance > 0.50 AND due_date < :today) AS overdue_n,
                               COALESCE(SUM(balance) FILTER (WHERE balance > 0.50 AND due_date < :today), 0) AS overdue,
                               COUNT(*) FILTER (WHERE balance > 0 AND balance <= 0.50) AS tolerance_n,
                               COALESCE(SUM(balance) FILTER (WHERE balance > 0 AND balance <= 0.50), 0) AS tolerance,
                               COUNT(*) FILTER (WHERE balance > 0.50 AND paid > 0) AS partly_n,
                               COALESCE(SUM(balance) FILTER (WHERE balance > 0.50 AND paid > 0), 0) AS partly
                          FROM b
                        """, params, (rs, i) -> new Receivables(rs.getLong("n"), rs.getBigDecimal("outstanding"),
                rs.getLong("overdue_n"), rs.getBigDecimal("overdue"), rs.getLong("tolerance_n"),
                rs.getBigDecimal("tolerance"), rs.getLong("partly_n"), rs.getBigDecimal("partly")));
    }

    /** Outstanding balances (above ₹0.50) by days past due: Not due, 1–30, 31–60, 61–90, 90+. */
    public List<Row> receivableAging(MapSqlParameterSource params) {
        return jdbc.query("WITH b AS (" + BALANCES + """
                        )
                        SELECT CASE WHEN due_date IS NULL OR due_date >= :today THEN 'NOT_DUE'
                                    WHEN CAST(:today AS date) - due_date <= 30 THEN 'D1_30'
                                    WHEN CAST(:today AS date) - due_date <= 60 THEN 'D31_60'
                                    WHEN CAST(:today AS date) - due_date <= 90 THEN 'D61_90'
                                    ELSE 'D90_PLUS' END AS key,
                               COUNT(*) AS n, SUM(balance) AS amount
                          FROM b WHERE balance > 0.50 GROUP BY 1
                        """, params, (rs, i) -> new Row(rs.getString("key"), null, rs.getLong("n"), rs.getBigDecimal("amount")));
    }

    /** Invoiced gross (a) and taxable (b) per invoice day in the period (live issued invoices). */
    public List<Daily> invoicedDaily(MapSqlParameterSource params) {
        return jdbc.query("""
                        SELECT invoice_date AS d, SUM(grand_total) AS a, SUM(taxable_amount) AS b, 0 AS c
                          FROM invoices
                         WHERE status = 'ISSUED' AND deleted_at IS NULL AND invoice_date BETWEEN :from AND :to
                         GROUP BY 1
                        """, params, DashboardQueries::daily);
    }

    // ------------------------------------------------------------------------------------------------ payments

    public record Collections(long receipts, BigDecimal gross, BigDecimal net, BigDecimal netPrevious,
                              long reversals, BigDecimal reversed) {
    }

    /**
     * Realised collections in the period: standing ledger entries by payment date (gross incl. GST and net ex-GST).
     * Reversal entries dated in the period are counted separately as positive amounts.
     */
    public Collections collections(MapSqlParameterSource params) {
        return jdbc.queryForObject("""
                        SELECT COUNT(*) FILTER (WHERE p.entry_type = 'RECEIPT' AND p.payment_date >= :from AND""" + " " + STANDING + """
                        ) AS receipts,
                               COALESCE(SUM(p.amount) FILTER (WHERE p.payment_date >= :from AND""" + " " + STANDING + """
                        ), 0) AS gross,
                               COALESCE(SUM(p.net_amount) FILTER (WHERE p.payment_date >= :from AND""" + " " + STANDING + """
                        ), 0) AS net,
                               COALESCE(SUM(p.net_amount) FILTER (WHERE p.payment_date <= :prevTo AND""" + " " + STANDING + """
                        ), 0) AS net_prev,
                               COUNT(*) FILTER (WHERE p.entry_type = 'REVERSAL' AND p.payment_date >= :from) AS reversals,
                               COALESCE(-SUM(p.amount) FILTER (WHERE p.entry_type = 'REVERSAL' AND p.payment_date >= :from), 0) AS reversed
                          FROM payments p WHERE p.payment_date BETWEEN :prevFrom AND :to
                        """, params, (rs, i) -> new Collections(rs.getLong("receipts"), rs.getBigDecimal("gross"),
                rs.getBigDecimal("net"), rs.getBigDecimal("net_prev"), rs.getLong("reversals"),
                rs.getBigDecimal("reversed")));
    }

    /** Standing receipts in the period by method. */
    public List<Row> paymentMethods(MapSqlParameterSource params) {
        return jdbc.query("""
                        SELECT p.payment_method AS key, COUNT(*) FILTER (WHERE p.entry_type = 'RECEIPT') AS n,
                               SUM(p.amount) AS amount
                          FROM payments p WHERE p.payment_date BETWEEN :from AND :to AND""" + " " + STANDING + """

                         GROUP BY 1 ORDER BY 3 DESC
                        """, params, (rs, i) -> new Row(rs.getString("key"), null, rs.getLong("n"), rs.getBigDecimal("amount")));
    }

    /** Collected gross (a) and ex-GST (b) per payment day in the period (standing entries). */
    public List<Daily> collectedDaily(MapSqlParameterSource params) {
        return jdbc.query("""
                        SELECT p.payment_date AS d, SUM(p.amount) AS a, SUM(p.net_amount) AS b, 0 AS c
                          FROM payments p WHERE p.payment_date BETWEEN :from AND :to AND""" + " " + STANDING + """

                         GROUP BY 1
                        """, params, DashboardQueries::daily);
    }

    public record PartnerRevenue(UUID partnerId, String name, String code, String tier, String status,
                                 BigDecimal gross, BigDecimal net, BigDecimal commission, BigDecimal invoicedTaxable) {
    }

    /** Realised revenue in the period per partner, highest ex-GST collection first, with commission earned on it. */
    public List<PartnerRevenue> partnerRevenue(MapSqlParameterSource params, int limit) {
        return jdbc.query("""
                        WITH col AS (
                            SELECT p.partner_id, SUM(p.amount) AS gross, SUM(p.net_amount) AS net,
                                   SUM(CASE WHEN p.entry_type = 'RECEIPT' AND i.fee_percent > 0
                                            THEN round(p.net_amount * i.fee_percent / 100, 2) ELSE 0 END) AS commission
                              FROM payments p JOIN invoices i ON i.id = p.invoice_id
                             WHERE p.payment_date BETWEEN :from AND :to AND""" + " " + STANDING + """

                             GROUP BY p.partner_id),
                        inv AS (
                            SELECT partner_id, SUM(taxable_amount) AS taxable FROM invoices
                             WHERE status = 'ISSUED' AND deleted_at IS NULL AND invoice_date BETWEEN :from AND :to
                             GROUP BY partner_id)
                        SELECT pa.id, pa.name, pa.partner_code, t.name AS tier, pa.status, col.gross, col.net,
                               col.commission, COALESCE(inv.taxable, 0) AS taxable
                          FROM col JOIN partners pa ON pa.id = col.partner_id
                          LEFT JOIN partner_tiers t ON t.id = pa.tier_id
                          LEFT JOIN inv ON inv.partner_id = pa.id
                         ORDER BY col.net DESC, pa.name
                         LIMIT :limit
                        """, new MapSqlParameterSource(params.getValues()).addValue("limit", limit),
                (rs, i) -> new PartnerRevenue(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("partner_code"), rs.getString("tier"), rs.getString("status"),
                        rs.getBigDecimal("gross"), rs.getBigDecimal("net"), rs.getBigDecimal("commission"),
                        rs.getBigDecimal("taxable")));
    }

    // ------------------------------------------------------------------------------------------------ payouts

    public record PayoutTotals(BigDecimal earned, BigDecimal earnedPrevious, BigDecimal unbilled, long paidCount,
                               BigDecimal paidCommission, BigDecimal paidTds, BigDecimal paidNet,
                               long negativeAdjustments, BigDecimal negativeBalance, long awaitingApproval) {
    }

    /**
     * Commission earned in the period (standing receipts × the invoice's fee %), commission not yet in any payout,
     * payouts paid in the period, and approved negative adjustments not yet carried into a payout.
     */
    public PayoutTotals payoutTotals(MapSqlParameterSource params) {
        return jdbc.queryForObject("""
                        SELECT (SELECT COALESCE(SUM(round(p.net_amount * i.fee_percent / 100, 2)), 0)
                                  FROM payments p JOIN invoices i ON i.id = p.invoice_id
                                 WHERE p.entry_type = 'RECEIPT' AND i.fee_percent > 0 AND""" + " " + STANDING + """

                                   AND p.payment_date BETWEEN :from AND :to) AS earned,
                               (SELECT COALESCE(SUM(round(p.net_amount * i.fee_percent / 100, 2)), 0)
                                  FROM payments p JOIN invoices i ON i.id = p.invoice_id
                                 WHERE p.entry_type = 'RECEIPT' AND i.fee_percent > 0 AND""" + " " + STANDING + """

                                   AND p.payment_date BETWEEN :prevFrom AND :prevTo) AS earned_prev,
                               (SELECT COALESCE(SUM(round(p.net_amount * i.fee_percent / 100, 2)), 0)
                                  FROM payments p JOIN invoices i ON i.id = p.invoice_id
                                 WHERE p.entry_type = 'RECEIPT' AND i.fee_percent > 0 AND""" + " " + STANDING + """

                                   AND NOT EXISTS (SELECT 1 FROM payout_lines l
                                                    WHERE l.payment_id = p.id AND l.released_at IS NULL)) AS unbilled,
                               (SELECT COUNT(*) FROM payouts WHERE status = 'PAID' AND paid_date BETWEEN :from AND :to) AS paid_n,
                               (SELECT COALESCE(SUM(commission_total), 0) FROM payouts
                                 WHERE status = 'PAID' AND paid_date BETWEEN :from AND :to) AS paid_commission,
                               (SELECT COALESCE(SUM(tds_amount), 0) FROM payouts
                                 WHERE status = 'PAID' AND paid_date BETWEEN :from AND :to) AS paid_tds,
                               (SELECT COALESCE(SUM(COALESCE(net_amount, commission_total - COALESCE(tds_amount, 0))), 0)
                                  FROM payouts WHERE status = 'PAID' AND paid_date BETWEEN :from AND :to) AS paid_net,
                               (SELECT COUNT(*) FROM partner_adjustments a WHERE""" + " " + OPEN_NEGATIVE_ADJUSTMENT + """
                        ) AS neg_n,
                               (SELECT COALESCE(SUM(a.amount), 0) FROM partner_adjustments a WHERE""" + " " + OPEN_NEGATIVE_ADJUSTMENT + """
                        ) AS neg,
                               (SELECT COUNT(*) FROM partner_adjustments WHERE status = 'PENDING_APPROVAL') AS awaiting
                        """, params, (rs, i) -> new PayoutTotals(rs.getBigDecimal("earned"),
                rs.getBigDecimal("earned_prev"), rs.getBigDecimal("unbilled"), rs.getLong("paid_n"),
                rs.getBigDecimal("paid_commission"), rs.getBigDecimal("paid_tds"), rs.getBigDecimal("paid_net"),
                rs.getLong("neg_n"), rs.getBigDecimal("neg"), rs.getLong("awaiting")));
    }

    /** An approved negative adjustment not yet carried into a live payout line. */
    public static final String OPEN_NEGATIVE_ADJUSTMENT = "a.status = 'APPROVED' AND a.amount < 0 AND NOT EXISTS "
            + "(SELECT 1 FROM payout_lines l WHERE l.adjustment_id = a.id AND l.released_at IS NULL)";

    // ------------------------------------------------------------------------------------------------ products

    public record ProductCounts(long total, long active, long inactive, long missingPartnerPrice, long missingHsn,
                                long missingB2cPrice) {
    }

    /** Price-sheet completeness of active products. The B2C count is only shown to internal-B2C viewers. */
    public ProductCounts productCounts() {
        return jdbc.queryForObject("""
                        SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE active) AS active,
                               COUNT(*) FILTER (WHERE NOT active) AS inactive,
                               COUNT(*) FILTER (WHERE active AND partner_price = 0) AS no_price,
                               COUNT(*) FILTER (WHERE active AND (hsn IS NULL OR btrim(hsn) = '')) AS no_hsn,
                               COUNT(*) FILTER (WHERE active AND b2c_price IS NULL) AS no_b2c
                          FROM products
                        """, new MapSqlParameterSource(), (rs, i) -> new ProductCounts(rs.getLong("total"),
                rs.getLong("active"), rs.getLong("inactive"), rs.getLong("no_price"), rs.getLong("no_hsn"),
                rs.getLong("no_b2c")));
    }

    public List<Row> productCategories() {
        return jdbc.query("SELECT category, COUNT(*) AS n FROM products GROUP BY category ORDER BY category",
                (rs, i) -> new Row(rs.getString("category"), null, rs.getLong("n"), null));
    }

    public record ProductSalesRow(UUID productId, String sku, String name, String category, BigDecimal quantity,
                                  BigDecimal taxable, long invoices) {
    }

    /**
     * Sales per product from the invoice line snapshots of live issued invoices dated in the period: quantity and
     * taxable value as invoiced. Lines without a product are grouped under a null product id. B2C names and prices
     * on the lines are never read.
     */
    public List<ProductSalesRow> productSales(MapSqlParameterSource params) {
        return jdbc.query("""
                        SELECT ii.product_id, pr.sku, COALESCE(pr.partner_name, MIN(ii.description)) AS name,
                               pr.category, SUM(ii.quantity) AS qty, SUM(ii.taxable_value) AS taxable,
                               COUNT(DISTINCT i.id) AS invoices
                          FROM invoice_items ii
                          JOIN invoices i ON i.id = ii.invoice_id
                          LEFT JOIN products pr ON pr.id = ii.product_id
                         WHERE i.status = 'ISSUED' AND i.deleted_at IS NULL AND i.invoice_date BETWEEN :from AND :to
                         GROUP BY ii.product_id, pr.sku, pr.partner_name, pr.category
                        """, params, (rs, i) -> new ProductSalesRow(rs.getObject("product_id", UUID.class),
                rs.getString("sku"), rs.getString("name"), rs.getString("category"), rs.getBigDecimal("qty"),
                rs.getBigDecimal("taxable"), rs.getLong("invoices")));
    }

    // ------------------------------------------------------------------------------------------------ alerts

    public record AlertRow(String id, String label, String detail, long total, BigDecimal amount) {
    }

    /**
     * The first {@code limit} rows of an alert query, each carrying the full count and amount (window functions), so
     * one query gives both. The SQL must select {@code id, label, detail, amount} and {@code ord}, the sort key.
     */
    public List<AlertRow> alert(String sql, MapSqlParameterSource params, int limit) {
        return jdbc.query("SELECT x.*, COUNT(*) OVER () AS total_n, SUM(x.amount) OVER () AS total_amount FROM ("
                        + sql + ") x ORDER BY x.ord, x.id LIMIT :alertLimit",
                new MapSqlParameterSource(params.getValues()).addValue("alertLimit", limit),
                (rs, i) -> new AlertRow(rs.getString("id"), rs.getString("label"), rs.getString("detail"),
                        rs.getLong("total_n"), rs.getBigDecimal("total_amount")));
    }

    /** Whether a confirmed TDS configuration is in force today. */
    public boolean tdsConfirmed(MapSqlParameterSource params) {
        Boolean confirmed = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM tds_configurations WHERE status = "
                + "'CONFIRMED' AND effective_from <= :today)", params, Boolean.class);
        return Boolean.TRUE.equals(confirmed);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Per-day values a, b, c of a trend query. */
    public record Daily(LocalDate day, BigDecimal a, BigDecimal b, BigDecimal c) {
    }

    private static Daily daily(ResultSet rs, int i) throws SQLException {
        return new Daily(rs.getObject("d", LocalDate.class), rs.getBigDecimal("a"), rs.getBigDecimal("b"),
                rs.getBigDecimal("c"));
    }
}
