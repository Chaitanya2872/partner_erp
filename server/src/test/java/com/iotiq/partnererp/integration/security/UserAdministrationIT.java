package com.iotiq.partnererp.integration.security;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13 user administration: lockout and unlock, password reset, account activity, the permission preview, user
 * overrides against the role default, role default permissions and the escalation guards around all of them.
 */
class UserAdministrationIT extends ApiIntegrationTest {

    private static final String TEMPORARY = "Reset-Temporary-77";

    @Test
    void lockedAccountsAreListedAndUnlockedByWhoeverManagesThem() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser manager = createUser(RoleCode.ADMIN);
        grantUserManagement(sa, manager);
        Session managerSession = login(manager);
        TestUser locked = createUser(RoleCode.USER);
        TestUser lockedAdmin = createUser(RoleCode.ADMIN);
        jdbc.update("update users set locked_until = now() + interval '10 minutes', failed_login_count = 0 where id in (?, ?)",
                locked.id(), lockedAdmin.id());

        assertThat(loginResult(locked.email(), PASSWORD).getResponse().getStatus()).isEqualTo(423);
        String list = mvc.perform(get("/api/v1/users").with(bearer(managerSession)).param("locked", "true").param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(list, "$.data[*].id");
        assertThat(ids).contains(locked.id().toString(), lockedAdmin.id().toString());
        assertThat(JsonPath.<List<Boolean>>read(list, "$.data[*].locked")).containsOnly(true);
        String unlockedList = mvc.perform(get("/api/v1/users").with(bearer(managerSession)).param("locked", "false")
                        .param("q", locked.email())).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(unlockedList, "$.data.length()")).isZero();

        // An Admin manages User accounts only.
        mvc.perform(withJson(post("/api/v1/users/{id}/unlock", lockedAdmin.id()).with(bearer(managerSession)),
                        body(Map.of("version", userVersion(lockedAdmin.id())))))
                .andExpect(status().isForbidden());
        // Without Manage users.
        Session reader = login(createUser(RoleCode.ADMIN));
        mvc.perform(withJson(post("/api/v1/users/{id}/unlock", locked.id()).with(bearer(reader)),
                        body(Map.of("version", userVersion(locked.id())))))
                .andExpect(status().isForbidden());

