package com.iotiq.partnererp.modules.estimates.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.modules.estimates.dto.InternalEstimateDocument;
import com.iotiq.partnererp.modules.estimates.service.EstimateService;
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
 * Internal B2C copies of estimates (ADR-06, P3-06): only for callers with {@code INTERNAL_B2C} read as well as
 * Estimates read. Kept under {@code /internal/**} so partner-facing routes can never return them.
 */
@Tag(name = "Internal B2C")
@RestController
@RequestMapping(AppConstants.API_V1 + "/internal/estimates")
public class InternalEstimateController {

    private final EstimateService estimateService;

    public InternalEstimateController(EstimateService estimateService) {
        this.estimateService = estimateService;
    }

    @Operation(summary = "Internal B2C copy of an estimate: B2C names and rates, partner totals, uplift")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INTERNAL_B2C:READ') and hasAuthority('ESTIMATES:READ')")
    public ApiResponse<InternalEstimateDocument> get(@PathVariable UUID id) {
        return ApiResponse.ok(estimateService.internalDocument(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Printable internal B2C copy (HTML, stamped INTERNAL ONLY)")
    @GetMapping("/{id}/document")
    @PreAuthorize("hasAuthority('INTERNAL_B2C:READ') and hasAuthority('ESTIMATES:READ')")
    public ResponseEntity<byte[]> document(@PathVariable UUID id) {
        EstimateService.Document doc = estimateService.internalDocumentHtml(id, SecurityUtils.requireCurrentUser());
        return EstimateController.download(doc.filename(), EstimateController.HTML, doc.html());
    }
}
