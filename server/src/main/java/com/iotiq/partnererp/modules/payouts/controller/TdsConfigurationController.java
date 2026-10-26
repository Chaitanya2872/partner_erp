package com.iotiq.partnererp.modules.payouts.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.modules.payouts.dto.TdsConfigurationRequest;
import com.iotiq.partnererp.modules.payouts.dto.TdsConfigurationResponse;
import com.iotiq.partnererp.modules.payouts.dto.TdsConfirmRequest;
import com.iotiq.partnererp.modules.payouts.service.TdsConfigurationService;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TDS settings for partner payouts. Settings write edits the draft; Payouts approve confirms it (a second person).
 */
@Tag(name = "Settings")
@RestController
@RequestMapping(AppConstants.API_V1 + "/settings/tds")
public class TdsConfigurationController {

    private final TdsConfigurationService service;

    public TdsConfigurationController(TdsConfigurationService service) {
        this.service = service;
    }

    @Operation(summary = "The TDS configuration in force, the draft and the confirmed versions")
    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS:READ') or hasAuthority('PAYOUTS:READ')")
    public ApiResponse<TdsConfigurationResponse> get() {
        return ApiResponse.ok(service.get(SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Create or update the draft TDS configuration")
    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS:WRITE')")
    public ApiResponse<TdsConfigurationResponse> save(@RequestBody TdsConfigurationRequest request) {
        return ApiResponse.ok(service.saveDraft(request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Confirm the draft (not by the person who last edited it); confirmed versions never change")
    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority('PAYOUTS:APPROVE')")
    public ApiResponse<TdsConfigurationResponse> confirm(@RequestBody TdsConfirmRequest request) {
        return ApiResponse.ok(service.confirm(request, SecurityUtils.requireCurrentUser()));
    }
}
