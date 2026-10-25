package com.iotiq.partnererp.integration.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for tests that need the full context and the local partner_erp_test database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(CleanMigrateTestConfig.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    protected Long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
