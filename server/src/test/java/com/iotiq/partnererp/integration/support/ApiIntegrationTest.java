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

    /** An ACTIVE account with the role's default permission template. */
    protected TestUser createUser(RoleCode role, boolean mustChangePassword) {
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
