package com.codeops.courier.controller;

import com.codeops.courier.dto.request.*;
import com.codeops.courier.dto.response.*;
import com.codeops.courier.entity.enums.*;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.security.JwtAuthFilter;
import com.codeops.courier.security.JwtTokenValidator;
import com.codeops.courier.security.RateLimitFilter;
import com.codeops.courier.config.RequestCorrelationFilter;
import com.codeops.courier.service.RequestProxyService;
import com.codeops.courier.service.RequestService;
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
import java.util.Map;
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
 * Controller-layer tests for {@link RequestController}.
 *
 * <p>Uses {@code @WebMvcTest} slice with a lightweight security configuration
 * that disables CSRF and requires authentication on all endpoints. Security
 * filter beans (JWT, rate-limit, correlation) are mocked to prevent the real
 * filter chain from loading.</p>
 */
@WebMvcTest(RequestController.class)
@Import(RequestControllerTest.TestSecurityConfig.class)
class RequestControllerTest {

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

    @MockBean RequestService requestService;
    @MockBean RequestProxyService requestProxyService;

    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean RequestCorrelationFilter requestCorrelationFilter;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID FOLDER_ID = UUID.randomUUID();
    private static final String BASE_URL = "/api/v1/courier/requests";

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "test@test.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── createRequest ───

    @Test
    void createRequest_201() throws Exception {
        CreateRequestRequest request = new CreateRequestRequest(FOLDER_ID, "Get Users",
                null, HttpMethod.GET, "https://api.example.com/users", null);
        when(requestService.createRequest(eq(TEAM_ID), any())).thenReturn(buildRequestResponse());

        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Get Users"));
    }

    @Test
    void createRequest_invalidBody_400() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── getRequest ───

    @Test
    void getRequest_200() throws Exception {
        when(requestService.getRequest(REQUEST_ID, TEAM_ID)).thenReturn(buildRequestResponse());

        mockMvc.perform(get(BASE_URL + "/" + REQUEST_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REQUEST_ID.toString()));
    }

