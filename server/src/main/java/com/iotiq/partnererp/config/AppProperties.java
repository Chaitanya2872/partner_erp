package com.iotiq.partnererp.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Application-specific settings bound from {@code app.*}. Values come from environment variables per profile.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors) {

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }
}
