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
     */
    public Map<UUID, BigDecimal> paidRevenueSince(Collection<UUID> partnerIds, LocalDate since) {
        if (partnerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> result = new HashMap<>();
        jdbc.query("""
                        SELECT partner_id, SUM(net_amount) AS revenue
                          FROM payments
                         WHERE partner_id IN (:ids) AND payment_date >= :since
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

    /** Referral fees owed (accrued, not yet paid) and paid, gross of TDS; clawbacks are negative. */
    public FeeTotals feeTotals(UUID partnerId) {
        return jdbc.queryForObject("""
                        SELECT COALESCE(SUM(gross_commission) FILTER (WHERE status = 'ACCRUED'), 0) AS unpaid,
                               COALESCE(SUM(gross_commission) FILTER (WHERE status = 'PAID'), 0)    AS paid
                          FROM partner_payouts
                         WHERE partner_id = :id
                        """,
                new MapSqlParameterSource("id", partnerId),
                (rs, i) -> new FeeTotals(rs.getBigDecimal("unpaid"), rs.getBigDecimal("paid")));
    }

    public record FeeTotals(BigDecimal unpaid, BigDecimal paid) {
    }
}
