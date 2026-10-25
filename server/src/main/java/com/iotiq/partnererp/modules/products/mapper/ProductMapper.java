package com.iotiq.partnererp.modules.products.mapper;

import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.products.dto.InternalProductResponse;
import com.iotiq.partnererp.modules.products.dto.ProductOption;
import com.iotiq.partnererp.modules.products.dto.ProductResponse;
import com.iotiq.partnererp.modules.products.entity.Product;
import com.iotiq.partnererp.modules.products.service.ProductLineSnapshot;
import com.iotiq.partnererp.modules.products.service.ProductRules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProductMapper {

    /** Audit snapshot keys that only {@code PRODUCTS:VIEW_INTERNAL_B2C} holders may see. */
    public static final Set<String> INTERNAL_FIELDS = Set.of("b2cName", "aliases", "b2cPrice", "costPrice");

    private ProductMapper() {
    }

    public static ProductResponse toPartnerView(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getCategory(), p.getCategory().getLabel(),
                p.getPartnerName(), p.getUnit(), p.getHsn(), p.getPartnerPrice(), p.getGstPercent(), p.isActive(),
                p.getVersion(), p.getUpdatedAt());
    }

    public static InternalProductResponse toInternalView(Product p) {
        return new InternalProductResponse(p.getId(), p.getSku(), p.getCategory(), p.getCategory().getLabel(),
                p.getPartnerName(), p.getB2cName(), List.copyOf(p.getAliases()), p.getUnit(), p.getHsn(),
                p.getPartnerPrice(), p.getB2cPrice(), p.getCostPrice(), p.getGstPercent(), p.isActive(),
                p.getVersion(), p.getUpdatedAt(), ProductRules.discountPercent(p.getPartnerPrice(), p.getB2cPrice()),
                ProductRules.b2cPriceMissing(p.getB2cPrice()), p.getPartnerName().equalsIgnoreCase(p.getB2cName()));
    }

    public static ProductOption toOption(Product p, boolean internal) {
        return new ProductOption(p.getId(), p.getSku(), p.getCategory(), p.getCategory().getLabel(),
                p.getPartnerName(), p.getHsn(), p.getUnit(), p.getPartnerPrice(), p.getGstPercent(),
                internal ? p.getB2cName() : null, internal ? p.getB2cPrice() : null);
    }

    /** §6.2: name = partner-facing name, rate = partner price, b2cRate = B2C price (0 when unset). */
    public static ProductLineSnapshot toLineSnapshot(Product p) {
        return new ProductLineSnapshot(p.getId(), p.getSku(), p.getPartnerName(), p.getB2cName(), p.getHsn(),
                p.getUnit(), p.getPartnerPrice(),
                p.getB2cPrice() == null ? BigDecimal.ZERO.setScale(2) : p.getB2cPrice(), p.getGstPercent());
    }

    public static Map<String, Object> auditSnapshot(Product p) {
        return AuditService.values(
                "sku", p.getSku(),
                "category", p.getCategory(),
                "unit", p.getUnit(),
                "hsn", p.getHsn(),
                "partnerName", p.getPartnerName(),
                "partnerPrice", p.getPartnerPrice(),
                "gstPercent", p.getGstPercent(),
                "b2cName", p.getB2cName(),
                "aliases", List.copyOf(p.getAliases()),
                "b2cPrice", p.getB2cPrice(),
                "costPrice", p.getCostPrice(),
                "active", p.isActive());
    }
}
