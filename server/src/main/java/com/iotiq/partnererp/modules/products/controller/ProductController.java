package com.iotiq.partnererp.modules.products.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.PageResponse;
import com.iotiq.partnererp.common.web.Pageables;
import com.iotiq.partnererp.modules.products.dto.ProductView;
import com.iotiq.partnererp.modules.products.entity.ProductCategory;
import com.iotiq.partnererp.modules.products.service.ProductService;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only price sheet. Internal B2C fields are returned only with {@code PRODUCTS:VIEW_INTERNAL_B2C};
 * everyone else receives the partner-safe view.
 */
@Tag(name = "Products")
@RestController
@RequestMapping(AppConstants.API_V1 + "/products")
public class ProductController {

    private static final Set<String> SORTABLE = Set.of("sku", "partnerName", "category", "partnerPrice");

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Price sheet (internal B2C fields only with VIEW_INTERNAL_B2C)")
    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTS:READ')")
    public ApiResponse<List<ProductView>> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) ProductCategory category,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size,
                                               @RequestParam(required = false) String sort) {
        return PageResponse.of(productService.search(q, category, canViewInternal(),
                Pageables.of(page, size, sort, SORTABLE, Sort.by("category", "sku")))).toApiResponse();
    }

    @Operation(summary = "One product")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTS:READ')")
    public ApiResponse<ProductView> get(@PathVariable UUID id) {
        return ApiResponse.ok(productService.get(id, canViewInternal()));
    }

    private static boolean canViewInternal() {
        return SecurityUtils.requireCurrentUser().permissions().canPerform("PRODUCTS", "VIEW_INTERNAL_B2C");
    }
}
