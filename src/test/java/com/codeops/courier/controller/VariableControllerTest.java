package com.codeops.courier.controller;

import com.codeops.courier.dto.request.BatchSaveGlobalVariablesRequest;
import com.codeops.courier.dto.request.SaveGlobalVariableRequest;
import com.codeops.courier.dto.response.GlobalVariableResponse;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.security.JwtAuthFilter;
import com.codeops.courier.security.JwtTokenValidator;
import com.codeops.courier.security.RateLimitFilter;
import com.codeops.courier.config.RequestCorrelationFilter;
import com.codeops.courier.service.VariableService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
 * Controller-layer tests for {@link VariableController}.
 *
 * <p>Uses {@code @WebMvcTest} slice with a lightweight security configuration.
 * Security filter beans are disabled via {@link FilterRegistrationBean}.</p>
 */
@WebMvcTest(VariableController.class)
@Import(VariableControllerTest.TestSecurityConfig.class)
class VariableControllerTest {

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

    @MockBean VariableService variableService;

    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean RequestCorrelationFilter requestCorrelationFilter;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID VARIABLE_ID = UUID.randomUUID();
    private static final String BASE_URL = "/api/v1/courier/variables/global";

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "test@test.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── getGlobalVariables ───

    @Test
    void getGlobalVariables_200() throws Exception {
        when(variableService.getGlobalVariables(TEAM_ID)).thenReturn(List.of(buildVarResponse()));

        mockMvc.perform(get(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].variableKey").value("BASE_URL"));
    }

    @Test
    void getGlobalVariables_empty_200() throws Exception {
        when(variableService.getGlobalVariables(TEAM_ID)).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── saveGlobalVariable ───

    @Test
    void saveGlobalVariable_200() throws Exception {
        SaveGlobalVariableRequest request = new SaveGlobalVariableRequest("BASE_URL", "https://api.example.com", false, true);
        when(variableService.saveGlobalVariable(eq(TEAM_ID), any())).thenReturn(buildVarResponse());

        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variableKey").value("BASE_URL"));
    }

    @Test
    void saveGlobalVariable_invalidBody_400() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── batchSaveGlobalVariables ───

    @Test
    void batchSaveGlobalVariables_200() throws Exception {
        SaveGlobalVariableRequest entry = new SaveGlobalVariableRequest("BASE_URL", "https://api.example.com", false, true);
        BatchSaveGlobalVariablesRequest request = new BatchSaveGlobalVariablesRequest(List.of(entry));
        when(variableService.batchSaveGlobalVariables(eq(TEAM_ID), any())).thenReturn(List.of(buildVarResponse()));

        mockMvc.perform(post(BASE_URL + "/batch")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ─── deleteGlobalVariable ───

    @Test
    void deleteGlobalVariable_204() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + VARIABLE_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNoContent());

        verify(variableService).deleteGlobalVariable(VARIABLE_ID, TEAM_ID);
    }

    @Test
    void deleteGlobalVariable_notFound_404() throws Exception {
        doThrow(new NotFoundException("Variable not found"))
                .when(variableService).deleteGlobalVariable(VARIABLE_ID, TEAM_ID);

        mockMvc.perform(delete(BASE_URL + "/" + VARIABLE_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
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

    private GlobalVariableResponse buildVarResponse() {
        return new GlobalVariableResponse(VARIABLE_ID, TEAM_ID, "BASE_URL",
                "https://api.example.com", false, true, Instant.now(), Instant.now());
    }
}
