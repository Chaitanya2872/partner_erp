package com.iotiq.partnererp.modules.invoices.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.PageResponse;
import com.iotiq.partnererp.common.web.Pageables;
import com.iotiq.partnererp.modules.invoices.dto.ConvertEstimateRequest;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceActionRequest;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceHistoryEntry;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceLeadOption;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceMeta;
import com.iotiq.partnererp.modules.invoices.dto.InvoicePreview;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceRequest;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceResponse;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceSummary;
import com.iotiq.partnererp.modules.invoices.service.InvoiceService;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Invoices (partner copies). Read/Write/Export follow the INVOICES module permission; Delete cancels an issued
 * invoice or discards a draft; Approve confirms a doubtful tax treatment. Every response here is partner-safe; the
 * internal B2C twin is served by {@link InternalInvoiceController}.
 */
@Tag(name = "Invoices")
@RestController
@RequestMapping(AppConstants.API_V1 + "/invoices")
public class InvoiceController {

    private static final Set<String> SORTABLE = Set.of("invoiceDate", "invoiceNumber", "dueDate", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Operation(summary = "Search invoices (number, billed-to, partner, client, lead or estimate number); newest first")
    @GetMapping
    @PreAuthorize("hasAuthority('INVOICES:READ')")
    public ApiResponse<List<InvoiceSummary>> list(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) InvoiceStatus status,
                                                  @RequestParam(required = false) InvoiceService.PaymentFilter payment,
                                                  @RequestParam(required = false) UUID partnerId,
                                                  @RequestParam(required = false) UUID leadId,
                                                  @RequestParam(required = false) UUID estimateId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String sort) {
        return PageResponse.of(invoiceService.search(
                new InvoiceService.Filters(q, status, payment, partnerId, leadId, estimateId, from, to),
                Pageables.of(page, size, sort, SORTABLE, DEFAULT_SORT), SecurityUtils.requireCurrentUser()))
                .toApiResponse();
    }

    @Operation(summary = "Statuses, states, place-of-supply bases, defaults and the caller's invoice actions")
    @GetMapping("/meta")
    @PreAuthorize("hasAuthority('INVOICES:READ')")
    public ApiResponse<InvoiceMeta> meta() {
        return ApiResponse.ok(invoiceService.meta(SecurityUtils.requireCurrentUser()));
    }

    /** Restricted lead picker for direct invoices (Phase 9 decision 5): minimum fields, no Leads read needed. */
    @Operation(summary = "Eligible leads for a direct invoice (minimum fields; Invoices write, no Leads read needed)")
    @GetMapping("/lead-options")
    @PreAuthorize("hasAuthority('INVOICES:WRITE')")
    public ApiResponse<List<InvoiceLeadOption>> leadOptions(@RequestParam(required = false) String q,
                                                            @RequestParam(required = false) UUID leadId) {
        return ApiResponse.ok(invoiceService.leadOptions(q, leadId, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Download the filtered list as CSV (partner-safe columns)")
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('INVOICES:EXPORT')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) InvoiceStatus status,
                                         @RequestParam(required = false) InvoiceService.PaymentFilter payment,
                                         @RequestParam(required = false) UUID partnerId,
                                         @RequestParam(required = false) UUID leadId,
                                         @RequestParam(required = false) UUID estimateId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String body = invoiceService.exportCsv(
                new InvoiceService.Filters(q, status, payment, partnerId, leadId, estimateId, from, to), DEFAULT_SORT,
                SecurityUtils.requireCurrentUser());
        return download("iotiq-invoices.csv", CSV, "﻿" + body);
    }

    @Operation(summary = "Server tax and totals for the editor; nothing is saved (internal block only with VIEW_INTERNAL_B2C)")
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('INVOICES:WRITE')")
    public ApiResponse<InvoicePreview> preview(@RequestBody InvoiceRequest request,
                                               @RequestParam(required = false) UUID invoiceId) {
        return ApiResponse.ok(invoiceService.preview(request, invoiceId, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "One invoice (partner copy)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVOICES:READ')")
    public ApiResponse<InvoiceResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(invoiceService.get(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Audited changes, newest first")
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('INVOICES:READ')")
    public ApiResponse<List<InvoiceHistoryEntry>> history(@PathVariable UUID id) {
        return ApiResponse.ok(invoiceService.history(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Printable partner copy (HTML); drafts are marked as not a tax invoice")
    @GetMapping("/{id}/document")
    @PreAuthorize("hasAuthority('INVOICES:READ')")
    public ResponseEntity<byte[]> document(@PathVariable UUID id) {
        InvoiceService.Document doc = invoiceService.partnerDocument(id, SecurityUtils.requireCurrentUser());
        return download(doc.filename(), HTML, doc.html());
    }

    @Operation(summary = "Create a draft invoice without an estimate")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVOICES:WRITE')")
    public ApiResponse<InvoiceResponse> create(@RequestBody InvoiceRequest request) {
        return ApiResponse.ok(invoiceService.create(request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Create a draft invoice from a sent or accepted estimate; the estimate is locked")
    @PostMapping("/from-estimate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVOICES:WRITE') and hasAuthority('ESTIMATES:READ')")
    public ApiResponse<InvoiceResponse> convert(@RequestBody ConvertEstimateRequest request) {
        return ApiResponse.ok(invoiceService.convert(request == null ? null : request.estimateId(),
                SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Replace a draft invoice (with version; 409 once issued or cancelled)")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INVOICES:WRITE')")
    public ApiResponse<InvoiceResponse> update(@PathVariable UUID id, @RequestBody InvoiceRequest request) {
        return ApiResponse.ok(invoiceService.update(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Confirm a tax treatment that needs confirmation (Invoices approve; note required)")
    @PostMapping("/{id}/confirm-tax")
    @PreAuthorize("hasAuthority('INVOICES:APPROVE')")
    public ApiResponse<InvoiceResponse> confirmTax(@PathVariable UUID id, @RequestBody InvoiceActionRequest request) {
        return ApiResponse.ok(invoiceService.confirmTax(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Issue a draft: allocates INV-YYYY-NNNNNN and freezes the tax snapshot")
    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('INVOICES:WRITE')")
    public ApiResponse<InvoiceResponse> issue(@PathVariable UUID id, @RequestBody InvoiceActionRequest request) {
        return ApiResponse.ok(invoiceService.issue(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Cancel an issued invoice with a reason (Invoices delete; refused while payments exist)")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('INVOICES:DELETE')")
    public ApiResponse<InvoiceResponse> cancel(@PathVariable UUID id, @RequestBody InvoiceActionRequest request) {
        return ApiResponse.ok(invoiceService.cancel(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Discard a draft invoice (soft; a converted draft unlocks its estimate)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('INVOICES:DELETE')")
    public void discard(@PathVariable UUID id) {
        invoiceService.discard(id, SecurityUtils.requireCurrentUser());
    }

    static ResponseEntity<byte[]> download(String filename, MediaType type, String body) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
