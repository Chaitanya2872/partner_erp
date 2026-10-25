package com.iotiq.partnererp.integration.security;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.auth.service.DataScopeService;
import com.iotiq.partnererp.modules.auth.service.PrincipalService;
import com.iotiq.partnererp.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PermissionIT extends ApiIntegrationTest {

    @Autowired
    private PrincipalService principalService;

    @Autowired
    private DataScopeService dataScopeService;

    @Autowired
    private UserRepository users;

    @Test
    void userTemplateGrantsDashboardAndProductsReadOnly() throws Exception {
        Session session = login(createUser(RoleCode.USER));
        mvc.perform(get("/api/v1/auth/me").with(bearer(session)))
                .andExpect(jsonPath("$.data.authorities").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "DASHBOARD:READ", "PRODUCTS:READ")))
                .andExpect(jsonPath("$.data.modules.length()").value(2))
                .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'PRODUCTS')].write").value(false))
                .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'LEADS')]").isEmpty());
        mvc.perform(get("/api/v1/products").with(bearer(session))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/audit-logs").with(bearer(session))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/modules").with(bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(14));
    }

    @Test
    void readOnlyReadWriteAndNoAccessAreEnforcedPerUser() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser admin = createUser(RoleCode.ADMIN);
        Session adminSession = login(admin);
        String newUser = body(Map.of("fullName", "Probe", "email", "probe-" + admin.id() + "@test.iotiq.local",
                "roleId", roleId(RoleCode.USER), "temporaryPassword", "Temporary-Pass-11"));

        // No access.
        mvc.perform(get("/api/v1/users").with(bearer(adminSession))).andExpect(status().isForbidden());

        // Read only: listing works, changes are refused. The change applies to the existing session at once.
        replace(sa, admin, List.of(Map.of("moduleCode", "USER_MANAGEMENT", "read", true)), List.of());
        mvc.perform(get("/api/v1/users").with(bearer(adminSession))).andExpect(status().isOk());
        mvc.perform(withJson(post("/api/v1/users").with(bearer(adminSession)), newUser)).andExpect(status().isForbidden());

        // Read + write.
        replace(sa, admin, List.of(Map.of("moduleCode", "USER_MANAGEMENT", "read", true, "write", true)), List.of());
        mvc.perform(withJson(post("/api/v1/users").with(bearer(adminSession)), newUser)).andExpect(status().isCreated());

        // Write does not imply sensitive actions.
        mvc.perform(get("/api/v1/auth/me").with(bearer(adminSession)))
                .andExpect(jsonPath("$.data.authorities").value(org.hamcrest.Matchers.hasItems(
                        "USER_MANAGEMENT:READ", "USER_MANAGEMENT:WRITE")))
                .andExpect(jsonPath("$.data.authorities").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("USER_MANAGEMENT:MANAGE_USERS"))));

        // Removing the module removes access, including products from the original template.
        replace(sa, admin, List.of(), List.of());
        mvc.perform(get("/api/v1/users").with(bearer(adminSession))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/products").with(bearer(adminSession))).andExpect(status().isForbidden());
    }

    @Test
    void changesArePersistedAuditedAndReturned() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser admin = createUser(RoleCode.ADMIN);
        replace(sa, admin, List.of(
                Map.of("moduleCode", "LEADS", "read", true, "write", true, "delete", true, "export", true,
                        "approve", true, "dataScope", "SELF"),
                Map.of("moduleCode", "PAYOUTS", "read", true, "write", true),
                Map.of("moduleCode", "PRODUCTS", "read", true)), List.of("MARK_PAYOUT_PAID", "VIEW_INTERNAL_B2C"));

        mvc.perform(get("/api/v1/users/{id}/permissions", admin.id()).with(bearer(sa)))
                .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'LEADS')].dataScope").value("SELF"))
                .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'LEADS')].delete").value(true))
                .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'PARTNERS')].read").value(false))
                .andExpect(jsonPath("$.data.actions").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "MARK_PAYOUT_PAID", "VIEW_INTERNAL_B2C")));
        assertThat(count("select count(*) from user_module_permissions where user_id = ?", admin.id())).isEqualTo(3);
        assertThat(count("select count(*) from user_action_permissions where user_id = ?", admin.id())).isEqualTo(2);
        assertThat(count("select count(*) from audit_logs where action = 'PERMISSIONS_CHANGED' and entity_id = ? "
                + "and (old_values -> 'modules' -> 'PARTNERS') is not null and new_values -> 'modules' -> 'LEADS' ->> 'scope' = 'SELF'",
                admin.id())).isEqualTo(1);

        Session adminSession = login(admin);
        mvc.perform(get("/api/v1/auth/me").with(bearer(adminSession)))
                .andExpect(jsonPath("$.data.authorities").value(org.hamcrest.Matchers.hasItems(
                        "LEADS:DELETE", "LEADS:APPROVE", "PAYOUTS:MARK_PAYOUT_PAID", "PRODUCTS:VIEW_INTERNAL_B2C")));
    }

    @Test
    void invalidAndEscalatingGrantsAreRejected() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser user = createUser(RoleCode.USER);
        TestUser admin = createUser(RoleCode.ADMIN);
        List<Object[]> invalid = List.of(
                new Object[]{user, List.of(Map.of("moduleCode", "LEADS", "read", true, "delete", true)), List.of()},
                new Object[]{user, List.of(Map.of("moduleCode", "LEADS", "read", false, "write", true)), List.of()},
                new Object[]{user, List.of(Map.of("moduleCode", "USER_MANAGEMENT", "read", true)), List.of()},
                new Object[]{admin, List.of(Map.of("moduleCode", "ROLE_MANAGEMENT", "read", true)), List.of()},
                new Object[]{admin, List.of(Map.of("moduleCode", "USER_MANAGEMENT", "read", true)),
                        List.of("MANAGE_PERMISSIONS")},
                new Object[]{admin, List.of(), List.of("MARK_PAYOUT_PAID")},
                new Object[]{admin, List.of(Map.of("moduleCode", "DASHBOARD", "read", true, "write", true)), List.of()},
                new Object[]{admin, List.of(Map.of("moduleCode", "PRODUCTS", "read", true, "dataScope", "TEAM")), List.of()},
                new Object[]{admin, List.of(Map.of("moduleCode", "NOT_A_MODULE", "read", true)), List.of()},
                new Object[]{admin, List.of(), List.of("DELETE")});
        for (Object[] c : invalid) {
            TestUser target = (TestUser) c[0];
            mvc.perform(withJson(put("/api/v1/users/{id}/permissions", target.id()).with(bearer(sa)),
                            body(Map.of("modules", c[1], "actions", c[2]))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        assertThat(count("select count(*) from user_module_permissions where user_id = ?", admin.id())).isEqualTo(9);
        assertThat(count("select count(*) from user_module_permissions where user_id = ?", user.id())).isEqualTo(2);
    }

    @Test
    void superAdminPermissionsAreFixedAndSelfChangesRefused() throws Exception {
        TestUser saUser = createUser(RoleCode.SUPER_ADMIN);
        Session sa = login(saUser);
        TestUser otherSa = createUser(RoleCode.SUPER_ADMIN);
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", saUser.id()).with(bearer(sa)),
                        body(Map.of("modules", List.of(), "actions", List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SELF_PERMISSION_CHANGE"));
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", otherSa.id()).with(bearer(sa)),
                        body(Map.of("modules", List.of(), "actions", List.of()))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("SUPER_ADMIN_PERMISSIONS_FIXED"));
        mvc.perform(get("/api/v1/users/{id}/permissions", otherSa.id()).with(bearer(sa)))
                .andExpect(jsonPath("$.data.superAdmin").value(true))
                .andExpect(jsonPath("$.data.editable").value(false));
        // Super Admin access is computed, not stored.
        assertThat(count("select count(*) from user_module_permissions where user_id = ?", saUser.id())).isZero();
        mvc.perform(get("/api/v1/audit-logs").with(bearer(sa))).andExpect(status().isOk());
    }

    @Test
    void selfAndTeamScopesDenyRecordsUntilOwnershipExists() {
        TestUser sa = createUser(RoleCode.SUPER_ADMIN);
        TestUser admin = createUser(RoleCode.ADMIN);
        jdbc.update("update user_module_permissions set data_scope = 'SELF' where user_id = ? and module_id = "
                + "(select id from modules where code = 'LEADS')", admin.id());
        jdbc.update("update user_module_permissions set data_scope = 'TEAM' where user_id = ? and module_id = "
                + "(select id from modules where code = 'PARTNERS')", admin.id());
        AuthenticatedUser adminPrincipal = principal(admin);
        assertThat(dataScopeService.resolve(adminPrincipal, ModuleCode.LEADS).unrestricted()).isFalse();
        assertThat(dataScopeService.resolve(adminPrincipal, ModuleCode.PARTNERS).unrestricted()).isFalse();
        assertThat(dataScopeService.resolve(adminPrincipal, ModuleCode.ESTIMATES).unrestricted()).isTrue();
        assertThat(dataScopeService.resolve(principal(sa), ModuleCode.LEADS).unrestricted()).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> dataScopeService.resolve(adminPrincipal, ModuleCode.INTERNAL_B2C))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private AuthenticatedUser principal(TestUser user) {
        return tx.execute(s -> principalService.toPrincipal(users.findWithRoleById(user.id()).orElseThrow()));
    }

    private void replace(Session sa, TestUser target, List<Map<String, Object>> modules, List<String> actions)
            throws Exception {
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", target.id()).with(bearer(sa)),
                        body(Map.of("modules", modules, "actions", actions))))
                .andExpect(status().isOk());
    }
}
