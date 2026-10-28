package com.iotiq.partnererp.modules.users.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.PageResponse;
import com.iotiq.partnererp.common.web.Pageables;
import com.iotiq.partnererp.modules.audit.dto.AuditLogResponse;
import com.iotiq.partnererp.modules.auth.dto.PermissionPreviewResponse;
import com.iotiq.partnererp.modules.auth.dto.UpdateUserPermissionsRequest;
import com.iotiq.partnererp.modules.auth.dto.UserPermissionsResponse;
import com.iotiq.partnererp.modules.auth.entity.UserStatus;
import com.iotiq.partnererp.modules.users.dto.CreateUserRequest;
import com.iotiq.partnererp.modules.users.dto.ResetPasswordRequest;
import com.iotiq.partnererp.modules.users.dto.UpdateUserRequest;
import com.iotiq.partnererp.modules.users.dto.UpdateUserStatusRequest;
import com.iotiq.partnererp.modules.users.dto.UserResponse;
import com.iotiq.partnererp.modules.users.dto.UserVersionRequest;
import com.iotiq.partnererp.modules.users.service.UserService;
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

@Tag(name = "User management")
@RestController
@RequestMapping(AppConstants.API_V1 + "/users")
public class UserController {

    private static final Set<String> SORTABLE = Set.of("fullName", "email", "status", "createdAt", "lastLoginAt");

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "List users (search by name or email, filter by role, status and lockout)")
    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGEMENT:READ')")
    public ApiResponse<List<UserResponse>> list(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) UUID roleId,
                                                @RequestParam(required = false) UserStatus status,
                                                @RequestParam(required = false) Boolean locked,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String sort) {
        return PageResponse.of(userService.search(q, roleId, status, locked,
                Pageables.of(page, size, sort, SORTABLE, Sort.by("fullName")))).toApiResponse();
    }

    @Operation(summary = "Create a user (permissions start from the role template)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_MANAGEMENT:WRITE')")
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userService.create(request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Get a user")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT:READ')")
    public ApiResponse<UserResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(userService.get(id));
    }

    @Operation(summary = "Update a user's name, email and role")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT:WRITE')")
    public ApiResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userService.update(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Activate or deactivate a user")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT:MANAGE_USERS')")
    public ApiResponse<UserResponse> updateStatus(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.ok(userService.updateStatus(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Clear a temporary sign-in lockout")
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT:MANAGE_USERS')")
    public ApiResponse<UserResponse> unlock(@PathVariable UUID id, @Valid @RequestBody UserVersionRequest request) {
        return ApiResponse.ok(userService.unlock(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Set a temporary password (must be changed at sign-in; every session is revoked)")
    @PostMapping("/{id}/password-reset")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT:MANAGE_USERS')")
    public ApiResponse<UserResponse> resetPassword(@PathVariable UUID id,
                                                   @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok(userService.resetPassword(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "The account's history: sign-ins, lockouts, password, role, status and permission changes")
    @GetMapping("/{id}/activity")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT:READ')")
    public ApiResponse<List<AuditLogResponse>> activity(@PathVariable UUID id,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(userService.activity(id,
                Pageables.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "createdAt")),
                SecurityUtils.requireCurrentUser())).toApiResponse();
    }

    @Operation(summary = "A user's module and action permissions")
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT:READ')")
    public ApiResponse<UserPermissionsResponse> permissions(@PathVariable UUID id) {
        return ApiResponse.ok(userService.permissions(id, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Validate proposed permissions and show what would change, without saving (Super Admin only)")
    @PostMapping("/{id}/permissions/preview")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT:MANAGE_PERMISSIONS')")
    public ApiResponse<PermissionPreviewResponse> previewPermissions(@PathVariable UUID id,
                                                                     @Valid @RequestBody UpdateUserPermissionsRequest request) {
        return ApiResponse.ok(userService.previewPermissions(id, request, SecurityUtils.requireCurrentUser()));
    }

    @Operation(summary = "Replace a user's module and action permissions (Super Admin only)")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('USER_MANAGEMENT:MANAGE_PERMISSIONS')")
    public ApiResponse<UserPermissionsResponse> replacePermissions(@PathVariable UUID id,
                                                                   @Valid @RequestBody UpdateUserPermissionsRequest request) {
        return ApiResponse.ok(userService.replacePermissions(id, request, SecurityUtils.requireCurrentUser()));
    }
}
