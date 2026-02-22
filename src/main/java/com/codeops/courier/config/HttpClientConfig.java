package com.codeops.courier.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configuration for the HTTP client used by RequestProxyService.
 * Provides a default HttpClient bean with sensible defaults.
 * Individual requests override timeout and redirect settings as needed.
 */
@Configuration
public class HttpClientConfig {

    /**
     * Creates a default HttpClient with connection timeout and manual redirect handling.
     * Redirects are handled manually by RequestProxyService for tracking purposes.
     *
     * @return the configured HttpClient instance
     */
    @Bean
    public HttpClient courierHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(AppConstants.DEFAULT_TIMEOUT_MS))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }
}
