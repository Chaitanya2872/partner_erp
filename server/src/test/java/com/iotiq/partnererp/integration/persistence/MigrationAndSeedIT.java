package com.iotiq.partnererp.integration.persistence;

import com.iotiq.partnererp.integration.support.AbstractIntegrationTest;
import com.iotiq.partnererp.modules.products.repository.ProductRepository;
import com.iotiq.partnererp.modules.settings.entity.CompanySettings;
import com.iotiq.partnererp.modules.settings.entity.PartnerTier;
import com.iotiq.partnererp.modules.settings.repository.CompanySettingsRepository;
import com.iotiq.partnererp.modules.settings.repository.PartnerTierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema and seed data produced by Flyway V1-V12 against the 29-product catalogue, tiers and
 * settings of the original application (docs/reference/IOTIQ_Partner_ERP_original.html).
 */
class MigrationAndSeedIT extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PartnerTierRepository tierRepository;

    @Autowired
    private CompanySettingsRepository settingsRepository;

    @Autowired
    private DataSource dataSource;

    @Test
    void allMigrationsAppliedSuccessfully() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16");
        assertThat(count("select count(*) from flyway_schema_history where not success")).isZero();
    }

    @Test
    void productCatalogueMatchesSource() {
        assertThat(productRepository.count()).isEqualTo(29);

        Map<String, Long> perCategory = jdbc.queryForList(
                        "select category, count(*) as n from products group by category")
                .stream()
                .collect(Collectors.toMap(r -> (String) r.get("category"), r -> ((Number) r.get("n")).longValue()));
        assertThat(perCategory).containsExactlyInAnyOrderEntriesOf(Map.of(
                "SMART_TOUCH_INTERFACES", 6L,
                "SMART_RELAY_SENSING", 14L,
                "SMART_GLIDE", 4L,
                "LOCKS_ENTRY", 5L));

        assertThat(jdbc.queryForObject("select sum(partner_price) from products", BigDecimal.class))
                .isEqualByComparingTo("344094");
        // The source has no B2C prices, landed costs or HSN codes; none are invented.
        assertThat(count("select count(*) from products where b2c_price is not null")).isZero();
        assertThat(count("select count(*) from products where cost_price is not null")).isZero();
        assertThat(count("select count(*) from products where hsn is not null")).isZero();
        assertThat(count("select count(*) from products where gst_percent <> 18")).isZero();
        assertThat(count("select count(*) from products where unit <> 'nos' or not active")).isZero();
        assertThat(count("select count(*) from products where cardinality(aliases) > 0")).isZero();
    }

    @Test
    void productSpotChecks() {
        assertThat(productRepository.findBySkuIgnoreCase("sti-2m")).hasValueSatisfying(p -> {
            assertThat(p.getB2cName()).isEqualTo("2 Module With Edge");
            assertThat(p.getPartnerName()).isEqualTo("Smart Touch Interface - 2M (STI-2M)");
            assertThat(p.getPartnerPrice()).isEqualByComparingTo("5749");
            assertThat(p.getB2cPrice()).isNull();
        });
        assertThat(productRepository.findBySkuIgnoreCase("SRH-15A"))
                .hasValueSatisfying(p -> assertThat(p.getPartnerPrice()).isEqualByComparingTo("2625"));
        assertThat(productRepository.findBySkuIgnoreCase("LK-KYRA"))
                .hasValueSatisfying(p -> assertThat(p.getPartnerPrice()).isEqualByComparingTo("32999"));
        assertThat(productRepository.findBySkuIgnoreCase("BT-MOD"))
                .hasValueSatisfying(p -> assertThat(p.getPartnerPrice()).isEqualByComparingTo("4055"));
    }

    @Test
    void productSeedIsIdempotent() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__seed_products.sql")).execute(dataSource);
        assertThat(productRepository.count()).isEqualTo(29);
    }

    @Test
    void tiersMatchSource() {
        List<PartnerTier> tiers = tierRepository.findAllByOrderByTierLevelAsc();
        assertThat(tiers).extracting(PartnerTier::getTierLevel).containsExactly((short) 1, (short) 2, (short) 3, (short) 4);
        assertThat(tiers).extracting(PartnerTier::getName).containsExactly("Registered", "Preferred", "Elite", "Strategic");
        assertThat(tiers).extracting(t -> t.getCommissionPercent().intValue()).containsExactly(8, 10, 12, 15);
        assertThat(tiers).extracting(t -> t.getMinimumRevenue().longValue()).containsExactly(0L, 1_000_000L, 3_000_000L, 10_000_000L);
        assertThat(tiers).extracting(PartnerTier::getPayoutBusinessDays).containsExactly((short) 15, (short) 12, (short) 10, (short) 7);
    }

    @Test
    void companySettingsDefaultsMatchSource() {
        assertThat(settingsRepository.count()).isEqualTo(1);
        CompanySettings s = settingsRepository.findSingleton().orElseThrow();
        assertThat(s.getCompanyName()).isEqualTo("IOTIQ");
        assertThat(s.getPhone()).isEqualTo("8121234162");
        assertThat(s.getEmail()).isEqualTo("gowtham.k@iotiq.co.in");
        assertThat(s.getGstin()).isNull();
        assertThat(s.getLeadProtectionDays()).isEqualTo(90);
        assertThat(s.getClawbackDays()).isEqualTo(90);
        assertThat(s.getTdsPercent()).isEqualByComparingTo("2");
        assertThat(s.getTdsThreshold()).isEqualByComparingTo("20000");
        assertThat(s.getAcknowledgementHours()).isEqualTo(48);
        assertThat(s.getEstimateSlaDays()).isEqualTo(5);
        assertThat(s.getEstimateValidityDays()).isEqualTo(15);
        assertThat(s.getInvoiceDueDays()).isEqualTo(15);
        assertThat(s.getDefaultGstPercent()).isEqualByComparingTo("18");
    }

    @Test
    void threeActiveRolesAndRetiredPhaseTwoRoles() {
        // V12 replaced the six-role model (D-23) with the approved SUPER_ADMIN / ADMIN / USER model.
        assertThat(jdbc.queryForList("select code from roles where active order by code", String.class))
                .containsExactly("ADMIN", "SUPER_ADMIN", "USER");
        assertThat(jdbc.queryForList("select display_name from roles where active order by code", String.class))
                .containsExactly("Admin", "Super Admin", "User");
        assertThat(jdbc.queryForList("select code from roles where not active order by code", String.class))
                .containsExactly("FINANCE", "PARTNER", "SALES_EXECUTIVE", "SALES_MANAGER");
        assertThat(count("select count(*) from users u join roles r on r.id = u.role_id where not r.active")).isZero();
        assertThat(count("select count(*) from information_schema.tables where table_name in ('user_roles', 'role_permissions')"))
                .isZero();
    }

    @Test
    void moduleCatalogueAndPermissionTemplates() {
        assertThat(jdbc.queryForList("select code from modules order by sort_order", String.class)).containsExactly(
                "DASHBOARD", "PARTNERS", "LEADS", "PRODUCTS", "ESTIMATES", "INVOICES", "PAYMENTS", "PAYOUTS",
                "INTERNAL_B2C", "REPORTS", "SETTINGS", "USER_MANAGEMENT", "ROLE_MANAGEMENT", "AUDIT_LOGS");
        assertThat(jdbc.queryForList("select code from permissions where active order by code", String.class))
                .containsExactlyInAnyOrder("DELETE", "EXPORT", "APPROVE", "MARK_PAYOUT_PAID", "REVERSE_PAYMENT",
                        "VIEW_INTERNAL_B2C", "MANAGE_USERS", "MANAGE_PERMISSIONS", "MANAGE_SETTINGS",
                        "VIEW_PARTNER_SENSITIVE");
        assertThat(count("select count(*) from permissions where not active")).isEqualTo(29);

        Map<String, String> admin = template("ADMIN");
        assertThat(admin).containsExactlyInAnyOrderEntriesOf(Map.of(
                "DASHBOARD", "R", "PARTNERS", "RW", "LEADS", "RW", "PRODUCTS", "RW", "ESTIMATES", "RW",
                "INVOICES", "RW", "PAYMENTS", "RW", "PAYOUTS", "R", "REPORTS", "R"));
        assertThat(template("USER")).containsExactlyInAnyOrderEntriesOf(Map.of("DASHBOARD", "R", "PRODUCTS", "R"));
        // Write never implies the sensitive flags in the defaults.
        assertThat(count("select count(*) from role_permission_templates where can_delete or can_export or can_approve"))
                .isZero();
    }

    private Map<String, String> template(String role) {
        return jdbc.queryForList("""
                        select m.code, case when t.can_write then 'RW' else 'R' end as level
                        from role_permission_templates t join roles r on r.id = t.role_id join modules m on m.id = t.module_id
                        where r.code = ?""", role).stream()
                .collect(Collectors.toMap(r -> (String) r.get("code"), r -> (String) r.get("level")));
    }
}
