package com.iotiq.partnererp.modules.settings.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.modules.settings.dto.GstRegistrationRequest;
import com.iotiq.partnererp.modules.settings.dto.GstRegistrationResponse;
import com.iotiq.partnererp.modules.settings.service.GstRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supplier GST registration (Phase 9). Readable by Settings and Invoices readers, because the same details are
 * printed on every invoice; changed with Settings write. The rest of Company Settings arrives in Phase 13.
 */
@Tag(name = "Settings")
@RestController
@RequestMapping(AppConstants.API_V1 + "/settings/gst-registration")
public class GstRegistrationController {

    private final GstRegistrationService service;

    public GstRegistrationController(GstRegistrationService service) {
        this.service = service;
    }

    @Operation(summary = "Supplier GST registration: legal name, address, registered state and GSTIN")
    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS:READ') or hasAuthority('INVOICES:READ')")
    public ApiResponse<GstRegistrationResponse> get() {
        return ApiResponse.ok(service.get());
    }

    @Operation(summary = "Change the supplier GST registration (issued invoices keep their own copy)")
    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS:WRITE')")
    public ApiResponse<GstRegistrationResponse> update(@RequestBody GstRegistrationRequest request) {
        return ApiResponse.ok(service.update(request));
    }
}
