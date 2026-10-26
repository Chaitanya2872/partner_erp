package com.iotiq.partnererp.modules.payouts.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.PageResponse;
import com.iotiq.partnererp.common.web.Pageables;
import com.iotiq.partnererp.modules.payouts.dto.PayoutActionRequest;
import com.iotiq.partnererp.modules.payouts.dto.PayoutCreateRequest;
import com.iotiq.partnererp.modules.payouts.dto.PayoutDashboard;
import com.iotiq.partnererp.modules.payouts.dto.PayoutHistoryEntry;
import com.iotiq.partnererp.modules.payouts.dto.PayoutMarkPaidRequest;
import com.iotiq.partnererp.modules.payouts.dto.PayoutMeta;
import com.iotiq.partnererp.modules.payouts.dto.PayoutPreview;
import com.iotiq.partnererp.modules.payouts.dto.PayoutPreviewRequest;
import com.iotiq.partnererp.modules.payouts.dto.PayoutResponse;
import com.iotiq.partnererp.modules.payouts.dto.PayoutSummary;
import com.iotiq.partnererp.modules.payouts.dto.TdsAdjustmentRequest;
import com.iotiq.partnererp.modules.payouts.entity.PayoutStatus;
import com.iotiq.partnererp.modules.payouts.repository.PayoutQueries;
import com.iotiq.partnererp.modules.payouts.service.PayoutService;
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
 * Partner payouts. Read/Write/Export follow the PAYOUTS module permission; approving, rejecting and cancelling an
 * approved payout need Payouts approve; processing, paid and failed need the {@code PAYOUTS:MARK_PAYOUT_PAID} action.
 * There is no bank integration and no delete.
 */
