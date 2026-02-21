package com.codeops.courier.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration that registers custom interceptors.
 *
 * <p>Adds the {@link LoggingInterceptor} to all {@code /api/**} paths so that
 * every API request is logged with method, URI, status code, and duration.</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoggingInterceptor loggingInterceptor;

    /**
     * Registers the {@link LoggingInterceptor} for all API paths.
     *
     * @param registry the interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/api/**");
    }
}
