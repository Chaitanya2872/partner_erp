package com.iotiq.partnererp.modules.reports.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.modules.reports.dto.ReportRequest;
import com.iotiq.partnererp.modules.reports.dto.ReportResponse;
import com.iotiq.partnererp.modules.reports.service.ReportPeriod;
import com.iotiq.partnererp.modules.reports.service.ReportService;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reports (FR-10). {@code REPORTS:READ} opens them, and each report also needs read access with the ALL scope to the
 * module that owns its records. Exports need {@code REPORTS:EXPORT} and that module's EXPORT permission (checked in
 * the service), so they never bypass the module's own export control.
 */
@Tag(name = "Reports")
@RestController
@RequestMapping(AppConstants.API_V1 + "/reports")
public class ReportController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "The reports, with the columns, filters and actions available to the caller")
    @GetMapping
    @PreAuthorize("hasAuthority('REPORTS:READ')")
    public ResponseEntity<ApiResponse<List<ReportResponse.ReportInfo>>> catalog() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(reportService.catalog(SecurityUtils.requireCurrentUser())));
    }

    @Operation(summary = "Partners for the report filter (needs Partners read with the ALL scope)")
    @GetMapping("/partner-options")
    @PreAuthorize("hasAuthority('REPORTS:READ')")
    public ApiResponse<List<ReportResponse.PartnerOption>> partnerOptions(@RequestParam(required = false) String q) {
        return ApiResponse.ok(reportService.partnerOptions(q, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "One page of a report with totals over every matching row")
    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('REPORTS:READ')")
    public ResponseEntity<ApiResponse<ReportResponse.Result>> run(
            @PathVariable String code,
            @RequestParam(required = false) ReportPeriod.Preset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID partnerId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ReportRequest request = new ReportRequest(preset, from, to, status, partnerId, q, sort, page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(reportService.run(code, request, SecurityUtils.requireCurrentUser())));
    }

    @Operation(summary = "Download every matching row as CSV (at most 10,000 rows)")
    @GetMapping("/{code}/export")
    @PreAuthorize("hasAuthority('REPORTS:EXPORT')")
    public ResponseEntity<byte[]> export(
            @PathVariable String code,
            @RequestParam(required = false) ReportPeriod.Preset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID partnerId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort) {
        ReportRequest request = new ReportRequest(preset, from, to, status, partnerId, q, sort, 0, 1);
        String body = reportService.export(code, request, SecurityUtils.requireCurrentUser());
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(reportService.filename(code, request)).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(("﻿" + body).getBytes(StandardCharsets.UTF_8));
    }
}