        mvc.perform(withJson(post("/api/v1/users/{id}/unlock", locked.id()).with(bearer(managerSession)),
                        body(Map.of("version", userVersion(locked.id())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.lockedUntil").doesNotExist());
        assertThat(count("select count(*) from audit_logs where action = 'ACCOUNT_UNLOCKED' and entity_id = ? and user_id = ?",
                locked.id(), manager.id())).isEqualTo(1);
        login(locked);
        // Unlocking an account that isn't locked changes nothing and isn't audited.
        mvc.perform(withJson(post("/api/v1/users/{id}/unlock", locked.id()).with(bearer(managerSession)),
                        body(Map.of("version", userVersion(locked.id())))))
                .andExpect(status().isOk());
        assertThat(count("select count(*) from audit_logs where action = 'ACCOUNT_UNLOCKED' and entity_id = ?", locked.id()))
                .isEqualTo(1);
    }

    @Test
    void passwordResetForcesAChangeRevokesSessionsAndRecordsNoSecret() throws Exception {
        TestUser saUser = createUser(RoleCode.SUPER_ADMIN);
        Session sa = login(saUser);
        TestUser target = createUser(RoleCode.ADMIN);
        Session targetSession = login(target);
        mvc.perform(get("/api/v1/auth/me").with(bearer(targetSession))).andExpect(status().isOk());

        mvc.perform(withJson(post("/api/v1/users/{id}/password-reset", target.id()).with(bearer(sa)),
                        body(Map.of("temporaryPassword", "short", "version", userVersion(target.id())))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("temporaryPassword"));
        mvc.perform(withJson(post("/api/v1/users/{id}/password-reset", target.id()).with(bearer(sa)),
                        body(Map.of("temporaryPassword", TEMPORARY, "version", userVersion(target.id())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        // Existing sessions end at once; the old password stops working; the temporary one must be changed first.
        mvc.perform(get("/api/v1/auth/me").with(bearer(targetSession))).andExpect(status().isUnauthorized());
        assertThat(loginResult(target.email(), PASSWORD).getResponse().getStatus()).isEqualTo(401);
        Session temporary = login(target.email(), TEMPORARY);
        mvc.perform(get("/api/v1/partners").with(bearer(temporary)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));

        String audit = jdbc.queryForObject("select coalesce(old_values::text, '') || new_values::text from audit_logs "
                + "where action = 'PASSWORD_RESET' and entity_id = ?", String.class, target.id());
        assertThat(audit).doesNotContain(TEMPORARY).doesNotContain("$2").contains("sessionsRevoked");
        assertThat(count("select count(*) from audit_logs where action = 'PASSWORD_RESET' and entity_id = ? and user_id = ?",
                target.id(), saUser.id())).isEqualTo(1);

        // Not for oneself (use change password).
        mvc.perform(withJson(post("/api/v1/users/{id}/password-reset", saUser.id()).with(bearer(sa)),
                        body(Map.of("temporaryPassword", TEMPORARY, "version", userVersion(saUser.id())))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminsCannotResetOrUnlockProtectedAccountsOrEscalate() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser manager = createUser(RoleCode.ADMIN);
        grantUserManagement(sa, manager);
        Session managerSession = login(manager);
        TestUser superAdmin = createUser(RoleCode.SUPER_ADMIN);
        TestUser user = createUser(RoleCode.USER);

        mvc.perform(withJson(post("/api/v1/users/{id}/password-reset", superAdmin.id()).with(bearer(managerSession)),
                        body(Map.of("temporaryPassword", TEMPORARY, "version", userVersion(superAdmin.id())))))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/v1/users/{id}/password-reset", user.id()).with(bearer(managerSession)),
                        body(Map.of("temporaryPassword", TEMPORARY, "version", userVersion(user.id())))))
                .andExpect(status().isOk());
        // Permissions, the preview and role defaults stay with Super Admins, including for oneself.
        Map<String, Object> grantAll = Map.of("modules", List.of(Map.of("moduleCode", "PAYOUTS", "read", true,
                "write", true, "approve", true)), "actions", List.of());
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", manager.id()).with(bearer(managerSession)), body(grantAll)))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/v1/users/{id}/permissions/preview", manager.id()).with(bearer(managerSession)),
                        body(grantAll)))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/v1/users/{id}/permissions/preview", user.id()).with(bearer(managerSession)),
                        body(grantAll)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/roles/{id}/permissions", roleId(RoleCode.USER)).with(bearer(managerSession)))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(put("/api/v1/roles/{id}/permissions", roleId(RoleCode.ADMIN)).with(bearer(managerSession)),
                        body(Map.of("modules", List.of(), "version", 0))))
                .andExpect(status().isForbidden());
        // A Super Admin can't preview changes to their own permissions either.
        TestUser saUser = createUser(RoleCode.SUPER_ADMIN);
        mvc.perform(withJson(post("/api/v1/users/{id}/permissions/preview", saUser.id()).with(bearer(login(saUser))),
                        body(grantAll)))
                .andExpect(status().isForbidden());
    }

    @Test
    void previewValidatesAndDiffsWithoutSaving() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser target = createUser(RoleCode.ADMIN);
        long auditsBefore = count("select count(*) from audit_logs where entity_id = ?", target.id());

        Map<String, Object> proposal = Map.of("modules", List.of(
                Map.of("moduleCode", "PARTNERS", "read", true, "write", true, "approve", true, "dataScope", "SELF"),
                Map.of("moduleCode", "PAYOUTS", "read", true)), "actions", List.of("VIEW_PARTNER_SENSITIVE"));
        String preview = mvc.perform(withJson(post("/api/v1/users/{id}/permissions/preview", target.id()).with(bearer(sa)),
                        body(proposal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(preview, "$.data.granted"))
                .contains("PARTNERS:APPROVE", "PARTNERS:VIEW_PARTNER_SENSITIVE");
        assertThat(JsonPath.<List<String>>read(preview, "$.data.revoked")).contains("LEADS:READ", "INVOICES:WRITE");
        assertThat(JsonPath.<String>read(preview, "$.data.scopeChanges[0].moduleCode")).isEqualTo("PARTNERS");
        assertThat(JsonPath.<String>read(preview, "$.data.scopeChanges[0].to")).isEqualTo("SELF");
        assertThat(JsonPath.<List<String>>read(preview, "$.data.differsFromRole")).contains("PARTNERS", "LEADS",
                "PARTNERS:VIEW_PARTNER_SENSITIVE");

        // Dependencies are validated exactly as when saving.
        mvc.perform(withJson(post("/api/v1/users/{id}/permissions/preview", target.id()).with(bearer(sa)),
                        body(Map.of("modules", List.of(Map.of("moduleCode", "PARTNERS", "read", true, "delete", true),
                                Map.of("moduleCode", "ROLE_MANAGEMENT", "read", true)), "actions", List.of("MARK_PAYOUT_PAID")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(3));

        // Nothing was saved or audited.
        mvc.perform(get("/api/v1/users/{id}/permissions", target.id()).with(bearer(sa)))
                .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'LEADS')].read").value(true))
                .andExpect(jsonPath("$.data.differsFromRole.length()").value(0));
        assertThat(count("select count(*) from audit_logs where entity_id = ?", target.id())).isEqualTo(auditsBefore);

        // After saving, the overrides against the role default are reported.
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", target.id()).with(bearer(sa)), body(proposal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.differsFromRole").isNotEmpty());
    }

    @Test
    void roleDefaultsApplyToNewAccountsOnlyAndFollowTheRoleRules() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        UUID userRole = roleId(RoleCode.USER);
        List<Map<String, Object>> original = jdbc.queryForList("select module_id, can_write, can_delete, can_export, "
                + "can_approve, data_scope from role_permission_templates where role_id = ?", userRole);
        try {
            TestUser existing = createUser(RoleCode.USER);
            String current = mvc.perform(get("/api/v1/roles/{id}/permissions", userRole).with(bearer(sa)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.editable").value(true))
                    .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'PRODUCTS')].read").value(true))
                    .andReturn().getResponse().getContentAsString();
            long version = ((Number) JsonPath.read(current, "$.data.version")).longValue();

            // USER accounts can't hold User Management, by default or otherwise.
            mvc.perform(withJson(put("/api/v1/roles/{id}/permissions", userRole).with(bearer(sa)),
                            body(Map.of("modules", List.of(Map.of("moduleCode", "USER_MANAGEMENT", "read", true)),
                                    "version", version))))
                    .andExpect(status().isBadRequest());
            mvc.perform(withJson(put("/api/v1/roles/{id}/permissions", roleId(RoleCode.SUPER_ADMIN)).with(bearer(sa)),
                            body(Map.of("modules", List.of(), "version", 0))))
                    .andExpect(jsonPath("$.error.code").value("SUPER_ADMIN_PERMISSIONS_FIXED"));
            mvc.perform(withJson(put("/api/v1/roles/{id}/permissions", userRole).with(bearer(sa)),
                            body(Map.of("modules", List.of(), "version", version + 3))))
                    .andExpect(status().isConflict());

            mvc.perform(withJson(put("/api/v1/roles/{id}/permissions", userRole).with(bearer(sa)),
                            body(Map.of("modules", List.of(Map.of("moduleCode", "DASHBOARD", "read", true),
                                    Map.of("moduleCode", "LEADS", "read", true, "write", true, "dataScope", "SELF")),
                                    "version", version))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'LEADS')].dataScope").value("SELF"))
                    .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'PRODUCTS')].read").value(false))
                    .andExpect(jsonPath("$.data.version").value((int) version + 1));
            assertThat(jdbc.queryForObject("select new_values::text from audit_logs where action = 'ROLE_PERMISSIONS_CHANGED' "
                    + "and entity_id = ? order by created_at desc limit 1", String.class, userRole)).contains("LEADS");

            // The existing account is unchanged; a new account starts from the new default.
            mvc.perform(get("/api/v1/users/{id}/permissions", existing.id()).with(bearer(sa)))
                    .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'PRODUCTS')].read").value(true))
                    .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'LEADS')].read").value(false))
                    .andExpect(jsonPath("$.data.differsFromRole").isNotEmpty());
            TestUser fresh = createUser(RoleCode.USER);
            mvc.perform(get("/api/v1/users/{id}/permissions", fresh.id()).with(bearer(sa)))
                    .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'LEADS')].dataScope").value("SELF"))
                    .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'PRODUCTS')].read").value(false))
                    .andExpect(jsonPath("$.data.differsFromRole.length()").value(0));
        } finally {
            jdbc.update("delete from role_permission_templates where role_id = ?", userRole);
            for (Map<String, Object> row : original) {
                jdbc.update("insert into role_permission_templates (role_id, module_id, can_write, can_delete, can_export, "
                                + "can_approve, data_scope) values (?, ?, ?, ?, ?, ?, ?)", userRole, row.get("module_id"),
                        row.get("can_write"), row.get("can_delete"), row.get("can_export"), row.get("can_approve"),
                        row.get("data_scope"));
            }
        }
    }

    @Test
    void activityShowsTheAccountsOwnHistoryAndHidesIpAddressesFromNonAuditors() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser manager = createUser(RoleCode.ADMIN);
        grantUserManagement(sa, manager);
        TestUser target = createUser(RoleCode.USER);
        login(target);
        loginResult(target.email(), "Wrong-Password-99");

        String asSuperAdmin = mvc.perform(get("/api/v1/users/{id}/activity", target.id()).with(bearer(sa)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(asSuperAdmin, "$.data[*].action")).contains("LOGIN_SUCCEEDED", "LOGIN_FAILED");
        assertThat(JsonPath.<List<String>>read(asSuperAdmin, "$.data[*].entityId")).containsOnly(target.id().toString());

        String asManager = mvc.perform(get("/api/v1/users/{id}/activity", target.id()).with(bearer(login(manager))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(asManager, "$.data[*].ipAddress")).containsOnlyNulls();
        mvc.perform(get("/api/v1/users/{id}/activity", target.id()).with(bearer(login(createUser(RoleCode.USER)))))
                .andExpect(status().isForbidden());
    }

    /** User Management read and write plus Manage users (never Manage permissions, which is Super Admin only). */
    private void grantUserManagement(Session sa, TestUser target) throws Exception {
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", target.id()).with(bearer(sa)),
                        body(Map.of("modules", List.of(Map.of("moduleCode", "USER_MANAGEMENT", "read", true, "write", true)),
                                "actions", List.of("MANAGE_USERS")))))
                .andExpect(status().isOk());
    }
}
