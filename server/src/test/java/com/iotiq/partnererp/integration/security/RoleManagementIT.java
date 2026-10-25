package com.iotiq.partnererp.integration.security;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleManagementIT extends ApiIntegrationTest {

    @AfterEach
    void restoreDisplayNames() {
        jdbc.update("update roles set display_name = case code when 'SUPER_ADMIN' then 'Super Admin' "
                + "when 'ADMIN' then 'Admin' when 'USER' then 'User' end, description = null where active");
    }

    @Test
    void exactlyThreeRolesAreListedWithUserCounts() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        mvc.perform(get("/api/v1/roles").with(bearer(sa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].code").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.data[1].code").value("ADMIN"))
                .andExpect(jsonPath("$.data[2].code").value("USER"))
                .andExpect(jsonPath("$.data[2].displayName").value("User"))
                .andExpect(jsonPath("$.data[0].userCount").isNumber());
    }

    @Test
    void superAdminRenamesRoleAndChangeIsAuditedAndVisible() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser member = createUser(RoleCode.ADMIN);
        String before = permissionsOf(member, sa);
        UUID adminRole = roleId(RoleCode.ADMIN);

        mvc.perform(withJson(patch("/api/v1/roles/{id}", adminRole).with(bearer(sa)),
                        body(Map.of("displayName", "  Operations   Manager ", "description", "Runs daily operations",
                                "version", roleVersion(adminRole)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("ADMIN"))
                .andExpect(jsonPath("$.data.displayName").value("Operations Manager"));

        // Renaming changes the label only: assignment, permissions and the code-based authority are unchanged.
        Session memberSession = login(member);
        mvc.perform(get("/api/v1/auth/me").with(bearer(memberSession)))
                .andExpect(jsonPath("$.data.role.code").value("ADMIN"))
                .andExpect(jsonPath("$.data.role.displayName").value("Operations Manager"));
        assertThat(permissionsOf(member, sa)).isEqualTo(before.replace("\"Admin\"", "\"Operations Manager\""));
        mvc.perform(get("/api/v1/products").with(bearer(memberSession))).andExpect(status().isOk());

        assertThat(count("select count(*) from audit_logs where action = 'ROLE_UPDATED' and entity_id = ? "
                + "and old_values ->> 'displayName' = 'Admin' and new_values ->> 'displayName' = 'Operations Manager'",
                adminRole)).isEqualTo(1);
    }

    @Test
    void adminAndUserCannotRenameRoles() throws Exception {
        UUID userRole = roleId(RoleCode.USER);
        Map<String, Object> rename = Map.of("displayName", "Members", "version", roleVersion(userRole));
        for (RoleCode role : new RoleCode[]{RoleCode.ADMIN, RoleCode.USER}) {
            Session session = login(createUser(role));
            mvc.perform(withJson(patch("/api/v1/roles/{id}", userRole).with(bearer(session)), body(rename)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/roles").with(bearer(login(createUser(RoleCode.USER))))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select display_name from roles where id = ?", String.class, userRole))
                .isEqualTo("User");
    }

    @Test
    void codeIsImmutableAndNamesAreValidated() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        UUID adminRole = roleId(RoleCode.ADMIN);
        long version = roleVersion(adminRole);

        Map<String, Object> codeChange = new HashMap<>(Map.of("displayName", "Admin", "code", "OWNER", "version", version));
        mvc.perform(withJson(patch("/api/v1/roles/{id}", adminRole).with(bearer(sa)), body(codeChange)))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(patch("/api/v1/roles/{id}", adminRole).with(bearer(sa)),
                        body(Map.of("displayName", "   ", "version", version))))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(patch("/api/v1/roles/{id}", adminRole).with(bearer(sa)),
                        body(Map.of("displayName", " super ADMIN ", "version", version))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_ROLE_NAME"));
        mvc.perform(withJson(patch("/api/v1/roles/{id}", adminRole).with(bearer(sa)),
                        body(Map.of("displayName", "Managers", "version", version - 1))))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select code || '/' || display_name from roles where id = ?", String.class, adminRole))
                .isEqualTo("ADMIN/Admin");
        // The database refuses code changes even outside the API.
        assertThatThrownBy(() -> jdbc.update("update roles set code = 'OWNER' where id = ?", adminRole))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long roleVersion(UUID id) {
        return count("select version from roles where id = ?", id);
    }

    private String permissionsOf(TestUser user, Session sa) throws Exception {
        return mvc.perform(get("/api/v1/users/{id}/permissions", user.id()).with(bearer(sa)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                .replaceAll("\"timestamp\":\"[^\"]+\"", "");
    }
}
