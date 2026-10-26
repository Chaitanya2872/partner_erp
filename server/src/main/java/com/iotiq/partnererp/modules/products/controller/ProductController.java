package com.iotiq.partnererp.modules.products.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.PageResponse;
import com.iotiq.partnererp.common.web.Pageables;
import com.iotiq.partnererp.modules.products.dto.ProductHistoryEntry;
import com.iotiq.partnererp.modules.products.dto.ProductImportResult;
import com.iotiq.partnererp.modules.products.dto.ProductMeta;
import com.iotiq.partnererp.modules.products.dto.ProductOption;
import com.iotiq.partnererp.modules.products.dto.ProductRequest;
import com.iotiq.partnererp.modules.products.dto.ProductStatusRequest;
import com.iotiq.partnererp.modules.products.dto.ProductView;
import com.iotiq.partnererp.modules.products.entity.ProductCategory;
import com.iotiq.partnererp.modules.products.service.ProductImportService;
import com.iotiq.partnererp.modules.products.service.ProductService;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Item & price sheet. Read/Write/Delete/Export follow the PRODUCTS module permission. Internal B2C fields are
 * returned, searched, exported and written only with {@code PRODUCTS:VIEW_INTERNAL_B2C}; everyone else receives
 * the partner-safe view. Creating and importing need that action too (the B2C name is required).
 */
@Tag(name = "Products")
@RestController
@RequestMapping(AppConstants.API_V1 + "/products")
public class ProductController {

    private static final Set<String> SORTABLE = Set.of("sku", "partnerName", "category", "partnerPrice", "updatedAt");
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ProductService productService;
    private final ProductImportService importService;

    public ProductController(ProductService productService, ProductImportService importService) {
        this.productService = productService;
        this.importService = importService;
    }

    @Operation(summary = "Price sheet, by category then SKU (internal B2C fields only with VIEW_INTERNAL_B2C)")
    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTS:READ')")
    public ApiResponse<List<ProductView>> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) ProductCategory category,
                                               @RequestParam(required = false) Boolean active,
                                               @RequestParam(required = false) Boolean b2cMissing,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size,
                                               @RequestParam(required = false) String sort) {
        return PageResponse.of(productService.search(new ProductService.Filters(q, category, active, b2cMissing),
                Pageables.of(page, size, sort, SORTABLE, Sort.by("category", "sku")),
                SecurityUtils.requireCurrentUser())).toApiResponse();
    }

    @Operation(summary = "Categories with product counts, and form defaults")
    @GetMapping("/meta")
    @PreAuthorize("hasAuthority('PRODUCTS:READ')")
    public ApiResponse<ProductMeta> meta() {
        return ApiResponse.ok(productService.meta());
    }

    /**
     * Decision 1 (2026-10-26): Estimates writers may pick products without Products read. The option carries only
     * what a line needs (SKU, partner name, category, HSN, unit, partner rate, GST); the B2C name and rate are added
     * only for callers holding {@code PRODUCTS:VIEW_INTERNAL_B2C}.
     */
    @Operation(summary = "Active products for document line pickers (at most 50; Products read or Estimates write)")
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('PRODUCTS:READ') or hasAuthority('ESTIMATES:WRITE')")
    public ApiResponse<List<ProductOption>> options(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) ProductCategory category) {
        return ApiResponse.ok(productService.options(q, category, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Download the filtered sheet as CSV (B2C columns only with VIEW_INTERNAL_B2C)")
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('PRODUCTS:EXPORT')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) ProductCategory category,
                                         @RequestParam(required = false) Boolean active,
                                         @RequestParam(required = false) Boolean b2cMissing) {
        return csv("iotiq-item-sheet.csv", importService.export(
                new ProductService.Filters(q, category, active, b2cMissing), SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Download the CSV import template")
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('PRODUCTS:WRITE')")
    public ResponseEntity<byte[]> template() {
        return csv("iotiq-item-template.csv", ProductImportService.template());
    }

    @Operation(summary = "Import a CSV: dryRun=true (default) previews per row; dryRun=false saves the valid rows")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PRODUCTS:WRITE')")
    public ApiResponse<ProductImportResult> importCsv(@RequestPart("file") MultipartFile file,
                                                      @RequestParam(defaultValue = "true") boolean dryRun)
            throws IOException {
        return ApiResponse.ok(importService.importCsv(file.getBytes(), dryRun, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "One product")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTS:READ')")
    public ApiResponse<ProductView> get(@PathVariable UUID id) {
        return ApiResponse.ok(productService.get(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Audited changes, newest first (internal fields only with VIEW_INTERNAL_B2C)")
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('PRODUCTS:READ')")
    public ApiResponse<List<ProductHistoryEntry>> history(@PathVariable UUID id) {
        return ApiResponse.ok(productService.history(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Create a product (needs VIEW_INTERNAL_B2C as well: the B2C name is required)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTS:WRITE')")
    public ApiResponse<ProductView> create(@RequestBody ProductRequest request) {
        return ApiResponse.ok(productService.create(request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Replace a product's details (with version; internal fields only with VIEW_INTERNAL_B2C)")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTS:WRITE')")
    public ApiResponse<ProductView> update(@PathVariable UUID id, @RequestBody ProductRequest request) {
        return ApiResponse.ok(productService.update(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Activate or deactivate a product (inactive products can't be added to new documents)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PRODUCTS:WRITE')")
    public ApiResponse<ProductView> changeStatus(@PathVariable UUID id, @Valid @RequestBody ProductStatusRequest request) {
        return ApiResponse.ok(productService.changeStatus(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Delete a product that no document has used (409 PRODUCT_IN_USE otherwise; deactivate it)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PRODUCTS:DELETE')")
    public void delete(@PathVariable UUID id) {
        productService.delete(id);
    }

    /** UTF-8 with a byte-order mark so spreadsheet programs show ₹ and non-ASCII names correctly. */
    private static ResponseEntity<byte[]> csv(String filename, String body) {
        byte[] bytes = ("﻿" + body).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }
}
