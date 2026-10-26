package com.iotiq.partnererp.modules.invoices.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.modules.invoices.dto.InternalInvoiceDocument;
import com.iotiq.partnererp.modules.invoices.service.InvoiceService;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal B2C copies of issued invoices (ADR-06): only for callers with {@code INTERNAL_B2C} read as well as
 * Invoices read. Kept under {@code /internal/**} so partner-facing routes can never return them.
 */
@Tag(name = "Internal B2C")
@RestController
@RequestMapping(AppConstants.API_V1 + "/internal/invoices")
public class InternalInvoiceController {

    private final InvoiceService invoiceService;

    public InternalInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Operation(summary = "Internal B2C copy of an issued invoice: B2C names and rates, partner totals, uplift")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INTERNAL_B2C:READ') and hasAuthority('INVOICES:READ')")
    public ApiResponse<InternalInvoiceDocument> get(@PathVariable UUID id) {
        return ApiResponse.ok(invoiceService.internalDocument(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Printable internal B2C copy (HTML, stamped INTERNAL ONLY)")
    @GetMapping("/{id}/document")
    @PreAuthorize("hasAuthority('INTERNAL_B2C:READ') and hasAuthority('INVOICES:READ')")
    public ResponseEntity<byte[]> document(@PathVariable UUID id) {
        InvoiceService.Document doc = invoiceService.internalDocumentHtml(id, SecurityUtils.requireCurrentUser());
        return InvoiceController.download(doc.filename(), InvoiceController.HTML, doc.html());
    }
}
