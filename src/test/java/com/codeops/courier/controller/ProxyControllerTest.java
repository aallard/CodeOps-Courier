package com.codeops.courier.controller;

import com.codeops.courier.dto.request.SendRequestProxyRequest;
import com.codeops.courier.dto.response.ProxyResponse;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.security.JwtAuthFilter;
import com.codeops.courier.security.JwtTokenValidator;
import com.codeops.courier.security.RateLimitFilter;
import com.codeops.courier.config.RequestCorrelationFilter;
import com.codeops.courier.service.RequestProxyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for {@link ProxyController}.
 *
 * <p>Uses {@code @WebMvcTest} slice with a lightweight security configuration.
 * Security filter beans are disabled via {@link FilterRegistrationBean}.</p>
 */
@WebMvcTest(ProxyController.class)
@Import(ProxyControllerTest.TestSecurityConfig.class)
class ProxyControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((req, resp, authEx) ->
                                    resp.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED)));
            return http.build();
        }

        @Bean
        FilterRegistrationBean<JwtAuthFilter> disableJwtAuth(JwtAuthFilter f) {
            var reg = new FilterRegistrationBean<>(f);
            reg.setEnabled(false);
            return reg;
        }

        @Bean
        FilterRegistrationBean<RateLimitFilter> disableRateLimit(RateLimitFilter f) {
            var reg = new FilterRegistrationBean<>(f);
            reg.setEnabled(false);
            return reg;
        }

        @Bean
        FilterRegistrationBean<RequestCorrelationFilter> disableCorrelation(RequestCorrelationFilter f) {
            var reg = new FilterRegistrationBean<>(f);
            reg.setEnabled(false);
            return reg;
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RequestProxyService requestProxyService;

    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean RequestCorrelationFilter requestCorrelationFilter;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID ENV_ID = UUID.randomUUID();
    private static final String BASE_URL = "/api/v1/courier/proxy";

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "test@test.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── sendRequest ───

    @Test
    void sendRequest_200() throws Exception {
        ProxyResponse response = buildProxyResponse();
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(TEAM_ID), eq(USER_ID)))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/send")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildSendRequest()))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.responseTimeMs").value(150))
                .andExpect(jsonPath("$.contentType").value("application/json"));
    }

    @Test
    void sendRequest_invalidBody_400() throws Exception {
        String invalidJson = """
                {"method": null, "url": ""}
                """;

        mockMvc.perform(post(BASE_URL + "/send")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(invalidJson)
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── executeStoredRequest ───

    @Test
    void executeStoredRequest_200() throws Exception {
        ProxyResponse response = buildProxyResponse();
        when(requestProxyService.executeStoredRequest(REQUEST_ID, TEAM_ID, USER_ID, null))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/send/" + REQUEST_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.responseBody").value("{\"users\":[]}"));
    }

    @Test
    void executeStoredRequest_withEnvironment_200() throws Exception {
        ProxyResponse response = buildProxyResponse();
        when(requestProxyService.executeStoredRequest(REQUEST_ID, TEAM_ID, USER_ID, ENV_ID))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/send/" + REQUEST_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .param("environmentId", ENV_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));
    }

    @Test
    void executeStoredRequest_notFound_404() throws Exception {
        when(requestProxyService.executeStoredRequest(REQUEST_ID, TEAM_ID, USER_ID, null))
                .thenThrow(new NotFoundException("Request not found"));

        mockMvc.perform(post(BASE_URL + "/send/" + REQUEST_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── Security ───

    @Test
    void unauthorized_401() throws Exception {
        mockMvc.perform(post(BASE_URL + "/send")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildSendRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTeamIdHeader_400() throws Exception {
        mockMvc.perform(post(BASE_URL + "/send/" + REQUEST_ID)
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ───

    private SendRequestProxyRequest buildSendRequest() {
        return new SendRequestProxyRequest(HttpMethod.GET, "https://api.example.com/users",
                List.of(), null, null, null, null, true, 5000, true);
    }

    private ProxyResponse buildProxyResponse() {
        return new ProxyResponse(200, "OK",
                Map.of("Content-Type", List.of("application/json")),
                "{\"users\":[]}", 150L, 42L, "application/json", List.of(), UUID.randomUUID());
    }
}
