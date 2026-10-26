package com.iotiq.partnererp.modules.dashboard.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardRequest;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse;
import com.iotiq.partnererp.modules.dashboard.service.DashboardService;
import com.iotiq.partnererp.modules.reports.service.ReportPeriod;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Executive dashboard, analytics and alerts (Phase 12). {@code DASHBOARD:READ} opens them; each section additionally
 * needs read access with the ALL scope to the module it summarises, checked in the service before any query.
 * Responses are never cached (they depend on the caller's permissions).
 */
@Tag(name = "Dashboard")
@RestController
@RequestMapping(AppConstants.API_V1 + "/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "Executive KPIs for the period: partners, leads, estimates, invoices, payments and payouts")
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('DASHBOARD:READ')")
    public ResponseEntity<ApiResponse<DashboardResponse.Overview>> overview(
            @RequestParam(required = false) ReportPeriod.Preset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return noStore(dashboardService.overview(new DashboardRequest(preset, from, to), SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Lead analytics: funnel, trends, conversion, ageing, follow-ups and partner performance")
    @GetMapping("/sales")
    @PreAuthorize("hasAuthority('DASHBOARD:READ')")
    public ResponseEntity<ApiResponse<DashboardResponse.Sales>> sales(
            @RequestParam(required = false) ReportPeriod.Preset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return noStore(dashboardService.sales(new DashboardRequest(preset, from, to), SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Partner performance: rankings, tiers, revenue contribution and payout balances")
    @GetMapping("/partners")
    @PreAuthorize("hasAuthority('DASHBOARD:READ')")
    public ResponseEntity<ApiResponse<DashboardResponse.Partners>> partners(
            @RequestParam(required = false) ReportPeriod.Preset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return noStore(dashboardService.partners(new DashboardRequest(preset, from, to), SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Product analytics from invoice line snapshots (no B2C prices or costs)")
    @GetMapping("/products")
    @PreAuthorize("hasAuthority('DASHBOARD:READ')")
    public ResponseEntity<ApiResponse<DashboardResponse.Products>> products(
            @RequestParam(required = false) ReportPeriod.Preset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return noStore(dashboardService.products(new DashboardRequest(preset, from, to), SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Financial analytics: invoicing, receivables ageing, collections and payout liabilities")
    @GetMapping("/finance")
    @PreAuthorize("hasAuthority('DASHBOARD:READ')")
    public ResponseEntity<ApiResponse<DashboardResponse.Finance>> finance(
            @RequestParam(required = false) ReportPeriod.Preset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return noStore(dashboardService.finance(new DashboardRequest(preset, from, to), SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Operational alerts derived from current data, for the modules the caller can read")
    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('DASHBOARD:READ')")
    public ResponseEntity<ApiResponse<DashboardResponse.Alerts>> alerts() {
        return noStore(dashboardService.alerts(SecurityUtils.requireCurrentUser()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(body));
    }
}
