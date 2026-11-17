package com.iotiq.partnererp.integration.support;

import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.repository.RoleRepository;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.auth.service.PermissionService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for HTTP-level tests: the real security filter chain over MockMvc, against partner_erp_test.
 * Every test creates its own accounts (unique emails), so tests do not depend on each other's data.
 */
public abstract class ApiIntegrationTest extends AbstractIntegrationTest {

    /** Test-only fixture password; it exists only in this source file and the throwaway test database. */
    protected static final String PASSWORD = "Fixture-Pass-2024";
    private static String passwordHash;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected PermissionService permissionService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected TransactionTemplate tx;

    @Autowired
    protected ObjectMapper json;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
    }

    protected TestUser createUser(RoleCode role) {
        return createUser(role, false);
    }

    /**
     * An ACTIVE account. USER and SUPER_ADMIN get their role's default permissions; ADMIN gets the deliberately
     * limited pre-V23 matrix ({@link #LEGACY_ADMIN_MODULES}, no special actions) so the many tests that need a
     * "partial" account keep working. Since V23 a real Admin has full access: use {@link #createUserFromTemplate}.
     */
    protected TestUser createUser(RoleCode role, boolean mustChangePassword) {
        TestUser created = createUserFromTemplate(role, mustChangePassword);
        if (role == RoleCode.ADMIN) {
            jdbc.update("delete from user_action_permissions where user_id = ?", created.id());
            jdbc.update("delete from user_module_permissions where user_id = ?", created.id());
            LEGACY_ADMIN_MODULES.forEach((module, write) -> jdbc.update("""
                    insert into user_module_permissions (user_id, module_id, can_write)
                    select ?, id, ? from modules where code = ?""", created.id(), write, module));
        }
        return created;
    }

    /** Pre-V23 Admin matrix: module -> write. */
    protected static final java.util.Map<String, Boolean> LEGACY_ADMIN_MODULES = java.util.Map.of(
            "DASHBOARD", false, "PARTNERS", true, "LEADS", true, "PRODUCTS", true, "ESTIMATES", true,
            "INVOICES", true, "PAYMENTS", true, "PAYOUTS", false, "REPORTS", false);

    /** An ACTIVE account with exactly the role's default permission template (and template actions). */
    protected TestUser createUserFromTemplate(RoleCode role) {
        return createUserFromTemplate(role, false);
    }

    protected TestUser createUserFromTemplate(RoleCode role, boolean mustChangePassword) {
        String email = role.name().toLowerCase().replace('_', '-') + "-" + UUID.randomUUID() + "@test.iotiq.local";
        UUID id = tx.execute(status -> {
            User user = new User();
            user.setFullName("Test " + role.name());
            user.setEmail(email);
            user.setPasswordHash(passwordHash);
            user.setRole(roleRepository.findByCode(role.name()).orElseThrow());
            user.setMustChangePassword(mustChangePassword);
            userRepository.saveAndFlush(user);
            permissionService.applyRoleTemplate(user, null);
            return user.getId();
        });
        return new TestUser(id, email);
    }

    protected MvcResult loginResult(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Credentials(email, password)))).andReturn();
    }

    protected Session login(TestUser user) throws Exception {
        return login(user.email(), PASSWORD);
    }

    protected Session login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Credentials(email, password))))
                .andExpect(status().isOk()).andReturn();
        return sessionFrom(result);
    }

    protected static Session sessionFrom(MvcResult result) throws Exception {
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
        return new Session(token, result.getResponse().getCookie("iotiq_rt"));
    }

    protected static RequestPostProcessor bearer(Session session) {
        return bearer(session.accessToken());
    }

    protected static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    protected static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** An archive (soft delete) call of a lead or estimate, with a valid reason. */
    protected static MockHttpServletRequestBuilder archive(String url, Object... uriVars) {
        return archive(url, "Archived by an integration test", uriVars);
    }

    protected static MockHttpServletRequestBuilder archive(String url, String reason, Object... uriVars) {
        return withJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url, uriVars),
                "{\"reason\":\"" + reason + "\"}");
    }

    protected String body(Object value) {
        return json.writeValueAsString(value);
    }

    protected UUID roleId(RoleCode role) {
        return roleRepository.findByCode(role.name()).orElseThrow().getId();
    }

    protected long userVersion(UUID id) {
        return count("select version from users where id = ?", id);
    }

    public record TestUser(UUID id, String email) {
    }

    public record Session(String accessToken, Cookie refreshCookie) {
    }

    public record Credentials(String email, String password) {
    }
}
