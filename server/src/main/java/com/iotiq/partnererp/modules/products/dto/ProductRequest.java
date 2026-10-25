package com.iotiq.partnererp.modules.products.dto;

import com.iotiq.partnererp.modules.products.entity.ProductCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Create or replace a product. {@code b2cName}, {@code aliases}, {@code b2cPrice} and {@code costPrice} are internal
 * fields: they need {@code PRODUCTS:VIEW_INTERNAL_B2C}, and callers without it must leave them out (they are kept
 * unchanged). {@code version} is required on update.
 */
public record ProductRequest(String sku, ProductCategory category, String unit, String hsn, String partnerName,
                             BigDecimal partnerPrice, BigDecimal gstPercent, String b2cName, List<String> aliases,
                             BigDecimal b2cPrice, BigDecimal costPrice, Long version) {

    public boolean hasInternalFields() {
        return b2cName != null || aliases != null || b2cPrice != null || costPrice != null;
    }
}
