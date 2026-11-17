package com.iotiq.partnererp.integration.security;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.auth.entity.UserStatus;
import com.iotiq.partnererp.modules.users.dto.UpdateUserStatusRequest;
import com.iotiq.partnererp.modules.users.service.UserService;
import com.iotiq.partnererp.security.AuthenticatedUser;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserManagementIT extends ApiIntegrationTest {

    @Autowired
    private UserService userService;

    @Test
    void superAdminCreatesUserWithRoleTemplateAndTemporaryPassword() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        String email = "new-admin-" + UUID.randomUUID() + "@test.iotiq.local";
        String response = mvc.perform(withJson(post("/api/v1/users").with(bearer(sa)), body(Map.of(
                        "fullName", "New Admin", "email", email.toUpperCase(), "roleId", roleId(RoleCode.ADMIN),
                        "temporaryPassword", "Temporary-Pass-11"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.role.code").value("ADMIN"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(response, "$.data.id"));

        mvc.perform(get("/api/v1/users/{id}/permissions", id).with(bearer(sa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editable").value(true))
                .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'PARTNERS')].write").value(true))
                .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'PAYOUTS')].write").value(true))
                .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'PAYOUTS')].read").value(true))
                // V23: a new Admin starts with full access (every module and every module action).
                .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'INTERNAL_B2C')].read").value(true))
                .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'ROLE_MANAGEMENT')].write").value(true))
                .andExpect(jsonPath("$.data.actions.length()").value(
                        count("select count(*) from permissions where active and kind = 'MODULE'").intValue()));
        assertThat(count("select count(*) from audit_logs where action = 'USER_CREATED' and entity_id = ?", id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select new_values::text from audit_logs where action = 'USER_CREATED' "
                + "and entity_id = ?", String.class, id)).doesNotContain("Temporary-Pass-11");

        mvc.perform(withJson(post("/api/v1/users").with(bearer(sa)), body(Map.of(
                        "fullName", "Duplicate", "email", email, "roleId", roleId(RoleCode.USER),
                        "temporaryPassword", "Temporary-Pass-11"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_IN_USE"));
        mvc.perform(withJson(post("/api/v1/users").with(bearer(sa)), body(Map.of(
                        "fullName", "Weak", "email", "weak-" + email, "roleId", roleId(RoleCode.USER),
                        "temporaryPassword", "password"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchFiltersByRoleStatusAndText() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser target = createUser(RoleCode.USER);
        mvc.perform(get("/api/v1/users").with(bearer(sa)).param("q", target.email().substring(0, 20))
                        .param("roleId", roleId(RoleCode.USER).toString()).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(target.id().toString()))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
        mvc.perform(get("/api/v1/users").with(bearer(sa)).param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultAdminAndUserCannotManageUsers() throws Exception {
        TestUser victim = createUser(RoleCode.USER);
        for (RoleCode role : new RoleCode[]{RoleCode.ADMIN, RoleCode.USER}) {
            Session session = login(createUser(role));
            mvc.perform(get("/api/v1/users").with(bearer(session))).andExpect(status().isForbidden());
            mvc.perform(withJson(post("/api/v1/users").with(bearer(session)), body(Map.of(
                            "fullName", "X", "email", "x-" + UUID.randomUUID() + "@test.iotiq.local",
                            "roleId", roleId(RoleCode.USER), "temporaryPassword", "Temporary-Pass-11"))))
                    .andExpect(status().isForbidden());
            mvc.perform(withJson(patch("/api/v1/users/{id}/status", victim.id()).with(bearer(session)),
                            body(Map.of("status", "DISABLED", "version", userVersion(victim.id())))))
                    .andExpect(status().isForbidden());
            mvc.perform(withJson(put("/api/v1/users/{id}/permissions", victim.id()).with(bearer(session)),
                            body(Map.of("modules", List.of(), "actions", List.of()))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void delegatedAdminCannotManageSuperAdminsOrEscalateToSuperAdmin() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser admin = createUser(RoleCode.ADMIN);
        TestUser otherAdmin = createUser(RoleCode.ADMIN);
        TestUser superAdmin = createUser(RoleCode.SUPER_ADMIN);
        TestUser user = createUser(RoleCode.USER);
        grant(sa, admin, List.of(
                Map.of("moduleCode", "DASHBOARD", "read", true),
                Map.of("moduleCode", "USER_MANAGEMENT", "read", true, "write", true)), List.of("MANAGE_USERS"));
        Session adminSession = login(admin);

        // Allowed: create and edit USER accounts, activate/deactivate them.
        mvc.perform(withJson(post("/api/v1/users").with(bearer(adminSession)), body(Map.of(
                        "fullName", "Created By Admin", "email", "by-admin-" + UUID.randomUUID() + "@test.iotiq.local",
                        "roleId", roleId(RoleCode.USER), "temporaryPassword", "Temporary-Pass-11"))))
                .andExpect(status().isCreated());
        mvc.perform(withJson(patch("/api/v1/users/{id}/status", user.id()).with(bearer(adminSession)),
                        body(Map.of("status", "DISABLED", "version", userVersion(user.id())))))
                .andExpect(status().isOk());

        // Allowed since V23 (Admin has Super Admin's access, apart from Super Admin accounts): creating Admins,
        // promoting a user to Admin, managing other Admins.
        mvc.perform(withJson(post("/api/v1/users").with(bearer(adminSession)), body(Map.of(
                        "fullName", "Another Admin", "email", "adm-" + UUID.randomUUID() + "@test.iotiq.local",
                        "roleId", roleId(RoleCode.ADMIN), "temporaryPassword", "Temporary-Pass-11"))))
                .andExpect(status().isCreated());
        mvc.perform(withJson(put("/api/v1/users/{id}", otherAdmin.id()).with(bearer(adminSession)), body(Map.of(
                        "fullName", "Renamed Admin", "email", otherAdmin.email(), "roleId", roleId(RoleCode.ADMIN),
                        "version", userVersion(otherAdmin.id())))))
                .andExpect(status().isOk());

        // Denied: creating or promoting to Super Admin, and touching Super Admin accounts or one's own role.
        mvc.perform(withJson(post("/api/v1/users").with(bearer(adminSession)), body(Map.of(
                        "fullName", "Escalation", "email", "esc-" + UUID.randomUUID() + "@test.iotiq.local",
                        "roleId", roleId(RoleCode.SUPER_ADMIN), "temporaryPassword", "Temporary-Pass-11"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_OPERATION"));
        mvc.perform(withJson(put("/api/v1/users/{id}", user.id()).with(bearer(adminSession)), body(Map.of(
                        "fullName", "Promoted", "email", user.email(), "roleId", roleId(RoleCode.SUPER_ADMIN),
                        "version", userVersion(user.id())))))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(put("/api/v1/users/{id}", superAdmin.id()).with(bearer(adminSession)), body(Map.of(
                        "fullName", "Hijacked", "email", superAdmin.email(), "roleId", roleId(RoleCode.SUPER_ADMIN),
                        "version", userVersion(superAdmin.id())))))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(put("/api/v1/users/{id}", superAdmin.id()).with(bearer(adminSession)), body(Map.of(
                        "fullName", "Demoted", "email", superAdmin.email(), "roleId", roleId(RoleCode.USER),
                        "version", userVersion(superAdmin.id())))))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(put("/api/v1/users/{id}", admin.id()).with(bearer(adminSession)), body(Map.of(
                        "fullName", "Self Promoted", "email", admin.email(), "roleId", roleId(RoleCode.SUPER_ADMIN),
                        "version", userVersion(admin.id())))))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(patch("/api/v1/users/{id}/status", superAdmin.id()).with(bearer(adminSession)),
                        body(Map.of("status", "DISABLED", "version", userVersion(superAdmin.id())))))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(patch("/api/v1/users/{id}/status", otherAdmin.id()).with(bearer(adminSession)),
                        body(Map.of("status", "DISABLED", "version", userVersion(otherAdmin.id())))))
                .andExpect(status().isOk());
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", admin.id()).with(bearer(adminSession)),
                        body(Map.of("modules", List.of(Map.of("moduleCode", "INTERNAL_B2C", "read", true)),
                                "actions", List.of()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/roles").with(bearer(adminSession))).andExpect(status().isOk());
        mvc.perform(withJson(patch("/api/v1/roles/{id}", roleId(RoleCode.ADMIN)).with(bearer(adminSession)),
                        body(Map.of("displayName", "Boss", "version", 0)))).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select r.code from users u join roles r on r.id = u.role_id where u.id = ?",
                String.class, admin.id())).isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("select status from users where id = ?", String.class, superAdmin.id()))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select r.code from users u join roles r on r.id = u.role_id where u.id = ?",
                String.class, superAdmin.id())).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void superAdminCannotDemoteOrDisableThemselves() throws Exception {
        TestUser saUser = createUser(RoleCode.SUPER_ADMIN);
        Session sa = login(saUser);
        mvc.perform(withJson(patch("/api/v1/users/{id}/status", saUser.id()).with(bearer(sa)),
                        body(Map.of("status", "DISABLED", "version", userVersion(saUser.id())))))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(put("/api/v1/users/{id}", saUser.id()).with(bearer(sa)), body(Map.of(
                        "fullName", "Me", "email", saUser.email(), "roleId", roleId(RoleCode.USER),
                        "version", userVersion(saUser.id())))))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleChangeResetsPermissionsToTheNewTemplateAndIsAudited() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser user = createUser(RoleCode.USER);
        mvc.perform(withJson(put("/api/v1/users/{id}", user.id()).with(bearer(sa)), body(Map.of(
                        "fullName", "Now Admin", "email", user.email(), "roleId", roleId(RoleCode.ADMIN),
                        "version", userVersion(user.id())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role.code").value("ADMIN"));
        assertThat(count("select count(*) from user_module_permissions where user_id = ?", user.id())).isEqualTo(14);
        assertThat(count("select count(*) from audit_logs where action = 'USER_ROLE_CHANGED' and entity_id = ? "
                + "and old_values ->> 'role' = 'USER' and new_values ->> 'role' = 'ADMIN'", user.id())).isEqualTo(1);

        TestUser secondSa = createUser(RoleCode.SUPER_ADMIN);
        mvc.perform(withJson(put("/api/v1/users/{id}", secondSa.id()).with(bearer(sa)), body(Map.of(
                        "fullName", "Former SA", "email", secondSa.email(), "roleId", roleId(RoleCode.ADMIN),
                        "version", userVersion(secondSa.id())))))
                .andExpect(status().isOk());
    }

    @Test
    void lastActiveSuperAdminIsProtected() {
        TestUser onlySa = createUser(RoleCode.SUPER_ADMIN);
        jdbc.update("update users set status = 'DISABLED' where id <> ? and role_id = ?", onlySa.id(),
                roleId(RoleCode.SUPER_ADMIN));
        // An actor that is not itself an active account in the database (e.g. a session ended concurrently).
        AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), "ghost@test.iotiq.local", "Ghost",
                roleId(RoleCode.SUPER_ADMIN), "SUPER_ADMIN", "Super Admin", false,
                permissionService.superAdminPermissions());
        assertThatThrownBy(() -> userService.updateStatus(onlySa.id(),
                new UpdateUserStatusRequest(UserStatus.DISABLED, userVersion(onlySa.id())), actor))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAST_SUPER_ADMIN"));
        assertThat(jdbc.queryForObject("select status from users where id = ?", String.class, onlySa.id()))
                .isEqualTo("ACTIVE");
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser user = createUser(RoleCode.USER);
        mvc.perform(withJson(patch("/api/v1/users/{id}/status", user.id()).with(bearer(sa)),
                        body(Map.of("status", "DISABLED", "version", userVersion(user.id()) + 5))))
                .andExpect(status().isConflict());
    }

    private void grant(Session sa, TestUser target, List<Map<String, Object>> modules, List<String> actions)
            throws Exception {
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", target.id()).with(bearer(sa)),
                        body(Map.of("modules", modules, "actions", actions))))
                .andExpect(status().isOk());
    }
}
