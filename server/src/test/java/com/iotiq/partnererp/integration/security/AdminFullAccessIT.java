package com.iotiq.partnererp.integration.security;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.auth.service.ModuleCatalog;
import com.iotiq.partnererp.modules.auth.service.PermissionService;
import com.iotiq.partnererp.modules.auth.service.PrincipalService;
import com.iotiq.partnererp.security.AuthenticatedUser;
import com.iotiq.partnererp.security.EffectivePermissions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V23: the Admin role has the same access as Super Admin; Super Admin accounts stay protected. */
class AdminFullAccessIT extends ApiIntegrationTest {

    @Autowired
    private PrincipalService principalService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private ModuleCatalog catalog;

    @Autowired
    private UserRepository users;

    private String createdSku;

    @org.junit.jupiter.api.AfterEach
    void removeCreatedProduct() {
        if (createdSku != null) {
            jdbc.update("delete from products where sku = ?", createdSku);
        }
    }

    @Test
    void adminTemplateCoversEveryModuleAndAdminResolvesToFullAccess() {
        assertThat(count("select count(*) from role_permission_templates t join roles r on r.id = t.role_id "
                + "where r.code = 'ADMIN'")).isEqualTo(14);
        assertThat(catalog.modules()).hasSize(14);

        TestUser admin = createUserFromTemplate(RoleCode.ADMIN);
        AuthenticatedUser principal = tx.execute(s -> principalService.toPrincipal(users.findWithRoleById(admin.id()).orElseThrow()));
        EffectivePermissions effective = principal.permissions();
        EffectivePermissions full = permissionService.superAdminPermissions();

        assertThat(effective.superAdmin()).isFalse();
        assertThat(effective.modules()).containsOnlyKeys(full.modules().keySet());
        full.modules().forEach((code, expected) -> {
            var actual = effective.modules().get(code);
            assertThat(actual.write()).as(code + " write").isEqualTo(expected.write());
            assertThat(actual.delete()).as(code + " delete").isEqualTo(expected.delete());
            assertThat(actual.export()).as(code + " export").isEqualTo(expected.export());
            assertThat(actual.approve()).as(code + " approve").isEqualTo(expected.approve());
            assertThat(actual.scope()).as(code + " scope").isEqualTo(expected.scope());
        });
        assertThat(effective.actionCodes()).containsExactlyInAnyOrderElementsOf(full.actionCodes());
        assertThat(effective.actionCodes()).contains("USER_MANAGEMENT:MANAGE_PERMISSIONS", "PRODUCTS:VIEW_INTERNAL_B2C");
        assertThat(effective.canWrite("ROLE_MANAGEMENT")).isTrue();
    }

