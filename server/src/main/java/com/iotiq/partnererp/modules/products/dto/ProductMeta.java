package com.iotiq.partnererp.modules.products.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Categories with product counts (all and active), plus form defaults.
 */
public record ProductMeta(List<Category> categories, BigDecimal defaultGstPercent, String defaultUnit,
                          int maxAliases) {

    public record Category(String value, String label, long total, long active) {
    }
}
