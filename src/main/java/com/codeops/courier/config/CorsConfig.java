package com.codeops.courier.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configures Cross-Origin Resource Sharing (CORS) policy for the CodeOps-Courier API.
 *
 * <p>Allowed origins are loaded from the {@code codeops.cors.allowed-origins} property
 * (comma-separated), defaulting to {@code http://localhost:3000} for local development.
 * Credentials are enabled for JWT bearer token support. Preflight responses are cached
 * for 3600 seconds (1 hour).</p>
 */
@Configuration
public class CorsConfig {

    @Value("${codeops.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    /**
     * Creates a {@link CorsConfigurationSource} that applies the CORS policy to all endpoints.
     *
     * <p>Allows GET, POST, PUT, DELETE, PATCH, and OPTIONS methods. Permits
     * {@code Authorization}, {@code Content-Type}, {@code X-Team-ID}, and
     * {@code X-Correlation-ID} request headers. Exposes the {@code Authorization}
     * header in responses.</p>
     *
     * @return the configured CORS configuration source registered for all URL patterns
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Team-ID", "X-Correlation-ID"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
