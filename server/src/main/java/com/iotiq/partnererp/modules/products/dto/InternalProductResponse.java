package com.iotiq.partnererp.modules.products.dto;

import com.iotiq.partnererp.modules.products.entity.ProductCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Internal product view including B2C name, alternative names, B2C price, landed cost and the BR-I6 discount.
 * Returned only to callers holding {@code PRODUCTS:VIEW_INTERNAL_B2C}.
 */
public record InternalProductResponse(UUID id, String sku, ProductCategory category, String categoryLabel,
                                      String partnerName, String b2cName, List<String> aliases, String unit,
                                      String hsn, BigDecimal partnerPrice, BigDecimal b2cPrice,
                                      BigDecimal costPrice, BigDecimal gstPercent, boolean active, long version,
                                      Instant updatedAt, int discountPercent, boolean b2cPriceMissing,
                                      boolean sameNameAsB2c)
        implements ProductView {
}
