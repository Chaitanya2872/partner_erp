package com.iotiq.partnererp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Repositories live inside each module package (modules/&lt;module&gt;/repository).
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.iotiq.partnererp.modules")
public class JpaConfig {
}
