package com.iotiq.partnererp.integration.security;

import com.iotiq.partnererp.config.BootstrapProperties;
import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.auth.service.PasswordPolicy;
import com.iotiq.partnererp.modules.auth.service.SuperAdminBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditAndBootstrapIT extends ApiIntegrationTest {

    private static final String BOOTSTRAP_PASSWORD = "Bootstrap-Fixture-9";

    @Autowired
    private AuditService auditService;

    @Autowired
    private PasswordPolicy passwordPolicy;

    @Test
    void secretLookingKeysAreRedactedBeforeStorage() {
        UUID entity = UUID.randomUUID();
        tx.executeWithoutResult(s -> auditService.recordAs(null, "TEST_EVENT", "TEST", entity,
                Map.of("password", "p1", "nested", Map.of("refreshToken", "t1", "kept", "visible")),
                Map.of("passwordHash", "h1", "clientSecret", "s1", "email", "a@b.c")));
        String stored = jdbc.queryForObject("select old_values::text || new_values::text from audit_logs "
                + "where entity_id = ?", String.class, entity);
        assertThat(stored).doesNotContain("p1", "t1", "h1", "s1").contains("[REDACTED]", "visible", "a@b.c");
    }

    @Test
    void auditTrailIsReadableOnlyWithAuditLogsRead() throws Exception {
        TestUser saUser = createUser(RoleCode.SUPER_ADMIN);
        Session sa = login(saUser);
        mvc.perform(get("/api/v1/audit-logs").with(bearer(sa)).param("action", "login_succeeded")
                        .param("entityId", saUser.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].actorEmail").value(saUser.email()))
                .andExpect(jsonPath("$.data[0].entityType").value("USER"));
        mvc.perform(get("/api/v1/audit-logs").with(bearer(login(createUser(RoleCode.ADMIN)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void bootstrapCreatesSuperAdminOnlyWhenNoneExists() {
        String email = "bootstrap-" + UUID.randomUUID() + "@test.iotiq.local";
        SuperAdminBootstrap bootstrap = new SuperAdminBootstrap(
                new BootstrapProperties(email, BOOTSTRAP_PASSWORD, "Owner"), userRepository, roleRepository,
                passwordEncoder, passwordPolicy, auditService);

        // With an existing Super Admin the variables are ignored.
        createUser(RoleCode.SUPER_ADMIN);
        bootstrap.run(new DefaultApplicationArguments());
        assertThat(count("select count(*) from users where email = ?", email)).isZero();

        // With none, exactly one is created; demonstrated in a transaction that is rolled back afterwards.
        tx.executeWithoutResult(status -> {
            jdbc.update("update users set role_id = ? where role_id = ?", roleId(RoleCode.ADMIN),
                    roleId(RoleCode.SUPER_ADMIN));
            bootstrap.run(new DefaultApplicationArguments());
            userRepository.flush();
            Map<String, Object> created = jdbc.queryForMap("select u.password_hash, u.must_change_password, "
                    + "u.full_name, r.code from users u join roles r on r.id = u.role_id where u.email = ?", email);
            assertThat(created.get("code")).isEqualTo("SUPER_ADMIN");
            assertThat(created.get("must_change_password")).isEqualTo(true);
            assertThat(created.get("full_name")).isEqualTo("Owner");
            assertThat((String) created.get("password_hash")).startsWith("$2").doesNotContain(BOOTSTRAP_PASSWORD);
            assertThat(passwordEncoder.matches(BOOTSTRAP_PASSWORD, (String) created.get("password_hash"))).isTrue();
            assertThat(jdbc.queryForObject("select coalesce(new_values::text, '') from audit_logs "
                    + "where action = 'SUPER_ADMIN_BOOTSTRAPPED' order by created_at desc limit 1", String.class))
                    .doesNotContain(BOOTSTRAP_PASSWORD);
            bootstrap.run(new DefaultApplicationArguments());
            assertThat(count("select count(*) from users where email = ?", email)).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(count("select count(*) from users where email = ?", email)).isZero();
    }

    @Test
    void bootstrapRefusesWeakPasswordsAndExistingAccounts() {
        TestUser existing = createUser(RoleCode.USER);
        tx.executeWithoutResult(status -> {
            jdbc.update("update users set role_id = ? where role_id = ?", roleId(RoleCode.ADMIN),
                    roleId(RoleCode.SUPER_ADMIN));
            assertThatThrownBy(() -> new SuperAdminBootstrap(new BootstrapProperties(
                    "weak-" + UUID.randomUUID() + "@test.iotiq.local", "short", null), userRepository, roleRepository,
                    passwordEncoder, passwordPolicy, auditService).run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("short");
            assertThatThrownBy(() -> new SuperAdminBootstrap(new BootstrapProperties(existing.email(),
                    BOOTSTRAP_PASSWORD, null), userRepository, roleRepository, passwordEncoder, passwordPolicy,
                    auditService).run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("select r.code from users u join roles r on r.id = u.role_id where u.id = ?",
                    String.class, existing.id())).isEqualTo("USER");
            status.setRollbackOnly();
        });
    }
}
