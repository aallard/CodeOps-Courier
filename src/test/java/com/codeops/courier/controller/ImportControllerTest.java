package com.codeops.courier.controller;

import com.codeops.courier.dto.request.ImportCollectionRequest;
import com.codeops.courier.dto.response.ImportResultResponse;
import com.codeops.courier.security.JwtAuthFilter;
import com.codeops.courier.security.JwtTokenValidator;
import com.codeops.courier.security.RateLimitFilter;
import com.codeops.courier.config.RequestCorrelationFilter;
import com.codeops.courier.service.ImportService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for {@link ImportController}.
 *
 * <p>Uses {@code @WebMvcTest} slice with a lightweight security configuration.
 * Security filter beans are disabled via {@link FilterRegistrationBean}.</p>
 */
@WebMvcTest(ImportController.class)
@Import(ImportControllerTest.TestSecurityConfig.class)
class ImportControllerTest {

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

    @MockBean ImportService importService;

    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean RequestCorrelationFilter requestCorrelationFilter;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COLLECTION_ID = UUID.randomUUID();
    private static final String BASE_URL = "/api/v1/courier/import";

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "test@test.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── importPostman ───

    @Test
    void importPostman_201() throws Exception {
        when(importService.importCollection(eq(TEAM_ID), eq(USER_ID), any(ImportCollectionRequest.class)))
                .thenReturn(buildImportResult("Postman Collection"));

        ImportCollectionRequest request = new ImportCollectionRequest("postman", "{\"info\":{\"name\":\"Test\"}}");

        mockMvc.perform(post(BASE_URL + "/postman")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.collectionId").value(COLLECTION_ID.toString()))
                .andExpect(jsonPath("$.collectionName").value("Postman Collection"))
                .andExpect(jsonPath("$.foldersImported").value(2))
                .andExpect(jsonPath("$.requestsImported").value(5));
    }

    @Test
    void importPostman_invalidBody_400() throws Exception {
        String invalidJson = """
                {"format": "", "content": ""}
                """;

        mockMvc.perform(post(BASE_URL + "/postman")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(invalidJson)
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── importOpenApi ───

    @Test
    void importOpenApi_201() throws Exception {
        when(importService.importCollection(eq(TEAM_ID), eq(USER_ID), any(ImportCollectionRequest.class)))
                .thenReturn(buildImportResult("OpenAPI Spec"));

        ImportCollectionRequest request = new ImportCollectionRequest("openapi", "openapi: 3.0.0\ninfo:\n  title: Test");

        mockMvc.perform(post(BASE_URL + "/openapi")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.collectionId").value(COLLECTION_ID.toString()))
                .andExpect(jsonPath("$.collectionName").value("OpenAPI Spec"));
    }

    // ─── importCurl ───

    @Test
    void importCurl_201() throws Exception {
        when(importService.importCollection(eq(TEAM_ID), eq(USER_ID), any(ImportCollectionRequest.class)))
                .thenReturn(buildImportResult("cURL Import"));

        ImportCollectionRequest request = new ImportCollectionRequest("curl",
                "curl -X GET https://api.example.com/users");

        mockMvc.perform(post(BASE_URL + "/curl")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.collectionId").value(COLLECTION_ID.toString()))
                .andExpect(jsonPath("$.collectionName").value("cURL Import"));
    }

    // ─── Security ───

    @Test
    void unauthorized_401() throws Exception {
        mockMvc.perform(post(BASE_URL + "/postman")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content("{\"format\":\"postman\",\"content\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTeamIdHeader_400() throws Exception {
        ImportCollectionRequest request = new ImportCollectionRequest("postman", "{\"info\":{}}");

        mockMvc.perform(post(BASE_URL + "/postman")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ───

    private ImportResultResponse buildImportResult(String name) {
        return new ImportResultResponse(COLLECTION_ID, name, 2, 5, 1, List.of());
    }
}
