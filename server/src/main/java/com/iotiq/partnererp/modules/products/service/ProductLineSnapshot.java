package com.iotiq.partnererp.modules.products.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The values an estimate or invoice line copies from a product when it is added (BUSINESS_RULES §6.2, ADR-10).
 * Later product edits never change a saved line. {@code name} is the partner-facing name; {@code b2cName} and
 * {@code b2cRate} feed only the internal B2C twin.
 */
public record ProductLineSnapshot(UUID productId, String sku, String name, String b2cName, String hsn, String unit,
                                  BigDecimal rate, BigDecimal b2cRate, BigDecimal gstPercent) {
}
