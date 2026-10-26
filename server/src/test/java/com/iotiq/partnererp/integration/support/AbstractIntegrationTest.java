package com.iotiq.partnererp.integration.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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

    @Autowired
    private PlatformTransactionManager transactionManager;

    protected Long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    /**
     * Test cleanup only: hard-deletes the documents that use products matching {@code skuLike} and then the products.
     * A product that was ever used can't be deleted (V15 backstop trigger), so the trigger is disabled for this one
     * transaction; DDL is transactional in PostgreSQL, so it is enabled again on commit or rollback.
     */
    protected void deleteTestProducts(String skuLike) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("ALTER TABLE products DISABLE TRIGGER trg_products_refuse_referenced_delete");
            jdbc.update("delete from estimates where id in (select estimate_id from estimate_items ei join products p "
                    + "on p.id = ei.product_id where p.sku ilike ?)", skuLike);
            jdbc.update("delete from products where sku ilike ?", skuLike);
            jdbc.execute("ALTER TABLE products ENABLE TRIGGER trg_products_refuse_referenced_delete");
        });
    }
}
