package com.iotiq.partnererp.modules.settings.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.modules.settings.dto.SettingRequest;
import com.iotiq.partnererp.modules.settings.dto.SettingResponse;
import com.iotiq.partnererp.modules.settings.service.AdministrationInfoService;
import com.iotiq.partnererp.modules.settings.service.SettingService;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Company Settings and the read-only administration views (Phase 13). The GST registration (Phase 9) and TDS
 * configuration (Phase 11) keep their own endpoints.
 */
@Tag(name = "Settings")
@RestController
@RequestMapping(AppConstants.API_V1 + "/settings")
public class SettingController {

    private final SettingService settingService;
    private final AdministrationInfoService infoService;

    public SettingController(SettingService settingService, AdministrationInfoService infoService) {
        this.settingService = settingService;
        this.infoService = infoService;
    }

    @Operation(summary = "Company profile, document defaults, lead SLA targets and commercial settings")
    @GetMapping("/company")
    @PreAuthorize("hasAuthority('SETTINGS:READ')")
    public ApiResponse<SettingResponse.Company> company() {
        return ApiResponse.ok(settingService.company(SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Change contact details, default terms, document defaults and SLA targets")
    @PutMapping("/company")
    @PreAuthorize("hasAuthority('SETTINGS:WRITE')")
    public ApiResponse<SettingResponse.Company> updateCompany(@RequestBody SettingRequest.Company request) {
        return ApiResponse.ok(settingService.updateCompany(request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Change lead protection (Manage commercial settings)")
    @PutMapping("/commercial")
    @PreAuthorize("hasAuthority('SETTINGS:WRITE') and hasAuthority('SETTINGS:MANAGE_SETTINGS')")
    public ApiResponse<SettingResponse.Company> updateCommercial(@RequestBody SettingRequest.Commercial request) {
        return ApiResponse.ok(settingService.updateCommercial(request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "The four partner tiers")
    @GetMapping("/tiers")
    @PreAuthorize("hasAuthority('SETTINGS:READ') or hasAuthority('PARTNERS:READ')")
    public ApiResponse<SettingResponse.Tiers> tiers() {
        return ApiResponse.ok(settingService.tiers(SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Change the partner tiers (stored fee snapshots are never recalculated)")
    @PutMapping("/tiers")
    @PreAuthorize("hasAuthority('SETTINGS:WRITE') and hasAuthority('SETTINGS:MANAGE_SETTINGS')")
    public ApiResponse<SettingResponse.Tiers> updateTiers(@RequestBody SettingRequest.Tiers request) {
        return ApiResponse.ok(settingService.updateTiers(request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Document number series (read-only)")
    @GetMapping("/numbering")
    @PreAuthorize("hasAuthority('SETTINGS:READ')")
    public ApiResponse<List<SettingResponse.NumberSeries>> numbering() {
        return ApiResponse.ok(infoService.numbering());
    }

    @Operation(summary = "Password, lockout and session policy in force (read-only, no secrets)")
    @GetMapping("/security")
    @PreAuthorize("hasAuthority('SETTINGS:READ') or hasAuthority('USER_MANAGEMENT:READ')")
    public ApiResponse<SettingResponse.Security> security() {
        return ApiResponse.ok(infoService.security());
    }

    @Operation(summary = "Financial and security controls and where they are enforced (read-only)")
    @GetMapping("/controls")
    @PreAuthorize("hasAuthority('SETTINGS:READ')")
    public ApiResponse<List<SettingResponse.Control>> controls() {
        return ApiResponse.ok(infoService.controls());
    }
}
