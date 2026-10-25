package com.iotiq.partnererp.modules.leads.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.PageResponse;
import com.iotiq.partnererp.common.web.Pageables;
import com.iotiq.partnererp.modules.leads.dto.LeadHistoryEntry;
import com.iotiq.partnererp.modules.leads.dto.LeadMeta;
import com.iotiq.partnererp.modules.leads.dto.LeadRequest;
import com.iotiq.partnererp.modules.leads.dto.LeadResponse;
import com.iotiq.partnererp.modules.leads.dto.LeadStageRequest;
import com.iotiq.partnererp.modules.leads.dto.LeadSummary;
import com.iotiq.partnererp.modules.leads.service.LeadService;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Leads and deal registration. Read/Write follow the LEADS module permission; acknowledging and reopening a Won or
 * Lost lead need {@code LEADS:APPROVE}; delete (soft) needs {@code LEADS:DELETE}.
 */
@Tag(name = "Leads")
@RestController
@RequestMapping(AppConstants.API_V1 + "/leads")
public class LeadController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "clientName", "city", "budget", "expectedClose");

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @Operation(summary = "Search leads (client, city, partner name); newest first by default")
    @GetMapping
    @PreAuthorize("hasAuthority('LEADS:READ')")
    public ApiResponse<List<LeadSummary>> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) LeadStage stage,
                                               @RequestParam(required = false) UUID partnerId,
                                               @RequestParam(required = false) Boolean conflict,
                                               @RequestParam(required = false) Boolean slaBreach,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String sort) {
        LeadService.Filters filters = new LeadService.Filters(q, stage, partnerId, conflict, slaBreach);
        return PageResponse.of(leadService.search(filters,
                Pageables.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt")),
                SecurityUtils.requireCurrentUser())).toApiResponse();
    }

    @Operation(summary = "Stages, project types, referring partners and SLA settings for forms and filters")
    @GetMapping("/meta")
    @PreAuthorize("hasAuthority('LEADS:READ')")
    public ApiResponse<LeadMeta> meta() {
        return ApiResponse.ok(leadService.meta());
    }

    @Operation(summary = "One lead with its SLA block and the partner currently protecting the same phone")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEADS:READ')")
    public ApiResponse<LeadResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(leadService.get(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Stage history, oldest first")
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('LEADS:READ')")
    public ApiResponse<List<LeadHistoryEntry>> history(@PathVariable UUID id) {
        return ApiResponse.ok(leadService.history(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Create a lead (conflict=true with conflictPartnerName when the phone is protected elsewhere)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LEADS:WRITE')")
    public ApiResponse<LeadResponse> create(@RequestBody LeadRequest request) {
        return ApiResponse.ok(leadService.create(request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Replace a lead's details (with version; reopening a Won/Lost lead needs LEADS:APPROVE)")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('LEADS:WRITE')")
    public ApiResponse<LeadResponse> update(@PathVariable UUID id, @RequestBody LeadRequest request) {
        return ApiResponse.ok(leadService.update(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Change the stage (with version and optional reason; reopening needs LEADS:APPROVE)")
    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasAuthority('LEADS:WRITE')")
    public ApiResponse<LeadResponse> changeStage(@PathVariable UUID id, @Valid @RequestBody LeadStageRequest request) {
        return ApiResponse.ok(leadService.changeStage(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Acknowledge an open lead: lock the fee rate and start protection (422 for Won/Lost, 409 if already done)")
    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('LEADS:APPROVE')")
    public ApiResponse<LeadResponse> acknowledge(@PathVariable UUID id) {
        return ApiResponse.ok(leadService.acknowledge(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Delete a lead (soft; 409 while estimates, invoices or payments point at it)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('LEADS:DELETE')")
    public void delete(@PathVariable UUID id) {
        leadService.delete(id, SecurityUtils.requireCurrentUser());
    }
}