    @Test
    void adminCanChangeOthersPermissionsButNotItsOwnNorASuperAdmins() throws Exception {
        TestUser admin = createUserFromTemplate(RoleCode.ADMIN);
        TestUser otherAdmin = createUserFromTemplate(RoleCode.ADMIN);
        TestUser user = createUser(RoleCode.USER);
        TestUser superAdmin = createUser(RoleCode.SUPER_ADMIN);
        Session session = login(admin);
        Map<String, Object> grant = Map.of(
                "modules", List.of(Map.of("moduleCode", "ROLE_MANAGEMENT", "read", true, "write", true)),
                "actions", List.of());

        mvc.perform(get("/api/v1/users/{id}/permissions", user.id()).with(bearer(session)))
                .andExpect(jsonPath("$.data.editable").value(true));
        mvc.perform(withJson(post("/api/v1/users/{id}/permissions/preview", otherAdmin.id()).with(bearer(session)), body(grant)))
                .andExpect(status().isOk());
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", otherAdmin.id()).with(bearer(session)), body(grant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modules[?(@.moduleCode == 'ROLE_MANAGEMENT')].write").value(true));
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", user.id()).with(bearer(session)), body(Map.of(
                        "modules", List.of(Map.of("moduleCode", "LEADS", "read", true, "write", true)), "actions", List.of()))))
                .andExpect(status().isOk());
        // A USER still cannot hold User Management.
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", user.id()).with(bearer(session)), body(Map.of(
                        "modules", List.of(Map.of("moduleCode", "USER_MANAGEMENT", "read", true)), "actions", List.of()))))
                .andExpect(status().isBadRequest());

        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", admin.id()).with(bearer(session)), body(grant)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SELF_PERMISSION_CHANGE"));
        mvc.perform(get("/api/v1/users/{id}/permissions", admin.id()).with(bearer(session)))
                .andExpect(jsonPath("$.data.editable").value(false));
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", superAdmin.id()).with(bearer(session)), body(grant)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("SUPER_ADMIN_PERMISSIONS_FIXED"));
        mvc.perform(get("/api/v1/users/{id}/permissions", superAdmin.id()).with(bearer(session)))
                .andExpect(jsonPath("$.data.editable").value(false));
    }

    @Test
    void adminCannotEditDeactivateResetOrDemoteASuperAdminAccount() throws Exception {
        TestUser admin = createUserFromTemplate(RoleCode.ADMIN);
        TestUser superAdmin = createUser(RoleCode.SUPER_ADMIN);
        Session session = login(admin);
        long version = userVersion(superAdmin.id());

        mvc.perform(withJson(put("/api/v1/users/{id}", superAdmin.id()).with(bearer(session)), body(Map.of(
                        "fullName", "Hijacked", "email", superAdmin.email(), "roleId", roleId(RoleCode.SUPER_ADMIN),
                        "version", version)))).andExpect(status().isForbidden());
        mvc.perform(withJson(put("/api/v1/users/{id}", superAdmin.id()).with(bearer(session)), body(Map.of(
                        "fullName", "Demoted", "email", superAdmin.email(), "roleId", roleId(RoleCode.USER),
                        "version", version)))).andExpect(status().isForbidden());
        mvc.perform(withJson(patch("/api/v1/users/{id}/status", superAdmin.id()).with(bearer(session)),
                        body(Map.of("status", "DISABLED", "version", version)))).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/v1/users/{id}/password-reset", superAdmin.id()).with(bearer(session)),
                        body(Map.of("temporaryPassword", "Temporary-Pass-11", "version", version))))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/v1/users").with(bearer(session)), body(Map.of(
                        "fullName", "New SA", "email", "sa-" + UUID.randomUUID() + "@test.iotiq.local",
                        "roleId", roleId(RoleCode.SUPER_ADMIN), "temporaryPassword", "Temporary-Pass-11"))))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select status from users where id = ?", String.class, superAdmin.id()))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select full_name from users where id = ?", String.class, superAdmin.id()))
                .isEqualTo("Test SUPER_ADMIN");
    }

    @Test
    void adminCanCreateProductsAndManageRoles() throws Exception {
        TestUser admin = createUserFromTemplate(RoleCode.ADMIN);
        Session session = login(admin);
        mvc.perform(get("/api/v1/auth/me").with(bearer(session)))
                .andExpect(jsonPath("$.data.authorities").value(org.hamcrest.Matchers.hasItems(
                        "PRODUCTS:WRITE", "PRODUCTS:DELETE", "ROLE_MANAGEMENT:READ", "ROLE_MANAGEMENT:WRITE",
                        "USER_MANAGEMENT:MANAGE_PERMISSIONS")));
        String sku = "ADM-" + UUID.randomUUID().toString().substring(0, 8);
        createdSku = sku;
        mvc.perform(withJson(post("/api/v1/products").with(bearer(session)), body(Map.of(
                        "sku", sku, "category", "SMART_GLIDE", "hsn", "8536 5090", "partnerName", "Admin product",
                        "partnerPrice", 1000, "gstPercent", 18, "b2cName", "Admin B2C", "b2cPrice", 1500,
                        "costPrice", 700))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/roles").with(bearer(session))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/roles/{id}/permissions", roleId(RoleCode.USER)).with(bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editable").value(true));
        mvc.perform(get("/api/v1/roles/{id}/permissions", roleId(RoleCode.SUPER_ADMIN)).with(bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editable").value(false));
        mvc.perform(get("/api/v1/modules").with(bearer(session)))
                .andExpect(jsonPath("$.data[?(@.code == 'ROLE_MANAGEMENT')].superAdminOnly").value(false));
    }
}
