package com.codeops.courier.controller;

import com.codeops.courier.dto.request.CloneEnvironmentRequest;
import com.codeops.courier.dto.request.CreateEnvironmentRequest;
import com.codeops.courier.dto.request.SaveEnvironmentVariablesRequest;
import com.codeops.courier.dto.request.UpdateEnvironmentRequest;
import com.codeops.courier.dto.response.EnvironmentResponse;
import com.codeops.courier.dto.response.EnvironmentVariableResponse;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.security.JwtAuthFilter;
import com.codeops.courier.security.JwtTokenValidator;
import com.codeops.courier.security.RateLimitFilter;
import com.codeops.courier.config.RequestCorrelationFilter;
import com.codeops.courier.service.EnvironmentService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for {@link EnvironmentController}.
 *
 * <p>Uses {@code @WebMvcTest} slice with a lightweight security configuration
 * that disables CSRF and requires authentication on all endpoints. Security
 * filter beans are disabled via {@link FilterRegistrationBean} to prevent
 * mock filters from breaking the servlet filter chain.</p>
 */
@WebMvcTest(EnvironmentController.class)
@Import(EnvironmentControllerTest.TestSecurityConfig.class)
class EnvironmentControllerTest {

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

    @MockBean EnvironmentService environmentService;

    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean RequestCorrelationFilter requestCorrelationFilter;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ENV_ID = UUID.randomUUID();
    private static final String BASE_URL = "/api/v1/courier/environments";

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "test@test.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── createEnvironment ───

    @Test
    void createEnvironment_201() throws Exception {
        CreateEnvironmentRequest request = new CreateEnvironmentRequest("Production", "Prod env");
        when(environmentService.createEnvironment(eq(TEAM_ID), any(UUID.class), any()))
                .thenReturn(buildEnvResponse());

        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Production"));
    }

    @Test
    void createEnvironment_invalidBody_400() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── getEnvironments ───

    @Test
    void getEnvironments_200() throws Exception {
        when(environmentService.getEnvironments(TEAM_ID)).thenReturn(List.of(buildEnvResponse()));

        mockMvc.perform(get(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Production"));
    }

    // ─── getActiveEnvironment ───

    @Test
    void getActiveEnvironment_200() throws Exception {
        when(environmentService.getActiveEnvironment(TEAM_ID)).thenReturn(buildEnvResponse());

        mockMvc.perform(get(BASE_URL + "/active")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Production"));
    }

    @Test
    void getActiveEnvironment_notFound_404() throws Exception {
        when(environmentService.getActiveEnvironment(TEAM_ID))
                .thenThrow(new NotFoundException("No active environment"));

        mockMvc.perform(get(BASE_URL + "/active")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── getEnvironment ───

    @Test
    void getEnvironment_200() throws Exception {
        when(environmentService.getEnvironment(ENV_ID, TEAM_ID)).thenReturn(buildEnvResponse());

        mockMvc.perform(get(BASE_URL + "/" + ENV_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ENV_ID.toString()));
    }

    @Test
    void getEnvironment_notFound_404() throws Exception {
        when(environmentService.getEnvironment(ENV_ID, TEAM_ID))
                .thenThrow(new NotFoundException("Environment not found"));

        mockMvc.perform(get(BASE_URL + "/" + ENV_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── updateEnvironment ───

    @Test
    void updateEnvironment_200() throws Exception {
        UpdateEnvironmentRequest request = new UpdateEnvironmentRequest("Updated", null);
        when(environmentService.updateEnvironment(eq(ENV_ID), eq(TEAM_ID), any()))
                .thenReturn(buildEnvResponse());

        mockMvc.perform(put(BASE_URL + "/" + ENV_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    // ─── activateEnvironment ───

    @Test
    void activateEnvironment_200() throws Exception {
        when(environmentService.setActiveEnvironment(ENV_ID, TEAM_ID)).thenReturn(buildEnvResponse());

        mockMvc.perform(put(BASE_URL + "/" + ENV_ID + "/activate")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    // ─── deleteEnvironment ───

    @Test
    void deleteEnvironment_204() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + ENV_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNoContent());

        verify(environmentService).deleteEnvironment(ENV_ID, TEAM_ID);
    }

    // ─── cloneEnvironment ───

    @Test
    void cloneEnvironment_201() throws Exception {
        CloneEnvironmentRequest request = new CloneEnvironmentRequest("Production-Copy");
        when(environmentService.cloneEnvironment(eq(ENV_ID), eq(TEAM_ID), any(UUID.class), any()))
                .thenReturn(buildEnvResponse());

        mockMvc.perform(post(BASE_URL + "/" + ENV_ID + "/clone")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated());
    }

    // ─── getEnvironmentVariables ───

    @Test
    void getEnvironmentVariables_200() throws Exception {
        EnvironmentVariableResponse varResp = new EnvironmentVariableResponse(
                UUID.randomUUID(), "API_KEY", "abc123", false, true, "ENVIRONMENT");
        when(environmentService.getEnvironmentVariables(ENV_ID, TEAM_ID)).thenReturn(List.of(varResp));

        mockMvc.perform(get(BASE_URL + "/" + ENV_ID + "/variables")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].variableKey").value("API_KEY"));
    }

    // ─── saveEnvironmentVariables ───

    @Test
    void saveEnvironmentVariables_200() throws Exception {
        SaveEnvironmentVariablesRequest.VariableEntry entry =
                new SaveEnvironmentVariablesRequest.VariableEntry("API_KEY", "abc123", false, true);
        SaveEnvironmentVariablesRequest request = new SaveEnvironmentVariablesRequest(List.of(entry));
        EnvironmentVariableResponse varResp = new EnvironmentVariableResponse(
                UUID.randomUUID(), "API_KEY", "abc123", false, true, "ENVIRONMENT");
        when(environmentService.saveEnvironmentVariables(eq(ENV_ID), eq(TEAM_ID), any()))
                .thenReturn(List.of(varResp));

        mockMvc.perform(put(BASE_URL + "/" + ENV_ID + "/variables")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].variableKey").value("API_KEY"));
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

    private EnvironmentResponse buildEnvResponse() {
        return new EnvironmentResponse(ENV_ID, TEAM_ID, "Production", "Prod env",
                true, USER_ID, 5, Instant.now(), Instant.now());
    }
}
