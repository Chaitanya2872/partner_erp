package com.iotiq.partnererp.modules.leads.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lead queries that span other tables: first estimate dates for the SLA, the phone conflict check, linked
 * documents for delete, and the SLA-breach filter.
 */
@Repository
public class LeadMetricsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public LeadMetricsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Date of the earliest-dated estimate per lead, any status (§4.1). Leads without estimates are absent. */
    public Map<UUID, LocalDate> earliestEstimateDates(Collection<UUID> leadIds) {
        if (leadIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, LocalDate> result = new HashMap<>();
        jdbc.query("SELECT lead_id, MIN(estimate_date) AS d FROM estimates WHERE lead_id IN (:ids) GROUP BY lead_id",
                new MapSqlParameterSource("ids", leadIds),
                rs -> {
                    result.put(rs.getObject("lead_id", UUID.class), rs.getObject("d", LocalDate.class));
                });
        return result;
    }

    /**
     * BR-L6: the partner protecting the same phone, if any. Another live lead with the same last-10 digits, a
     * different partner and {@code protection_until >= today}; the longest-running protection wins.
     */
    public Optional<String> protectingPartnerName(String phoneDigits, UUID partnerId, UUID excludeLeadId, LocalDate today) {
        MapSqlParameterSource params = new MapSqlParameterSource("digits", phoneDigits)
                .addValue("partnerId", partnerId).addValue("today", today);
        String exclude = "";
        if (excludeLeadId != null) {
            exclude = " AND l.id <> :excludeId";
            params.addValue("excludeId", excludeLeadId);
        }
        List<String> names = jdbc.queryForList("""
                        SELECT p.name
                          FROM leads l JOIN partners p ON p.id = l.partner_id
                         WHERE l.deleted_at IS NULL AND l.client_phone_digits = :digits
                           AND l.partner_id <> :partnerId AND l.protection_until >= :today""" + exclude + """

                         ORDER BY l.protection_until DESC, l.created_at
                         LIMIT 1
                        """, params, String.class);
        return names.stream().findFirst();
    }

    /** Whether any estimate, invoice, payment or internal B2C document points at the lead (D-26). */
    public boolean hasDocuments(UUID leadId) {
        Boolean exists = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM estimates WHERE lead_id = :id)
                            OR EXISTS (SELECT 1 FROM invoices WHERE lead_id = :id)
                            OR EXISTS (SELECT 1 FROM payments WHERE lead_id = :id)
                            OR EXISTS (SELECT 1 FROM b2c_documents WHERE lead_id = :id)
                        """, new MapSqlParameterSource("id", leadId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Ids of live leads with an SLA breach, the same rules as {@code LeadSla} expressed in SQL. Rounded hours
     * exceed the target exactly when the elapsed time is at least target + 0.5 h.
     */
    public Set<UUID> slaBreachedIds(Instant now, ZoneId zone, int ackHours, int estimateDays) {
        Set<UUID> ids = new HashSet<>();
        jdbc.query("""
                        SELECT l.id
                          FROM leads l
                          LEFT JOIN LATERAL (SELECT MIN(e.estimate_date) AS d FROM estimates e WHERE e.lead_id = l.id) est ON true
                         WHERE l.deleted_at IS NULL AND (
                               (l.acknowledged_at IS NOT NULL AND l.acknowledged_at - l.created_at >= make_interval(secs => :ackSecs))
                            OR (l.acknowledged_at IS NULL AND l.stage NOT IN ('WON', 'LOST')
                                AND CAST(:now AS timestamptz) - l.created_at >= make_interval(secs => :ackSecs))
                            OR (l.acknowledged_at IS NOT NULL AND est.d IS NOT NULL
                                AND est.d - (l.acknowledged_at AT TIME ZONE :zone)::date > :estDays)
                            OR (l.acknowledged_at IS NOT NULL AND est.d IS NULL AND l.stage NOT IN ('WON', 'LOST')
                                AND (CAST(:now AS timestamptz) AT TIME ZONE :zone)::date - (l.acknowledged_at AT TIME ZONE :zone)::date > :estDays))
                        """,
                new MapSqlParameterSource("now", now.atOffset(ZoneOffset.UTC)).addValue("zone", zone.getId())
                        .addValue("ackSecs", ackHours * 3600L + 1800L).addValue("estDays", estimateDays),
                rs -> {
                    ids.add(rs.getObject("id", UUID.class));
                });
        return ids;
    }
}
