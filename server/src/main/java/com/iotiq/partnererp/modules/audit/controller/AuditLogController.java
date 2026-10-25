package com.iotiq.partnererp.modules.audit.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.PageResponse;
import com.iotiq.partnererp.common.web.Pageables;
import com.iotiq.partnererp.modules.audit.dto.AuditLogResponse;
import com.iotiq.partnererp.modules.audit.service.AuditLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Tag(name = "Audit logs")
@RestController
@RequestMapping(AppConstants.API_V1 + "/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService queryService;

    public AuditLogController(AuditLogQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "Audit trail, newest first")
    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_LOGS:READ')")
    public ApiResponse<List<AuditLogResponse>> list(@RequestParam(required = false) String entityType,
                                                    @RequestParam(required = false) UUID entityId,
                                                    @RequestParam(required = false) String action,
                                                    @RequestParam(required = false) UUID userId,
                                                    @RequestParam(required = false) Instant from,
                                                    @RequestParam(required = false) Instant to,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(queryService.search(entityType, entityId, action, userId, from, to,
                Pageables.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "createdAt")))).toApiResponse();
    }
}
