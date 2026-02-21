package com.codeops.courier.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for sibling service URLs, bound to the
 * {@code codeops.services} prefix in application properties.
 *
 * <p>Enables type-safe injection of CodeOps-Server, Registry, Vault, and Logger
 * base URLs for cross-service REST calls.</p>
 */
@ConfigurationProperties(prefix = "codeops.services")
@Getter
@Setter
public class ServiceUrlProperties {
    private String serverUrl;
    private String registryUrl;
    private String vaultUrl;
    private String loggerUrl;
}