    @Test
    void getRequest_notFound_404() throws Exception {
        when(requestService.getRequest(REQUEST_ID, TEAM_ID)).thenThrow(new NotFoundException("Request not found"));

        mockMvc.perform(get(BASE_URL + "/" + REQUEST_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── updateRequest ───

    @Test
    void updateRequest_200() throws Exception {
        UpdateRequestRequest request = new UpdateRequestRequest("Updated", null, null, null, null);
        when(requestService.updateRequest(eq(REQUEST_ID), eq(TEAM_ID), any())).thenReturn(buildRequestResponse());

        mockMvc.perform(put(BASE_URL + "/" + REQUEST_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void updateRequest_notFound_404() throws Exception {
        UpdateRequestRequest request = new UpdateRequestRequest("Updated", null, null, null, null);
        when(requestService.updateRequest(eq(REQUEST_ID), eq(TEAM_ID), any()))
                .thenThrow(new NotFoundException("Request not found"));

        mockMvc.perform(put(BASE_URL + "/" + REQUEST_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── deleteRequest ───

    @Test
    void deleteRequest_204() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + REQUEST_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNoContent());

        verify(requestService).deleteRequest(REQUEST_ID, TEAM_ID);
    }

    @Test
    void deleteRequest_notFound_404() throws Exception {
        doThrow(new NotFoundException("Request not found")).when(requestService).deleteRequest(REQUEST_ID, TEAM_ID);

        mockMvc.perform(delete(BASE_URL + "/" + REQUEST_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── duplicateRequest ───

    @Test
    void duplicateRequest_201() throws Exception {
        DuplicateRequestRequest request = new DuplicateRequestRequest(UUID.randomUUID());
        when(requestService.duplicateRequest(eq(REQUEST_ID), eq(TEAM_ID), any())).thenReturn(buildRequestResponse());

        mockMvc.perform(post(BASE_URL + "/" + REQUEST_ID + "/duplicate")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated());
    }

    // ─── moveRequest ───

    @Test
    void moveRequest_200() throws Exception {
        UUID targetFolder = UUID.randomUUID();
        when(requestService.moveRequest(REQUEST_ID, TEAM_ID, targetFolder)).thenReturn(buildRequestResponse());

        mockMvc.perform(put(BASE_URL + "/" + REQUEST_ID + "/move")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .param("targetFolderId", targetFolder.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    // ─── reorderRequests ───

    @Test
    void reorderRequests_200() throws Exception {
        ReorderRequestRequest request = new ReorderRequestRequest(List.of(REQUEST_ID, UUID.randomUUID()));
        RequestSummaryResponse summary = new RequestSummaryResponse(REQUEST_ID,
                "Get Users", HttpMethod.GET, "https://api.example.com/users", 0);
        when(requestService.reorderRequests(eq(TEAM_ID), any())).thenReturn(List.of(summary));

        mockMvc.perform(put(BASE_URL + "/reorder")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ─── saveHeaders ───

    @Test
    void saveHeaders_200() throws Exception {
        SaveRequestHeadersRequest.RequestHeaderEntry entry =
                new SaveRequestHeadersRequest.RequestHeaderEntry("Content-Type", "application/json", null, true);
        SaveRequestHeadersRequest request = new SaveRequestHeadersRequest(List.of(entry));
        RequestHeaderResponse headerResp = new RequestHeaderResponse(UUID.randomUUID(),
                "Content-Type", "application/json", null, true);
        when(requestService.saveHeaders(eq(REQUEST_ID), eq(TEAM_ID), any())).thenReturn(List.of(headerResp));

        mockMvc.perform(put(BASE_URL + "/" + REQUEST_ID + "/headers")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].headerKey").value("Content-Type"));
    }

    // ─── saveParams ───

    @Test
    void saveParams_200() throws Exception {
        SaveRequestParamsRequest.RequestParamEntry entry =
                new SaveRequestParamsRequest.RequestParamEntry("page", "1", null, true);
        SaveRequestParamsRequest request = new SaveRequestParamsRequest(List.of(entry));
        RequestParamResponse paramResp = new RequestParamResponse(UUID.randomUUID(),
                "page", "1", null, true);
        when(requestService.saveParams(eq(REQUEST_ID), eq(TEAM_ID), any())).thenReturn(List.of(paramResp));

        mockMvc.perform(put(BASE_URL + "/" + REQUEST_ID + "/params")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paramKey").value("page"));
    }

    // ─── saveBody ───

    @Test
    void saveBody_200() throws Exception {
        SaveRequestBodyRequest request = new SaveRequestBodyRequest(BodyType.RAW_JSON,
                "{\"key\":\"value\"}", null, null, null, null);
        RequestBodyResponse bodyResp = new RequestBodyResponse(UUID.randomUUID(),
                BodyType.RAW_JSON, "{\"key\":\"value\"}", null, null, null, null);
        when(requestService.saveBody(eq(REQUEST_ID), eq(TEAM_ID), any())).thenReturn(bodyResp);

        mockMvc.perform(put(BASE_URL + "/" + REQUEST_ID + "/body")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyType").value("RAW_JSON"));
    }

    // ─── saveAuth ───

    @Test
    void saveAuth_200() throws Exception {
        SaveRequestAuthRequest request = new SaveRequestAuthRequest(AuthType.BEARER_TOKEN,
                null, null, null, "token123", null, null,
                null, null, null, null, null, null, null, null,
                null, null, null);
        RequestAuthResponse authResp = new RequestAuthResponse(UUID.randomUUID(),
                AuthType.BEARER_TOKEN, null, null, null, "token123", null, null,
                null, null, null, null, null, null, null, null,
                null, null, null);
        when(requestService.saveAuth(eq(REQUEST_ID), eq(TEAM_ID), any())).thenReturn(authResp);

        mockMvc.perform(put(BASE_URL + "/" + REQUEST_ID + "/auth")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authType").value("BEARER_TOKEN"));
    }

    // ─── saveScript ───

    @Test
    void saveScript_200() throws Exception {
        SaveRequestScriptRequest request = new SaveRequestScriptRequest(ScriptType.PRE_REQUEST,
                "console.log('pre');");
        RequestScriptResponse scriptResp = new RequestScriptResponse(UUID.randomUUID(),
                ScriptType.PRE_REQUEST, "console.log('pre');");
        when(requestService.saveScript(eq(REQUEST_ID), eq(TEAM_ID), any())).thenReturn(scriptResp);

        mockMvc.perform(put(BASE_URL + "/" + REQUEST_ID + "/scripts")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scriptType").value("PRE_REQUEST"));
    }

    // ─── sendRequest ───

    @Test
    void sendRequest_200() throws Exception {
        ProxyResponse proxyResp = new ProxyResponse(200, "OK",
                Map.of("Content-Type", List.of("application/json")),
                "{}", 150L, 2L, "application/json", List.of(), null);
        when(requestProxyService.executeStoredRequest(eq(REQUEST_ID), eq(TEAM_ID), any(UUID.class), eq(null)))
                .thenReturn(proxyResp);

        mockMvc.perform(post(BASE_URL + "/" + REQUEST_ID + "/send")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));
    }

    @Test
    void sendRequest_notFound_404() throws Exception {
        when(requestProxyService.executeStoredRequest(eq(REQUEST_ID), eq(TEAM_ID), any(UUID.class), eq(null)))
                .thenThrow(new NotFoundException("Request not found"));

        mockMvc.perform(post(BASE_URL + "/" + REQUEST_ID + "/send")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── Security ───

    @Test
    void unauthorized_401() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + REQUEST_ID)
                        .header("X-Team-ID", TEAM_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTeamIdHeader_400() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + REQUEST_ID)
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ───

    private RequestResponse buildRequestResponse() {
        return new RequestResponse(REQUEST_ID, FOLDER_ID, "Get Users", "Fetch all users",
                HttpMethod.GET, "https://api.example.com/users", 0,
                List.of(), List.of(), null, null, List.of(),
                Instant.now(), Instant.now());
    }
}
