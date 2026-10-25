package com.iotiq.partnererp.modules.partners.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.enums.PartnerStatus;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.PageResponse;
import com.iotiq.partnererp.common.web.Pageables;
import com.iotiq.partnererp.modules.partners.dto.PartnerMeta;
import com.iotiq.partnererp.modules.partners.dto.PartnerPerformance;
import com.iotiq.partnererp.modules.partners.dto.PartnerRequest;
import com.iotiq.partnererp.modules.partners.dto.PartnerResponse;
import com.iotiq.partnererp.modules.partners.dto.PartnerStatusRequest;
import com.iotiq.partnererp.modules.partners.dto.PartnerSummary;
import com.iotiq.partnererp.modules.partners.dto.TierReviewDtos;
import com.iotiq.partnererp.modules.partners.entity.AgreementStatus;
import com.iotiq.partnererp.modules.partners.entity.PartnerCategory;
import com.iotiq.partnererp.modules.partners.service.PartnerService;
import com.iotiq.partnererp.modules.partners.service.TierReviewService;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Partner master. Read/Write follow the PARTNERS module permission; tier changes and tier reviews also need
 * {@code PARTNERS:APPROVE}; full PAN and bank numbers need {@code PARTNERS:VIEW_PARTNER_SENSITIVE}. There is no
 * delete endpoint: partners are deactivated (BR-P10).
 */
@Tag(name = "Partners")
@RestController
@RequestMapping(AppConstants.API_V1 + "/partners")
public class PartnerController {

    private static final Set<String> SORTABLE = Set.of("name", "city", "category", "status", "agreementStatus", "createdAt");

    private final PartnerService partnerService;
    private final TierReviewService tierReviewService;

    public PartnerController(PartnerService partnerService, TierReviewService tierReviewService) {
        this.partnerService = partnerService;
        this.tierReviewService = tierReviewService;
    }

    @Operation(summary = "Search partners (name, city, category, contact, phone); sorted by name by default")
    @GetMapping
    @PreAuthorize("hasAuthority('PARTNERS:READ')")
    public ApiResponse<List<PartnerSummary>> list(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) PartnerStatus status,
                                                  @RequestParam(required = false) PartnerCategory category,
                                                  @RequestParam(required = false) Integer tier,
                                                  @RequestParam(required = false) AgreementStatus agreementStatus,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String sort) {
        PartnerService.Filters filters = new PartnerService.Filters(q, status, category, tier, agreementStatus);
        return PageResponse.of(partnerService.search(filters,
                Pageables.of(page, size, sort, SORTABLE, Sort.by("name")), SecurityUtils.requireCurrentUser()))
                .toApiResponse();
    }

    @Operation(summary = "Categories, statuses, agreement statuses and tiers for forms and filters")
    @GetMapping("/meta")
    @PreAuthorize("hasAuthority('PARTNERS:READ')")
    public ApiResponse<PartnerMeta> meta() {
        return ApiResponse.ok(partnerService.meta());
    }

    @Operation(summary = "One partner (PAN and bank account masked unless VIEW_PARTNER_SENSITIVE)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PARTNERS:READ')")
    public ApiResponse<PartnerResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(partnerService.get(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Create a partner (tier other than 1 or programme needs PARTNERS:APPROVE)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PARTNERS:WRITE')")
    public ApiResponse<PartnerResponse> create(@RequestBody PartnerRequest request) {
        return ApiResponse.ok(partnerService.create(request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Replace a partner's details (with version; tier/programme changes need PARTNERS:APPROVE)")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PARTNERS:WRITE')")
    public ApiResponse<PartnerResponse> update(@PathVariable UUID id, @RequestBody PartnerRequest request) {
        return ApiResponse.ok(partnerService.update(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Change status (activate / deactivate)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PARTNERS:WRITE')")
    public ApiResponse<PartnerResponse> changeStatus(@PathVariable UUID id, @Valid @RequestBody PartnerStatusRequest request) {
        return ApiResponse.ok(partnerService.changeStatus(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Detail tiles: tier, paid revenue (12 m), next-tier progress, fees, payout readiness")
    @GetMapping("/{id}/performance")
    @PreAuthorize("hasAuthority('PARTNERS:READ')")
    public ApiResponse<PartnerPerformance> performance(@PathVariable UUID id) {
        return ApiResponse.ok(partnerService.performance(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Tier review history, newest first")
    @GetMapping("/{id}/tier-reviews")
    @PreAuthorize("hasAuthority('PARTNERS:READ')")
    public ApiResponse<List<TierReviewDtos.HistoryEntry>> tierHistory(@PathVariable UUID id) {
        return ApiResponse.ok(partnerService.tierHistory(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Preview a tier review (all non-Inactive partners, or the given ones)")
    @PostMapping("/tier-review/preview")
    @PreAuthorize("hasAuthority('PARTNERS:APPROVE')")
    public ApiResponse<TierReviewDtos.Preview> previewTierReview(@RequestBody(required = false) TierReviewDtos.PreviewRequest request) {
        return ApiResponse.ok(tierReviewService.preview(request == null ? null : request.partnerIds(),
                SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Apply previewed tier review changes (recomputed; 409 if any changed since the preview)")
    @PostMapping("/tier-review/apply")
    @PreAuthorize("hasAuthority('PARTNERS:APPROVE')")
    public ApiResponse<TierReviewDtos.ApplyResult> applyTierReview(@Valid @RequestBody TierReviewDtos.ApplyRequest request) {
        return ApiResponse.ok(tierReviewService.apply(request.items(), SecurityUtils.requireCurrentUser()));
    }
}
