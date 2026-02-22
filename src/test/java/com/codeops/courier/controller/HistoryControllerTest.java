package com.codeops.courier.controller;

import com.codeops.courier.dto.response.PageResponse;
import com.codeops.courier.dto.response.RequestHistoryDetailResponse;
import com.codeops.courier.dto.response.RequestHistoryResponse;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.security.JwtAuthFilter;
import com.codeops.courier.security.JwtTokenValidator;
import com.codeops.courier.security.RateLimitFilter;
import com.codeops.courier.config.RequestCorrelationFilter;
import com.codeops.courier.service.HistoryService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for {@link HistoryController}.
 *
 * <p>Uses {@code @WebMvcTest} slice with a lightweight security configuration.
 * Security filter beans are disabled via {@link FilterRegistrationBean}.</p>
 */
@WebMvcTest(HistoryController.class)
@Import(HistoryControllerTest.TestSecurityConfig.class)
class HistoryControllerTest {

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

    @MockBean HistoryService historyService;

    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean RequestCorrelationFilter requestCorrelationFilter;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID HISTORY_ID = UUID.randomUUID();
    private static final String BASE_URL = "/api/v1/courier/history";

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "test@test.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── getHistory ───

    @Test
    void getHistory_200_paginated() throws Exception {
        PageResponse<RequestHistoryResponse> page = new PageResponse<>(
                List.of(buildHistoryResponse()), 0, 20, 1, 1, true);
        when(historyService.getHistory(TEAM_ID, 0, 20)).thenReturn(page);

        mockMvc.perform(get(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getHistory_customPage_200() throws Exception {
        PageResponse<RequestHistoryResponse> page = new PageResponse<>(
                List.of(), 2, 10, 25, 3, false);
        when(historyService.getHistory(TEAM_ID, 2, 10)).thenReturn(page);

        mockMvc.perform(get(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .param("page", "2")
                        .param("size", "10")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));
    }

    // ─── getUserHistory ───

    @Test
    void getUserHistory_200() throws Exception {
        PageResponse<RequestHistoryResponse> page = new PageResponse<>(
                List.of(buildHistoryResponse()), 0, 20, 1, 1, true);
        when(historyService.getUserHistory(TEAM_ID, USER_ID, 0, 20)).thenReturn(page);

        mockMvc.perform(get(BASE_URL + "/user/" + USER_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ─── getHistoryByMethod ───

    @Test
    void getHistoryByMethod_200() throws Exception {
        PageResponse<RequestHistoryResponse> page = new PageResponse<>(
                List.of(buildHistoryResponse()), 0, 20, 1, 1, true);
        when(historyService.getHistoryByMethod(TEAM_ID, HttpMethod.GET, 0, 20)).thenReturn(page);

        mockMvc.perform(get(BASE_URL + "/method/GET")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ─── searchHistory ───

    @Test
    void searchHistory_200() throws Exception {
        when(historyService.searchHistory(TEAM_ID, "api.example")).thenReturn(List.of(buildHistoryResponse()));

        mockMvc.perform(get(BASE_URL + "/search")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .param("query", "api.example")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].requestUrl").value("https://api.example.com/users"));
    }

    @Test
    void searchHistory_noResults_200() throws Exception {
        when(historyService.searchHistory(TEAM_ID, "nonexistent")).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/search")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .param("query", "nonexistent")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── getHistoryDetail ───

    @Test
    void getHistoryDetail_200() throws Exception {
        RequestHistoryDetailResponse detail = new RequestHistoryDetailResponse(
                HISTORY_ID, USER_ID, HttpMethod.GET, "https://api.example.com/users",
                "{\"Content-Type\":\"application/json\"}", null,
                200, "{\"Content-Type\":\"application/json\"}", "{\"users\":[]}",
                42L, 150L, "application/json", null, null, null, Instant.now());
        when(historyService.getHistoryDetail(HISTORY_ID, TEAM_ID)).thenReturn(detail);

        mockMvc.perform(get(BASE_URL + "/" + HISTORY_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(HISTORY_ID.toString()))
                .andExpect(jsonPath("$.responseStatus").value(200));
    }

    @Test
    void getHistoryDetail_notFound_404() throws Exception {
        when(historyService.getHistoryDetail(HISTORY_ID, TEAM_ID))
                .thenThrow(new NotFoundException("History entry not found"));

        mockMvc.perform(get(BASE_URL + "/" + HISTORY_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── deleteHistory ───

    @Test
    void deleteHistory_204() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + HISTORY_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNoContent());

        verify(historyService).deleteHistoryEntry(HISTORY_ID, TEAM_ID);
    }

    @Test
    void deleteHistory_notFound_404() throws Exception {
        doThrow(new NotFoundException("History entry not found"))
                .when(historyService).deleteHistoryEntry(HISTORY_ID, TEAM_ID);

        mockMvc.perform(delete(BASE_URL + "/" + HISTORY_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── clearHistory ───

    @Test
    void clearHistory_withDaysToRetain_204() throws Exception {
        when(historyService.clearOldHistory(TEAM_ID, 30)).thenReturn(10);

        mockMvc.perform(delete(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .param("daysToRetain", "30")
                        .with(authentication(adminAuth())))
                .andExpect(status().isNoContent());

        verify(historyService).clearOldHistory(TEAM_ID, 30);
    }

    @Test
    void clearAllHistory_204() throws Exception {
        mockMvc.perform(delete(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNoContent());

        verify(historyService).clearTeamHistory(TEAM_ID);
    }

    // ─── Security ───

    @Test
    void unauthorized_401() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTeamIdHeader_400() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ───

    private RequestHistoryResponse buildHistoryResponse() {
        return new RequestHistoryResponse(HISTORY_ID, USER_ID, HttpMethod.GET,
                "https://api.example.com/users", 200, 150L, 42L,
                "application/json", null, null, null, Instant.now());
    }
}