@Tag(name = "Payouts")
@RestController
@RequestMapping(AppConstants.API_V1 + "/payouts")
public class PayoutController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "asOfDate", "payoutNumber", "commissionTotal",
            "netAmount", "paidDate");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final PayoutService payoutService;

    public PayoutController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @Operation(summary = "Search payouts (number, bank reference, partner name or code); newest first")
    @GetMapping
    @PreAuthorize("hasAuthority('PAYOUTS:READ')")
    public ApiResponse<List<PayoutSummary>> list(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) PayoutStatus status,
                                                 @RequestParam(required = false) UUID partnerId,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String sort) {
        return PageResponse.of(payoutService.search(new PayoutService.Filters(q, status, partnerId, from, to),
                Pageables.of(page, size, sort, SORTABLE, DEFAULT_SORT), SecurityUtils.requireCurrentUser()))
                .toApiResponse();
    }

    @Operation(summary = "Payout totals by status, commission waiting to be paid out, and this year's payments")
    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('PAYOUTS:READ')")
    public ApiResponse<PayoutDashboard> dashboard() {
        return ApiResponse.ok(payoutService.dashboard(SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Statuses, limits, TDS readiness and the caller's payout actions")
    @GetMapping("/meta")
    @PreAuthorize("hasAuthority('PAYOUTS:READ')")
    public ApiResponse<PayoutMeta> meta() {
        return ApiResponse.ok(payoutService.meta(SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Download the filtered payouts as CSV (no bank details)")
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('PAYOUTS:EXPORT')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) PayoutStatus status,
                                         @RequestParam(required = false) UUID partnerId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String body = payoutService.exportCsv(new PayoutService.Filters(q, status, partnerId, from, to), DEFAULT_SORT,
                SecurityUtils.requireCurrentUser());
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("iotiq-payouts.csv").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(("﻿" + body).getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "Payout position of each partner with commission, adjustments or payouts")
    @GetMapping("/partners")
    @PreAuthorize("hasAuthority('PAYOUTS:READ')")
    public ApiResponse<List<PayoutQueries.Balance>> balances(@RequestParam(required = false) String q) {
        return ApiResponse.ok(payoutService.balances(q, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "One partner's payout position")
    @GetMapping("/partners/{partnerId}")
    @PreAuthorize("hasAuthority('PAYOUTS:READ')")
    public ApiResponse<PayoutQueries.Balance> balance(@PathVariable UUID partnerId) {
        return ApiResponse.ok(payoutService.balance(partnerId, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Preview a payout for a partner: commission lines, carried adjustments and TDS; nothing is saved")
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('PAYOUTS:READ')")
    public ApiResponse<PayoutPreview> preview(@RequestBody PayoutPreviewRequest request) {
        return ApiResponse.ok(payoutService.preview(request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Generate a draft payout for a partner (201; 200 when the idempotency key was already used)")
    @PostMapping
    @PreAuthorize("hasAuthority('PAYOUTS:WRITE')")
    public ResponseEntity<ApiResponse<PayoutResponse>> create(@RequestBody PayoutCreateRequest request) {
        PayoutService.Created created = payoutService.create(request, SecurityUtils.requireCurrentUser());
        return ResponseEntity.status(created.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(created.payout()));
    }

    @Operation(summary = "One payout with lines, TDS calculations and workflow")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYOUTS:READ')")
    public ApiResponse<PayoutResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(payoutService.get(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Audited events, newest first")
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('PAYOUTS:READ')")
    public ApiResponse<List<PayoutHistoryEntry>> history(@PathVariable UUID id) {
        return ApiResponse.ok(payoutService.history(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Recalculate a draft or pending payout")
    @PostMapping("/{id}/recalculate")
    @PreAuthorize("hasAuthority('PAYOUTS:WRITE')")
    public ApiResponse<PayoutResponse> recalculate(@PathVariable UUID id, @RequestBody PayoutActionRequest request) {
        return ApiResponse.ok(payoutService.recalculate(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Change the calculated TDS of a draft or pending payout, with a reason")
    @PostMapping("/{id}/tds-adjustment")
    @PreAuthorize("hasAuthority('PAYOUTS:WRITE')")
    public ApiResponse<PayoutResponse> adjustTds(@PathVariable UUID id, @RequestBody TdsAdjustmentRequest request) {
        return ApiResponse.ok(payoutService.adjustTds(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Submit a draft for approval")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('PAYOUTS:WRITE')")
    public ApiResponse<PayoutResponse> submit(@PathVariable UUID id, @RequestBody PayoutActionRequest request) {
        return ApiResponse.ok(payoutService.submit(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Approve a pending payout (not by its creator or submitter)")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PAYOUTS:APPROVE')")
    public ApiResponse<PayoutResponse> approve(@PathVariable UUID id, @RequestBody PayoutActionRequest request) {
        return ApiResponse.ok(payoutService.approve(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Reject a pending payout with a reason")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('PAYOUTS:APPROVE')")
    public ApiResponse<PayoutResponse> reject(@PathVariable UUID id, @RequestBody PayoutActionRequest request) {
        return ApiResponse.ok(payoutService.reject(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Cancel a payout with a reason (draft or pending: write; approved: approve)")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PAYOUTS:WRITE') or hasAuthority('PAYOUTS:APPROVE')")
    public ApiResponse<PayoutResponse> cancel(@PathVariable UUID id, @RequestBody PayoutActionRequest request) {
        return ApiResponse.ok(payoutService.cancel(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Record that the bank transfer of an approved payout was started (no money moves here)")
    @PostMapping("/{id}/processing")
    @PreAuthorize("hasAuthority('PAYOUTS:READ') and hasAuthority('PAYOUTS:MARK_PAYOUT_PAID')")
    public ApiResponse<PayoutResponse> startProcessing(@PathVariable UUID id, @RequestBody PayoutActionRequest request) {
        return ApiResponse.ok(payoutService.startProcessing(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Confirm a payout under processing was paid, with the bank reference")
    @PostMapping("/{id}/mark-paid")
    @PreAuthorize("hasAuthority('PAYOUTS:READ') and hasAuthority('PAYOUTS:MARK_PAYOUT_PAID')")
    public ApiResponse<PayoutResponse> markPaid(@PathVariable UUID id, @RequestBody PayoutMarkPaidRequest request) {
        return ApiResponse.ok(payoutService.markPaid(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Record that the transfer of a payout under processing failed, with a reason")
    @PostMapping("/{id}/fail")
    @PreAuthorize("hasAuthority('PAYOUTS:READ') and hasAuthority('PAYOUTS:MARK_PAYOUT_PAID')")
    public ApiResponse<PayoutResponse> fail(@PathVariable UUID id, @RequestBody PayoutActionRequest request) {
        return ApiResponse.ok(payoutService.fail(id, request, SecurityUtils.requireCurrentUser()));
    }
}
