package com.iotiq.partnererp.modules.products.mapper;

import com.iotiq.partnererp.modules.products.dto.InternalProductResponse;
import com.iotiq.partnererp.modules.products.dto.ProductResponse;
import com.iotiq.partnererp.modules.products.entity.Product;

import java.util.List;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductResponse toPartnerView(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getCategory(), p.getCategory().getLabel(),
                p.getPartnerName(), p.getUnit(), p.getHsn(), p.getPartnerPrice(), p.getGstPercent(), p.isActive());
    }

    public static InternalProductResponse toInternalView(Product p) {
        return new InternalProductResponse(p.getId(), p.getSku(), p.getCategory(), p.getCategory().getLabel(),
                p.getPartnerName(), p.getB2cName(), List.copyOf(p.getAliases()), p.getUnit(), p.getHsn(),
                p.getPartnerPrice(), p.getB2cPrice(), p.getCostPrice(), p.getGstPercent(), p.isActive());
    }
}
