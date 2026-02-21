package com.codeops.courier.security;

import com.codeops.courier.config.RequestCorrelationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Configures Spring Security for the CodeOps-Courier API with stateless JWT-based authentication.
 *
 * <p>Key security behaviors:</p>
 * <ul>
 *   <li>CSRF is disabled since the API uses stateless JWT tokens (no cookie-based auth)</li>
 *   <li>Session management is set to {@link SessionCreationPolicy#STATELESS}</li>
 *   <li>Health and Swagger UI endpoints are publicly accessible</li>
 *   <li>All other {@code /api/**} endpoints require authentication</li>
 *   <li>Security headers include CSP, HSTS, X-Frame-Options DENY, and X-Content-Type-Options</li>
 *   <li>{@link RequestCorrelationFilter}, {@link RateLimitFilter}, and {@link JwtAuthFilter}
 *       are registered before {@link UsernamePasswordAuthenticationFilter}</li>
 * </ul>
 *
 * @see JwtAuthFilter
 * @see RateLimitFilter
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RequestCorrelationFilter requestCorrelationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * Builds the {@link SecurityFilterChain} with stateless session management, JWT authentication,
     * rate limiting, CORS support, and security response headers.
     *
     * @param http the {@link HttpSecurity} builder provided by Spring Security
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if an error occurs during security configuration
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF disabled: stateless JWT API with no cookie-based auth — not vulnerable to CSRF
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/courier/health").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> {})
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(requestCorrelationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
