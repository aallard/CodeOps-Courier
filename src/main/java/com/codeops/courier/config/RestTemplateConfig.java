package com.codeops.courier.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configures a {@link RestTemplate} bean with reasonable connection and read timeouts.
 *
 * <p>Used for outbound HTTP calls to sibling CodeOps services.</p>
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates a {@link RestTemplate} with a 5-second connection timeout and
     * a 10-second read timeout.
     *
     * @param builder the Spring-provided {@link RestTemplateBuilder}
     * @return the configured {@link RestTemplate}
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
