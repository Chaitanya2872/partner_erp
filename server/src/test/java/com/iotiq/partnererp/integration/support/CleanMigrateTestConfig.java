package com.iotiq.partnererp.integration.support;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Rebuilds the test schema from the migrations at context start so every run sees exactly what
 * Flyway produces. Refuses to clean any database whose name does not end in "_test".
 */
@TestConfiguration(proxyBeanMethods = false)
public class CleanMigrateTestConfig {

    @Bean
    FlywayMigrationStrategy cleanMigrateStrategy() {
        return flyway -> {
            assertTestDatabase(flyway);
            flyway.clean();
            flyway.migrate();
        };
    }

    private static void assertTestDatabase(Flyway flyway) {
        DataSource dataSource = flyway.getConfiguration().getDataSource();
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            if (catalog == null || !catalog.endsWith("_test")) {
                throw new IllegalStateException("Refusing to clean non-test database: " + catalog);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot verify the test database", e);
        }
    }
}
