package com.iotiq.partnererp.modules.partners.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates across payments, leads and payouts for partner lists, detail tiles and tier reviews.
 */
@Repository
public class PartnerMetricsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PartnerMetricsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * BR-P12: Σ payment net (ex-GST) with {@code payment_date >= since}, per partner. Refunds are negative and
     * reduce the total; future-dated payments count, as in the source. Partners without payments are absent.
     * A reversed receipt never counts, and neither does its reversal (P10-09): the receipt was recorded in error, so
     * it is left out whatever the reversal date.
     */
    public Map<UUID, BigDecimal> paidRevenueSince(Collection<UUID> partnerIds, LocalDate since) {
        if (partnerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> result = new HashMap<>();
        jdbc.query("""
                        SELECT partner_id, SUM(net_amount) AS revenue
                          FROM payments p
                         WHERE partner_id IN (:ids) AND payment_date >= :since AND entry_type <> 'REVERSAL'
                           AND NOT EXISTS (SELECT 1 FROM payments r WHERE r.reversed_payment_id = p.id)
                         GROUP BY partner_id
                        """,
                new MapSqlParameterSource("ids", partnerIds).addValue("since", since),
                rs -> {
                    result.put(rs.getObject("partner_id", UUID.class), rs.getBigDecimal("revenue"));
                });
        return result;
    }

    public Map<UUID, Long> leadCounts(Collection<UUID> partnerIds) {
        if (partnerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> result = new HashMap<>();
        jdbc.query("SELECT partner_id, COUNT(*) AS n FROM leads WHERE partner_id IN (:ids) AND deleted_at IS NULL GROUP BY partner_id",
                new MapSqlParameterSource("ids", partnerIds),
                rs -> {
                    result.put(rs.getObject("partner_id", UUID.class), rs.getLong("n"));
                });
        return result;
    }

    /**
     * Partner commission (gross of TDS) not yet paid and paid (Phase 11). Unpaid: commission on standing receipts not
     * yet in a payout, active lines of payouts not yet paid, and approved adjustments not yet in a payout (negative
     * reversal adjustments reduce it). Paid: commission of paid payouts, legacy ones included.
     */
    public FeeTotals feeTotals(UUID partnerId) {
        return jdbc.queryForObject("""
                        SELECT (SELECT COALESCE(SUM(round(p.net_amount * i.fee_percent / 100, 2)), 0)
                                  FROM payments p JOIN invoices i ON i.id = p.invoice_id
                                 WHERE p.partner_id = :id AND p.entry_type = 'RECEIPT' AND i.fee_percent > 0
                                   AND NOT EXISTS (SELECT 1 FROM payments r WHERE r.reversed_payment_id = p.id)
                                   AND NOT EXISTS (SELECT 1 FROM payout_lines l
                                                    WHERE l.payment_id = p.id AND l.released_at IS NULL))
                             + (SELECT COALESCE(SUM(l.amount), 0)
                                  FROM payout_lines l JOIN payouts o ON o.id = l.payout_id
                                 WHERE o.partner_id = :id AND l.released_at IS NULL
                                   AND o.status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'PROCESSING'))
                             + (SELECT COALESCE(SUM(a.amount), 0)
                                  FROM partner_adjustments a
                                 WHERE a.partner_id = :id AND a.status = 'APPROVED'
                                   AND NOT EXISTS (SELECT 1 FROM payout_lines l
                                                    WHERE l.adjustment_id = a.id AND l.released_at IS NULL)) AS unpaid,
                               (SELECT COALESCE(SUM(commission_total), 0)
                                  FROM payouts WHERE partner_id = :id AND status = 'PAID') AS paid
                        """,
                new MapSqlParameterSource("id", partnerId),
                (rs, i) -> new FeeTotals(rs.getBigDecimal("unpaid"), rs.getBigDecimal("paid")));
    }

    public record FeeTotals(BigDecimal unpaid, BigDecimal paid) {
    }
}
