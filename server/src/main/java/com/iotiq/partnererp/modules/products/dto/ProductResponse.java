package com.iotiq.partnererp.modules.products.dto;

import com.iotiq.partnererp.modules.products.entity.ProductCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Partner-safe product view: partner-facing name and price only. Never contains B2C names, B2C prices,
 * alternative names, landed cost or discount (ADR-06, BR-I9).
 */
public record ProductResponse(UUID id, String sku, ProductCategory category, String categoryLabel,
                              String partnerName, String unit, String hsn, BigDecimal partnerPrice,
                              BigDecimal gstPercent, boolean active, long version, Instant updatedAt)
        implements ProductView {
}
