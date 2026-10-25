package com.iotiq.partnererp.modules.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The signed-in user's profile and effective permissions (used by the client for navigation and
 * button state; the server enforces the same permissions on every request).
 *
 * @param modules     readable modules with their flags and scope
 * @param authorities {@code <MODULE>:<LEVEL>} strings, e.g. {@code LEADS:WRITE}, {@code PAYOUTS:MARK_PAYOUT_PAID}
 */
public record MeResponse(UUID id, String fullName, String email, RoleRef role, boolean superAdmin,
                         boolean mustChangePassword, Instant lastLoginAt, List<ModulePermissionDto> modules,
                         List<String> authorities) {
}
