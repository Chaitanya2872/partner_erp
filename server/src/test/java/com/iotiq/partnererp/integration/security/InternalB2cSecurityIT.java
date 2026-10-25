package com.iotiq.partnererp.integration.security;

import com.iotiq.partnererp.integration.support.ApiIntegrationTest;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Internal B2C names, B2C prices and landed costs never reach callers without
 * {@code PRODUCTS:VIEW_INTERNAL_B2C}, whatever their other product permissions.
 */
class InternalB2cSecurityIT extends ApiIntegrationTest {

    /** Distinctive values so a leak anywhere in a response body is detectable. */
    private static final String B2C_PRICE = "987654.32";
    private static final String COST_PRICE = "123456.78";

    private UUID productId;

    @BeforeEach
    void setInternalPrices() {
        productId = jdbc.queryForObject("select id from products where sku = 'STI-2M'", UUID.class);
        jdbc.update("update products set b2c_price = " + B2C_PRICE + ", cost_price = " + COST_PRICE + " where id = ?",
                productId);
    }

    @AfterEach
    void clearInternalPrices() {
        jdbc.update("update products set b2c_price = null, cost_price = null where id = ?", productId);
    }

    @Test
    void readersWithoutTheActionGetThePartnerSafeViewOnly() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser productWriter = createUser(RoleCode.ADMIN);
        // Full product rights and INTERNAL_B2C module read, but not the VIEW_INTERNAL_B2C action.
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", productWriter.id()).with(bearer(sa)),
                        body(Map.of("modules", List.of(
                                Map.of("moduleCode", "PRODUCTS", "read", true, "write", true, "delete", true,
                                        "export", true),
                                Map.of("moduleCode", "INTERNAL_B2C", "read", true, "export", true)),
                                "actions", List.of()))))
                .andExpect(status().isOk());

        for (Session session : List.of(login(createUser(RoleCode.USER)), login(createUser(RoleCode.ADMIN)),
                login(productWriter))) {
            String list = mvc.perform(get("/api/v1/products").with(bearer(session)).param("size", "200"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(29))
                    .andReturn().getResponse().getContentAsString();
            String one = mvc.perform(get("/api/v1/products/{id}", productId).with(bearer(session)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.partnerPrice").value(5749))
                    .andReturn().getResponse().getContentAsString();
            for (String response : List.of(list, one)) {
                assertThat(response).doesNotContain("b2cPrice", "costPrice", "b2cName", "aliases",
                        "987654", "123456.78", "2 Module With Edge");
            }
            // Searching by the internal B2C name finds nothing (no oracle on hidden names).
            mvc.perform(get("/api/v1/products").with(bearer(session)).param("q", "Module With Edge"))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    @Test
    void holdersOfTheActionSeeInternalFields() throws Exception {
        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser internal = createUser(RoleCode.ADMIN);
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", internal.id()).with(bearer(sa)),
                        body(Map.of("modules", List.of(Map.of("moduleCode", "PRODUCTS", "read", true)),
                                "actions", List.of("VIEW_INTERNAL_B2C")))))
                .andExpect(status().isOk());

        for (Session session : List.of(login(internal), sa)) {
            mvc.perform(get("/api/v1/products/{id}", productId).with(bearer(session)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.b2cName").value("2 Module With Edge"))
                    .andExpect(jsonPath("$.data.b2cPrice").value(987654.32))
                    .andExpect(jsonPath("$.data.costPrice").value(123456.78));
            mvc.perform(get("/api/v1/products").with(bearer(session)).param("q", "Module With Edge"))
                    .andExpect(jsonPath("$.data[*].sku").value(org.hamcrest.Matchers.hasItem("STI-2M")));
        }
    }

    @Test
    void productsRequireAuthenticationAndModuleRead() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/products/{id}", productId)).andExpect(status().isUnauthorized());

        Session sa = login(createUser(RoleCode.SUPER_ADMIN));
        TestUser noProducts = createUser(RoleCode.USER);
        mvc.perform(withJson(put("/api/v1/users/{id}/permissions", noProducts.id()).with(bearer(sa)),
                        body(Map.of("modules", List.of(Map.of("moduleCode", "DASHBOARD", "read", true)),
                                "actions", List.of()))))
                .andExpect(status().isOk());
        Session session = login(noProducts);
        mvc.perform(get("/api/v1/products").with(bearer(session))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/products/{id}", productId).with(bearer(session))).andExpect(status().isForbidden());
    }
}
