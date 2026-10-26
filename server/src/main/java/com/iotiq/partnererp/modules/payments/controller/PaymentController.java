package com.iotiq.partnererp.modules.payments.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.PageResponse;
import com.iotiq.partnererp.common.web.Pageables;
import com.iotiq.partnererp.modules.payments.dto.PaymentHistoryEntry;
import com.iotiq.partnererp.modules.payments.dto.PaymentInvoiceOption;
import com.iotiq.partnererp.modules.payments.dto.PaymentMeta;
import com.iotiq.partnererp.modules.payments.dto.PaymentReport;
import com.iotiq.partnererp.modules.payments.dto.PaymentRequest;
import com.iotiq.partnererp.modules.payments.dto.PaymentResponse;
import com.iotiq.partnererp.modules.payments.dto.PaymentSummary;
import com.iotiq.partnererp.modules.payments.dto.ReversePaymentRequest;
import com.iotiq.partnererp.modules.payments.entity.PaymentEntryType;
import com.iotiq.partnererp.modules.payments.entity.PaymentMethod;
import com.iotiq.partnererp.modules.payments.service.PaymentService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Payments received. Read/Write/Export follow the PAYMENTS module permission; reversing a receipt needs the
 * {@code PAYMENTS:REVERSE_PAYMENT} action. There is no update or delete: the ledger is append-only.
 */
@Tag(name = "Payments")
@RestController
@RequestMapping(AppConstants.API_V1 + "/payments")
public class PaymentController {

    private static final Set<String> SORTABLE = Set.of("paymentDate", "paymentNumber", "amount", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "paymentDate", "createdAt", "id");
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Search the ledger (payment, reference, invoice, billed-to or partner); newest first")
    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENTS:READ')")
    public ApiResponse<List<PaymentSummary>> list(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) UUID invoiceId,
                                                  @RequestParam(required = false) UUID partnerId,
                                                  @RequestParam(required = false) PaymentMethod method,
                                                  @RequestParam(required = false) PaymentEntryType type,
                                                  @RequestParam(required = false) PaymentService.State state,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String sort) {
        return PageResponse.of(paymentService.search(
                new PaymentService.Filters(q, invoiceId, partnerId, method, type, state, from, to),
                Pageables.of(page, size, sort, SORTABLE, DEFAULT_SORT), SecurityUtils.requireCurrentUser()))
                .toApiResponse();
    }

    @Operation(summary = "Totals of the filtered ledger by method, and today's receivables")
    @GetMapping("/report")
    @PreAuthorize("hasAuthority('PAYMENTS:READ')")
    public ApiResponse<PaymentReport> report(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) UUID invoiceId,
                                             @RequestParam(required = false) UUID partnerId,
                                             @RequestParam(required = false) PaymentMethod method,
                                             @RequestParam(required = false) PaymentEntryType type,
                                             @RequestParam(required = false) PaymentService.State state,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(paymentService.report(
                new PaymentService.Filters(q, invoiceId, partnerId, method, type, state, from, to),
                SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Methods, entry types, states and the caller's payment actions")
    @GetMapping("/meta")
    @PreAuthorize("hasAuthority('PAYMENTS:READ')")
    public ApiResponse<PaymentMeta> meta() {
        return ApiResponse.ok(paymentService.meta(SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Issued invoices with a balance, for recording a receipt (Payments write, no Invoices read needed)")
    @GetMapping("/invoice-options")
    @PreAuthorize("hasAuthority('PAYMENTS:WRITE')")
    public ApiResponse<List<PaymentInvoiceOption>> invoiceOptions(@RequestParam(required = false) String q,
                                                                  @RequestParam(required = false) UUID invoiceId) {
        return ApiResponse.ok(paymentService.invoiceOptions(q, invoiceId, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Download the filtered ledger as CSV")
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('PAYMENTS:EXPORT')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) UUID invoiceId,
                                         @RequestParam(required = false) UUID partnerId,
                                         @RequestParam(required = false) PaymentMethod method,
                                         @RequestParam(required = false) PaymentEntryType type,
                                         @RequestParam(required = false) PaymentService.State state,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String body = paymentService.exportCsv(
                new PaymentService.Filters(q, invoiceId, partnerId, method, type, state, from, to), DEFAULT_SORT,
                SecurityUtils.requireCurrentUser());
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("iotiq-payments.csv").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(("﻿" + body).getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "One ledger entry with its invoice's current balance")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYMENTS:READ')")
    public ApiResponse<PaymentResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(paymentService.get(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Audited events, newest first")
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('PAYMENTS:READ')")
    public ApiResponse<List<PaymentHistoryEntry>> history(@PathVariable UUID id) {
        return ApiResponse.ok(paymentService.history(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Record a receipt against an issued invoice (201; 200 when the idempotency key was already used)")
    @PostMapping
    @PreAuthorize("hasAuthority('PAYMENTS:WRITE')")
    public ResponseEntity<ApiResponse<PaymentResponse>> record(@RequestBody PaymentRequest request) {
        PaymentService.Recorded recorded = paymentService.record(request, SecurityUtils.requireCurrentUser());
        return ResponseEntity.status(recorded.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(recorded.payment()));
    }

    @Operation(summary = "Reverse a receipt with a reason; the receipt is kept and a reversal entry is added")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('PAYMENTS:READ') and hasAuthority('PAYMENTS:REVERSE_PAYMENT')")
    public ApiResponse<PaymentResponse> reverse(@PathVariable UUID id, @RequestBody(required = false) ReversePaymentRequest request) {
        return ApiResponse.ok(paymentService.reverse(id, request, SecurityUtils.requireCurrentUser()));
    }
}
