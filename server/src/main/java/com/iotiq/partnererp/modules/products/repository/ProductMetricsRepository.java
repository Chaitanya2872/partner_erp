package com.iotiq.partnererp.modules.products.repository;

import com.iotiq.partnererp.modules.products.entity.ProductCategory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only SQL for the price sheet: references from document lines (D-17) and per-category counts.
 */
@Repository
public class ProductMetricsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ProductMetricsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * True when a document line ever used the product (decision 2, 2026-10-26): the permanent
     * {@code first_referenced_at} marker, set by a database trigger, or a line that points at it now.
     */
    public boolean isReferenced(UUID productId) {
        Boolean referenced = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM products WHERE id = :id AND first_referenced_at IS NOT NULL)
                    OR EXISTS (SELECT 1 FROM estimate_items WHERE product_id = :id)
                    OR EXISTS (SELECT 1 FROM invoice_items WHERE product_id = :id)
                    OR EXISTS (SELECT 1 FROM b2c_document_items WHERE product_id = :id)
                """, Map.of("id", productId), Boolean.class);
        return Boolean.TRUE.equals(referenced);
    }

    /** Counts per category; categories without products are absent. */
    public Map<ProductCategory, Counts> categoryCounts() {
        Map<ProductCategory, Counts> counts = new EnumMap<>(ProductCategory.class);
        jdbc.query("""
                SELECT category, count(*) AS total, count(*) FILTER (WHERE active) AS live
                FROM products GROUP BY category
                """, rs -> {
            counts.put(ProductCategory.valueOf(rs.getString("category")),
                    new Counts(rs.getLong("total"), rs.getLong("live")));
        });
        return counts;
    }

    public record Counts(long total, long active) {
    }
}
