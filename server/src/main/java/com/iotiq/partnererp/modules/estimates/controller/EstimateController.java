package com.iotiq.partnererp.modules.estimates.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.PageResponse;
import com.iotiq.partnererp.common.web.Pageables;
import com.iotiq.partnererp.modules.estimates.dto.EstimateHistoryEntry;
import com.iotiq.partnererp.modules.estimates.dto.EstimateLeadOption;
import com.iotiq.partnererp.modules.estimates.dto.EstimateMeta;
import com.iotiq.partnererp.modules.estimates.dto.EstimatePreview;
import com.iotiq.partnererp.modules.estimates.dto.EstimateRequest;
import com.iotiq.partnererp.modules.estimates.dto.EstimateResponse;
import com.iotiq.partnererp.modules.estimates.dto.EstimateStatusRequest;
import com.iotiq.partnererp.modules.estimates.dto.EstimateSummary;
import com.iotiq.partnererp.modules.estimates.entity.EstimateStatus;
import com.iotiq.partnererp.modules.estimates.service.EstimateService;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Estimates (partner copies). Read/Write/Delete/Export follow the ESTIMATES module permission. Every response here
 * is partner-safe; the internal B2C twin is served by {@link InternalEstimateController}.
 */
@Tag(name = "Estimates")
@RestController
@RequestMapping(AppConstants.API_V1 + "/estimates")
public class EstimateController {

    private static final Set<String> SORTABLE = Set.of("estimateDate", "estimateNumber", "validUntil", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "estimateDate", "estimateNumber");
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);

    private final EstimateService estimateService;

    public EstimateController(EstimateService estimateService) {
        this.estimateService = estimateService;
    }

    @Operation(summary = "Search estimates (number, partner, client, lead number); newest first by default")
    @GetMapping
    @PreAuthorize("hasAuthority('ESTIMATES:READ')")
    public ApiResponse<List<EstimateSummary>> list(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) EstimateStatus status,
                                                   @RequestParam(required = false) UUID partnerId,
                                                   @RequestParam(required = false) UUID leadId,
                                                   @RequestParam(required = false) Boolean expired,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String sort) {
        return PageResponse.of(estimateService.search(new EstimateService.Filters(q, status, partnerId, leadId, expired),
                Pageables.of(page, size, sort, SORTABLE, DEFAULT_SORT), SecurityUtils.requireCurrentUser()))
                .toApiResponse();
    }

    @Operation(summary = "Statuses, buyer options and form defaults (validity days, terms, GST)")
    @GetMapping("/meta")
    @PreAuthorize("hasAuthority('ESTIMATES:READ')")
    public ApiResponse<EstimateMeta> meta() {
        return ApiResponse.ok(estimateService.meta(SecurityUtils.requireCurrentUser()));
    }

    /**
     * Restricted lead picker (Phase 8 decision 4): Estimates writers choose a lead without Leads read. At most 50
     * live, non-Lost leads with only id, lead number, client name and partner; searched by those fields only.
     */
    @Operation(summary = "Eligible leads for a new estimate (minimum fields; Estimates write, no Leads read needed)")
    @GetMapping("/lead-options")
    @PreAuthorize("hasAuthority('ESTIMATES:WRITE')")
    public ApiResponse<List<EstimateLeadOption>> leadOptions(@RequestParam(required = false) String q,
                                                             @RequestParam(required = false) UUID leadId) {
        return ApiResponse.ok(estimateService.leadOptions(q, leadId, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Download the filtered list as CSV (partner-safe columns)")
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('ESTIMATES:EXPORT')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) EstimateStatus status,
                                         @RequestParam(required = false) UUID partnerId,
                                         @RequestParam(required = false) UUID leadId,
                                         @RequestParam(required = false) Boolean expired) {
        String body = estimateService.exportCsv(new EstimateService.Filters(q, status, partnerId, leadId, expired),
                DEFAULT_SORT, SecurityUtils.requireCurrentUser());
        return download("iotiq-estimates.csv", CSV, "﻿" + body);
    }

    @Operation(summary = "Server totals for the editor; nothing is saved (internal block only with VIEW_INTERNAL_B2C)")
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('ESTIMATES:WRITE')")
    public ApiResponse<EstimatePreview> preview(@RequestBody EstimateRequest request,
                                                @RequestParam(required = false) UUID estimateId) {
        return ApiResponse.ok(estimateService.preview(request, estimateId, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "One estimate (partner copy)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ESTIMATES:READ')")
    public ApiResponse<EstimateResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(estimateService.get(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Audited changes, newest first")
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('ESTIMATES:READ')")
    public ApiResponse<List<EstimateHistoryEntry>> history(@PathVariable UUID id) {
        return ApiResponse.ok(estimateService.history(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Printable partner copy (HTML)")
    @GetMapping("/{id}/document")
    @PreAuthorize("hasAuthority('ESTIMATES:READ')")
    public ResponseEntity<byte[]> document(@PathVariable UUID id) {
        EstimateService.Document doc = estimateService.partnerDocument(id, SecurityUtils.requireCurrentUser());
        return download(doc.filename(), HTML, doc.html());
    }

    @Operation(summary = "Create an estimate and its internal B2C copy in one transaction")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ESTIMATES:WRITE')")
    public ApiResponse<EstimateResponse> create(@RequestBody EstimateRequest request) {
        return ApiResponse.ok(estimateService.create(request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Replace an estimate (with version; not when invoiced); the internal copy is rewritten")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ESTIMATES:WRITE')")
    public ApiResponse<EstimateResponse> update(@PathVariable UUID id, @RequestBody EstimateRequest request) {
        return ApiResponse.ok(estimateService.update(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Mark a draft estimate sent (the lead moves to Estimate sent if earlier)")
    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('ESTIMATES:WRITE')")
    public ApiResponse<EstimateResponse> send(@PathVariable UUID id,
                                              @RequestBody(required = false) EstimateStatusRequest request) {
        return ApiResponse.ok(estimateService.send(id, version(request), SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Mark a sent estimate accepted (the lead is Won)")
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('ESTIMATES:WRITE')")
    public ApiResponse<EstimateResponse> accept(@PathVariable UUID id,
                                                @RequestBody(required = false) EstimateStatusRequest request) {
        return ApiResponse.ok(estimateService.accept(id, version(request), SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Mark a sent estimate rejected (the lead is Lost unless already Won)")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('ESTIMATES:WRITE')")
    public ApiResponse<EstimateResponse> reject(@PathVariable UUID id,
                                                @RequestBody(required = false) EstimateStatusRequest request) {
        return ApiResponse.ok(estimateService.reject(id, version(request), SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Delete an estimate (soft; 409 once invoiced)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ESTIMATES:DELETE')")
    public void delete(@PathVariable UUID id) {
        estimateService.delete(id, SecurityUtils.requireCurrentUser());
    }

    private static Long version(EstimateStatusRequest request) {
        return request == null ? null : request.version();
    }

    static ResponseEntity<byte[]> download(String filename, MediaType type, String body) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
