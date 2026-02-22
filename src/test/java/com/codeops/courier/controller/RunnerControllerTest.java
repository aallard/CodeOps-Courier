package com.codeops.courier.controller;

import com.codeops.courier.dto.request.StartCollectionRunRequest;
import com.codeops.courier.dto.response.PageResponse;
import com.codeops.courier.dto.response.RunIterationResponse;
import com.codeops.courier.dto.response.RunResultDetailResponse;
import com.codeops.courier.dto.response.RunResultResponse;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.entity.enums.RunStatus;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.security.JwtAuthFilter;
import com.codeops.courier.security.JwtTokenValidator;
import com.codeops.courier.security.RateLimitFilter;
import com.codeops.courier.config.RequestCorrelationFilter;
import com.codeops.courier.service.CollectionRunnerService;
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
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for {@link RunnerController}.
 *
 * <p>Uses {@code @WebMvcTest} slice with a lightweight security configuration.
 * Security filter beans are disabled via {@link FilterRegistrationBean}.</p>
 */
@WebMvcTest(RunnerController.class)
@Import(RunnerControllerTest.TestSecurityConfig.class)
class RunnerControllerTest {

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

    @MockBean CollectionRunnerService runnerService;

    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean RequestCorrelationFilter requestCorrelationFilter;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COLLECTION_ID = UUID.randomUUID();
    private static final UUID RUN_RESULT_ID = UUID.randomUUID();
    private static final UUID ENV_ID = UUID.randomUUID();
    private static final String BASE_URL = "/api/v1/courier/runner";

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "test@test.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── startRun ───

    @Test
    void startRun_201() throws Exception {
        RunResultDetailResponse detail = new RunResultDetailResponse(buildRunResult(), List.of());
        when(runnerService.startRun(any(StartCollectionRunRequest.class), eq(TEAM_ID), eq(USER_ID)))
                .thenReturn(detail);

        mockMvc.perform(post(BASE_URL + "/start")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildStartRequest()))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.id").value(RUN_RESULT_ID.toString()))
                .andExpect(jsonPath("$.summary.status").value("COMPLETED"));
    }

    @Test
    void startRun_invalidBody_400() throws Exception {
        String invalidJson = """
                {"collectionId": null, "iterationCount": 0}
                """;

        mockMvc.perform(post(BASE_URL + "/start")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(invalidJson)
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── getRunResults ───

    @Test
    void getRunResults_200_paginated() throws Exception {
        PageResponse<RunResultResponse> page = new PageResponse<>(
                List.of(buildRunResult()), 0, 20, 1, 1, true);
        when(runnerService.getRunResultsPaged(TEAM_ID, 0, 20)).thenReturn(page);

        mockMvc.perform(get(BASE_URL + "/results")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ─── getRunResultsByCollection ───

    @Test
    void getRunResultsByCollection_200() throws Exception {
        when(runnerService.getRunResults(COLLECTION_ID, TEAM_ID))
                .thenReturn(List.of(buildRunResult()));

        mockMvc.perform(get(BASE_URL + "/results/collection/" + COLLECTION_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(RUN_RESULT_ID.toString()));
    }

    // ─── getRunResult ───

    @Test
    void getRunResult_200() throws Exception {
        when(runnerService.getRunResult(RUN_RESULT_ID, TEAM_ID)).thenReturn(buildRunResult());

        mockMvc.perform(get(BASE_URL + "/results/" + RUN_RESULT_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RUN_RESULT_ID.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getRunResult_notFound_404() throws Exception {
        when(runnerService.getRunResult(RUN_RESULT_ID, TEAM_ID))
                .thenThrow(new NotFoundException("Run result not found"));

        mockMvc.perform(get(BASE_URL + "/results/" + RUN_RESULT_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── getRunResultDetail ───

    @Test
    void getRunResultDetail_200() throws Exception {
        RunIterationResponse iteration = new RunIterationResponse(UUID.randomUUID(), 1,
                "Login", HttpMethod.POST, "https://api.example.com/login",
                200, 120L, 256L, true, null, null);
        RunResultDetailResponse detail = new RunResultDetailResponse(buildRunResult(), List.of(iteration));
        when(runnerService.getRunResultDetail(RUN_RESULT_ID, TEAM_ID)).thenReturn(detail);

        mockMvc.perform(get(BASE_URL + "/results/" + RUN_RESULT_ID + "/detail")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.id").value(RUN_RESULT_ID.toString()))
                .andExpect(jsonPath("$.iterations").isArray())
                .andExpect(jsonPath("$.iterations[0].requestName").value("Login"));
    }

    // ─── cancelRun ───

    @Test
    void cancelRun_200() throws Exception {
        RunResultResponse cancelled = new RunResultResponse(RUN_RESULT_ID, TEAM_ID, COLLECTION_ID,
                ENV_ID, RunStatus.CANCELLED, 5, 3, 2, 10, 7, 3,
                3500L, 1, 0, null, Instant.now(), Instant.now(), USER_ID, Instant.now());
        when(runnerService.cancelRun(RUN_RESULT_ID, TEAM_ID, USER_ID)).thenReturn(cancelled);

        mockMvc.perform(post(BASE_URL + "/results/" + RUN_RESULT_ID + "/cancel")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ─── deleteRunResult ───

    @Test
    void deleteRunResult_204() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/results/" + RUN_RESULT_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNoContent());

        verify(runnerService).deleteRunResult(RUN_RESULT_ID, TEAM_ID);
    }

    @Test
    void deleteRunResult_notFound_404() throws Exception {
        doThrow(new NotFoundException("Run result not found"))
                .when(runnerService).deleteRunResult(RUN_RESULT_ID, TEAM_ID);

        mockMvc.perform(delete(BASE_URL + "/results/" + RUN_RESULT_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── Security ───

    @Test
    void unauthorized_401() throws Exception {
        mockMvc.perform(get(BASE_URL + "/results")
                        .header("X-Team-ID", TEAM_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTeamIdHeader_400() throws Exception {
        mockMvc.perform(get(BASE_URL + "/results")
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ───

    private StartCollectionRunRequest buildStartRequest() {
        return new StartCollectionRunRequest(COLLECTION_ID, ENV_ID, 3, 500, null, null);
    }

    private RunResultResponse buildRunResult() {
        return new RunResultResponse(RUN_RESULT_ID, TEAM_ID, COLLECTION_ID, ENV_ID,
                RunStatus.COMPLETED, 5, 5, 0, 10, 10, 0,
                5000L, 3, 500, null, Instant.now(), Instant.now(), USER_ID, Instant.now());
    }
}
