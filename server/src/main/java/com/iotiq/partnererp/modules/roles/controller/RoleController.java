package com.iotiq.partnererp.modules.roles.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.modules.roles.dto.RoleResponse;
import com.iotiq.partnererp.modules.roles.dto.UpdateRoleRequest;
import com.iotiq.partnererp.modules.roles.service.RoleService;
import com.iotiq.partnererp.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Roles are readable by anyone who manages users (role dropdowns) or roles; only Role Management Write
 * (Super Admin only) can rename them. User counts are shown to Role Management readers only.
 */
@Tag(name = "Role management")
@RestController
@RequestMapping(AppConstants.API_V1 + "/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "List the active system roles")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_MANAGEMENT:READ', 'ROLE_MANAGEMENT:READ')")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.ok(roleService.list(canSeeAssignments()));
    }

    @Operation(summary = "Get a role")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('USER_MANAGEMENT:READ', 'ROLE_MANAGEMENT:READ')")
    public ApiResponse<RoleResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(roleService.get(id, canSeeAssignments()));
    }

    @Operation(summary = "Rename a role or edit its description (code and id are immutable)")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT:WRITE')")
    public ApiResponse<RoleResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.ok(roleService.update(id, request));
    }

    private static boolean canSeeAssignments() {
        return SecurityUtils.currentUser().map(u -> u.permissions().canRead("ROLE_MANAGEMENT")).orElse(false);
    }
}
