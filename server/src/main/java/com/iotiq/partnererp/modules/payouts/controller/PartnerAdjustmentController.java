package com.iotiq.partnererp.modules.payouts.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.PageResponse;
import com.iotiq.partnererp.common.web.Pageables;
import com.iotiq.partnererp.modules.payouts.dto.AdjustmentDecisionRequest;
import com.iotiq.partnererp.modules.payouts.dto.AdjustmentRequest;
import com.iotiq.partnererp.modules.payouts.dto.AdjustmentResponse;
import com.iotiq.partnererp.modules.payouts.entity.PayoutEnums.AdjustmentKind;
import com.iotiq.partnererp.modules.payouts.entity.PayoutEnums.AdjustmentStatus;
import com.iotiq.partnererp.modules.payouts.service.PartnerAdjustmentService;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Partner adjustments carried into payouts. Entering one needs Payouts write; approving or rejecting needs Payouts
 * approve, by someone other than the person who entered it. Reversal adjustments are created by the system.
 */
@Tag(name = "Payouts")
@RestController
@RequestMapping(AppConstants.API_V1 + "/payouts/adjustments")
public class PartnerAdjustmentController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "amount", "adjustmentNumber");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final PartnerAdjustmentService service;

    public PartnerAdjustmentController(PartnerAdjustmentService service) {
        this.service = service;
    }

    @Operation(summary = "Search adjustments (number, reason or partner); newest first")
    @GetMapping
    @PreAuthorize("hasAuthority('PAYOUTS:READ')")
    public ApiResponse<List<AdjustmentResponse>> list(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) UUID partnerId,
                                                      @RequestParam(required = false) AdjustmentStatus status,
                                                      @RequestParam(required = false) AdjustmentKind kind,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String sort) {
        return PageResponse.of(service.search(new PartnerAdjustmentService.Filters(q, partnerId, status, kind),
                Pageables.of(page, size, sort, SORTABLE, DEFAULT_SORT), SecurityUtils.requireCurrentUser()))
                .toApiResponse();
    }

    @Operation(summary = "One adjustment")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYOUTS:READ')")
    public ApiResponse<AdjustmentResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Enter a manual adjustment with a reason (201; 200 when the idempotency key was already used)")
    @PostMapping
    @PreAuthorize("hasAuthority('PAYOUTS:WRITE')")
    public ResponseEntity<ApiResponse<AdjustmentResponse>> create(@RequestBody AdjustmentRequest request) {
        PartnerAdjustmentService.Recorded recorded = service.create(request, SecurityUtils.requireCurrentUser());
        return ResponseEntity.status(recorded.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(recorded.adjustment()));
    }

    @Operation(summary = "Approve a manual adjustment (not by the person who entered it)")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PAYOUTS:APPROVE')")
    public ApiResponse<AdjustmentResponse> approve(@PathVariable UUID id,
                                                   @RequestBody(required = false) AdjustmentDecisionRequest request) {
        return ApiResponse.ok(service.approve(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Reject a manual adjustment with a reason")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('PAYOUTS:APPROVE')")
    public ApiResponse<AdjustmentResponse> reject(@PathVariable UUID id,
                                                  @RequestBody(required = false) AdjustmentDecisionRequest request) {
        return ApiResponse.ok(service.reject(id, request, SecurityUtils.requireCurrentUser()));
    }
}
