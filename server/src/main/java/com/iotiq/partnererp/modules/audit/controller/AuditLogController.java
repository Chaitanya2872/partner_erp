package com.iotiq.partnererp.modules.audit.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.PageResponse;
import com.iotiq.partnererp.common.web.Pageables;
import com.iotiq.partnererp.modules.audit.dto.AuditLogResponse;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditCatalog;
import com.iotiq.partnererp.modules.audit.service.AuditLogQueryService;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.security.AuthenticatedUser;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The audit trail (AUDIT_LOGS). Entries are append-only; values were masked when written (PAN, bank account, GSTIN,
 * references) and secrets are never recorded. Product B2C fields are hidden without PRODUCTS:VIEW_INTERNAL_B2C.
 * Responses are not cached.
 */
@Tag(name = "Audit logs")
@RestController
@RequestMapping(AppConstants.API_V1 + "/audit-logs")
public class AuditLogController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final AuditLogQueryService queryService;
    private final AuditService auditService;

    public AuditLogController(AuditLogQueryService queryService, AuditService auditService) {
        this.queryService = queryService;
        this.auditService = auditService;
    }

    @Operation(summary = "Audit trail, newest first")
    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_LOGS:READ')")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> list(@RequestParam(required = false) String module,
                                                                    @RequestParam(required = false) String entityType,
                                                                    @RequestParam(required = false) UUID entityId,
                                                                    @RequestParam(required = false) String action,
                                                                    @RequestParam(required = false) UUID userId,
                                                                    @RequestParam(required = false) Instant from,
                                                                    @RequestParam(required = false) Instant to,
                                                                    @RequestParam(required = false) String q,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "50") int size) {
        AuditLogQueryService.Filter filter = new AuditLogQueryService.Filter(module, entityType, entityId, action, userId,
                from, to, q);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageResponse.of(queryService.search(filter,
                Pageables.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "createdAt")),
                internalProductFields(SecurityUtils.requireCurrentUser()))).toApiResponse());
    }

    @Operation(summary = "Modules and the actions each one records")
    @GetMapping("/catalog")
    @PreAuthorize("hasAuthority('AUDIT_LOGS:READ')")
    public ApiResponse<List<AuditCatalog.Module>> catalog() {
        return ApiResponse.ok(AuditCatalog.MODULES);
    }

    @Operation(summary = "People to filter by (name and email)")
    @GetMapping("/actors")
    @PreAuthorize("hasAuthority('AUDIT_LOGS:READ')")
    public ApiResponse<List<AuditLogQueryService.Actor>> actors(@RequestParam(required = false) String q) {
        return ApiResponse.ok(queryService.actors(q));
    }

    @Operation(summary = "Export the filtered audit trail as CSV (at most 10,000 entries; the export itself is audited)")
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('AUDIT_LOGS:EXPORT')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String module,
                                         @RequestParam(required = false) String entityType,
                                         @RequestParam(required = false) UUID entityId,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) UUID userId,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to,
                                         @RequestParam(required = false) String q) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        AuditLogQueryService.Filter filter = new AuditLogQueryService.Filter(module, entityType, entityId, action, userId,
                from, to, q);
        AuditLogQueryService.Export export = queryService.export(filter, internalProductFields(user));
        auditService.record(AuditActions.AUDIT_LOG_EXPORTED, AuditActions.ENTITY_AUDIT_LOG, null, null,
                AuditService.values("module", module, "entityType", entityType, "entityId", entityId, "action", action,
                        "userId", userId, "from", from, "to", to, "search", q, "rows", export.rows()));
        String filename = "iotiq-audit-log-" + LocalDate.now(ZoneId.of("Asia/Kolkata")) + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(("﻿" + export.csv()).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean internalProductFields(AuthenticatedUser user) {
        return user.permissions().canPerform("PRODUCTS", "VIEW_INTERNAL_B2C");
    }
}
