package com.iotiq.partnererp.integration.security;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.security.JwtService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIT extends ApiIntegrationTest {

    private static final String NEW_PASSWORD = "Rotated-Fixture-77";

    @Autowired
    private JwtService jwtService;

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        TestUser user = createUser(RoleCode.USER);
        MvcResult result = loginResult(user.email(), PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("iotiq_rt=").contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        String body = result.getResponse().getContentAsString();
        String refreshToken = result.getResponse().getCookie("iotiq_rt").getValue();
        assertThat(body).doesNotContain(refreshToken).doesNotContain("refreshToken").doesNotContain("passwordHash");

        Session session = sessionFrom(result);
        mvc.perform(get("/api/v1/auth/me").with(bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(user.email()))
                .andExpect(jsonPath("$.data.role.code").value("USER"))
                .andExpect(jsonPath("$.data.role.displayName").value("User"))
                .andExpect(jsonPath("$.data.superAdmin").value(false))
                .andExpect(jsonPath("$.data.authorities").isArray());
        assertThat(count("select count(*) from refresh_tokens where user_id = ? and token_hash <> ?", user.id(),
                refreshToken)).isEqualTo(1);
        assertThat(count("select count(*) from refresh_tokens where token_hash = ?", refreshToken)).isZero();
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGenericError() throws Exception {
        TestUser user = createUser(RoleCode.USER);
        mvc.perform(withJson(post("/api/v1/auth/login"), body(new Credentials(user.email(), "Wrong-Password-1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        mvc.perform(withJson(post("/api/v1/auth/login"), body(new Credentials("nobody@test.iotiq.local", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.detail").value("Invalid email or password."));
        assertThat(count("select failed_login_count from users where id = ?", user.id())).isEqualTo(1);
    }

    @Test
    void disabledUserCannotSignIn() throws Exception {
        TestUser user = createUser(RoleCode.USER);
        jdbc.update("update users set status = 'DISABLED' where id = ?", user.id());
        mvc.perform(withJson(post("/api/v1/auth/login"), body(new Credentials(user.email(), PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));
        // A wrong password on a disabled account does not reveal that the account exists.
        mvc.perform(withJson(post("/api/v1/auth/login"), body(new Credentials(user.email(), "Wrong-Password-1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void repeatedFailuresLockTheAccount() throws Exception {
        TestUser user = createUser(RoleCode.USER);
        for (int i = 0; i < 5; i++) {
            mvc.perform(withJson(post("/api/v1/auth/login"), body(new Credentials(user.email(), "Wrong-Password-1"))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(withJson(post("/api/v1/auth/login"), body(new Credentials(user.email(), PASSWORD))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
        assertThat(count("select count(*) from audit_logs where entity_id = ? and action = 'ACCOUNT_LOCKED'", user.id()))
                .isEqualTo(1);
    }

    @Test
    void protectedEndpointsRejectMissingMalformedForeignAndExpiredTokens() throws Exception {
        TestUser user = createUser(RoleCode.USER);
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/auth/me").with(bearer("not-a-jwt"))).andExpect(status().isUnauthorized());

        NimbusJwtEncoder foreign = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "another-secret-that-is-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        String forged = foreign.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("partner-erp").subject(user.id().toString()).issuedAt(now)
                        .expiresAt(now.plusSeconds(600)).claim(JwtService.SESSION_VERSION_CLAIM, 0)
                        .claims(c -> c.putAll(Map.of("role", "SUPER_ADMIN"))).build())).getTokenValue();
        mvc.perform(get("/api/v1/auth/me").with(bearer(forged))).andExpect(status().isUnauthorized());

        String expired = tx.execute(s -> {
            User entity = userRepository.findWithRoleById(user.id()).orElseThrow();
            return jwtService.issue(entity, Instant.now().minus(Duration.ofMinutes(16))).value();
        });
        mvc.perform(get("/api/v1/auth/me").with(bearer(expired))).andExpect(status().isUnauthorized());

        String valid = tx.execute(s -> jwtService.issue(userRepository.findWithRoleById(user.id()).orElseThrow()).value());
        mvc.perform(get("/api/v1/auth/me").with(bearer(valid))).andExpect(status().isOk());
    }

    @Test
    void accessTokenLivesFifteenMinutes() throws Exception {
        MvcResult result = loginResult(createUser(RoleCode.USER).email(), PASSWORD);
        assertThat((Integer) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.expiresIn")).isEqualTo(900);
    }

    @Test
    void refreshRotatesTheCookieAndDetectsReuse() throws Exception {
        TestUser user = createUser(RoleCode.USER);
        Session first = login(user);

        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(first.refreshCookie())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        Session second = sessionFrom(rotated);
        assertThat(second.refreshCookie().getValue()).isNotEqualTo(first.refreshCookie().getValue());
        mvc.perform(get("/api/v1/auth/me").with(bearer(second))).andExpect(status().isOk());

        // Replaying the rotated-out token is treated as theft: the whole family is revoked.
        mvc.perform(post("/api/v1/auth/refresh").cookie(first.refreshCookie()).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
        mvc.perform(post("/api/v1/auth/refresh").cookie(second.refreshCookie()).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        assertThat(count("select count(*) from refresh_tokens where user_id = ? and revoked_at is null", user.id())).isZero();
        // Both replays (the rotated-out token, then the family member revoked with it) are recorded.
        assertThat(count("select count(*) from audit_logs where entity_id = ? and action = 'REFRESH_TOKEN_REUSED'",
                user.id())).isEqualTo(2);
    }

    @Test
    void refreshRequiresCookieAndCsrfHeader() throws Exception {
        Session session = login(createUser(RoleCode.USER));
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_HEADER_MISSING"));
        mvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("iotiq_rt", "forged-value"))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Session session = login(createUser(RoleCode.USER));
        MvcResult out = mvc.perform(post("/api/v1/auth/logout").cookie(session.refreshCookie())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk()).andReturn();
        assertThat(out.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRotatesSessionsAndCredentials() throws Exception {
        TestUser user = createUser(RoleCode.USER);
        Session other = login(user);
        Session current = login(user);

        mvc.perform(withJson(post("/api/v1/auth/change-password").with(bearer(current)),
                        body(Map.of("currentPassword", "Wrong-Password-1", "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("currentPassword"));
        mvc.perform(withJson(post("/api/v1/auth/change-password").with(bearer(current)),
                        body(Map.of("currentPassword", PASSWORD, "newPassword", "short1"))))
                .andExpect(status().isBadRequest());

        MvcResult changed = mvc.perform(withJson(post("/api/v1/auth/change-password").with(bearer(current)),
                        body(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        Session fresh = sessionFrom(changed);

        // Every earlier session ends immediately: access tokens and refresh tokens alike.
        mvc.perform(get("/api/v1/auth/me").with(bearer(current))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").with(bearer(other))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(other.refreshCookie()).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").with(bearer(fresh))).andExpect(status().isOk());

        mvc.perform(withJson(post("/api/v1/auth/login"), body(new Credentials(user.email(), PASSWORD))))
                .andExpect(status().isUnauthorized());
        login(user.email(), NEW_PASSWORD);

        String audit = jdbc.queryForObject("select coalesce(string_agg(coalesce(old_values::text, '') || "
                + "coalesce(new_values::text, ''), ' '), '') from audit_logs where entity_id = ?", String.class, user.id());
        assertThat(audit).doesNotContain(PASSWORD).doesNotContain(NEW_PASSWORD)
                .doesNotContain(fresh.accessToken()).doesNotContain(fresh.refreshCookie().getValue());
    }

    @Test
    void mustChangePasswordBlocksEverythingButTheAccountEndpoints() throws Exception {
        TestUser user = createUser(RoleCode.USER, true);
        Session session = login(user);
        mvc.perform(get("/api/v1/products").with(bearer(session)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/v1/auth/me").with(bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        Session fresh = sessionFrom(mvc.perform(withJson(post("/api/v1/auth/change-password").with(bearer(session)),
                        body(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk()).andReturn());
        mvc.perform(get("/api/v1/products").with(bearer(fresh))).andExpect(status().isOk());
    }

    @Test
    void deactivationEndsExistingSessionsImmediately() throws Exception {
        TestUser admin = createUser(RoleCode.SUPER_ADMIN);
        TestUser user = createUser(RoleCode.USER);
        Session adminSession = login(admin);
        Session userSession = login(user);
        mvc.perform(get("/api/v1/auth/me").with(bearer(userSession))).andExpect(status().isOk());

        mvc.perform(withJson(patch("/api/v1/users/{id}/status", user.id()).with(bearer(adminSession)),
                        body(Map.of("status", "DISABLED", "version", userVersion(user.id())))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/auth/me").with(bearer(userSession))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(userSession.refreshCookie())
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }
}
