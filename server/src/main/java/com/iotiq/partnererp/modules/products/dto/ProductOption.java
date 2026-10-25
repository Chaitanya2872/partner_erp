package com.iotiq.partnererp.modules.products.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iotiq.partnererp.modules.products.entity.ProductCategory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Active product for document line pickers (estimates, invoices). Partner-safe; the internal B2C name and price
 * are filled only for callers with {@code PRODUCTS:VIEW_INTERNAL_B2C} and are omitted from the JSON otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductOption(UUID id, String sku, ProductCategory category, String categoryLabel, String name,
                            String hsn, String unit, BigDecimal rate, BigDecimal gstPercent, String b2cName,
                            BigDecimal b2cRate) {
}
