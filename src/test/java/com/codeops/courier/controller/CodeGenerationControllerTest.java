package com.codeops.courier.controller;

import com.codeops.courier.dto.request.GenerateCodeRequest;
import com.codeops.courier.dto.response.CodeSnippetResponse;
import com.codeops.courier.entity.enums.CodeLanguage;
import com.codeops.courier.security.JwtAuthFilter;
import com.codeops.courier.security.JwtTokenValidator;
import com.codeops.courier.security.RateLimitFilter;
import com.codeops.courier.config.RequestCorrelationFilter;
import com.codeops.courier.service.CodeGenerationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for {@link CodeGenerationController}.
 *
 * <p>Uses {@code @WebMvcTest} slice with a lightweight security configuration.
 * Security filter beans are disabled via {@link FilterRegistrationBean}.</p>
 */
@WebMvcTest(CodeGenerationController.class)
@Import(CodeGenerationControllerTest.TestSecurityConfig.class)
class CodeGenerationControllerTest {

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

    @MockBean CodeGenerationService codeGenerationService;

    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean RequestCorrelationFilter requestCorrelationFilter;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String BASE_URL = "/api/v1/courier/codegen";

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "test@test.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── generate ───

    @Test
    void generate_200() throws Exception {
        CodeSnippetResponse snippet = new CodeSnippetResponse(
                CodeLanguage.CURL, "cURL", "curl -X GET https://api.example.com/users", ".sh", "text/plain");
        when(codeGenerationService.generateCode(any(GenerateCodeRequest.class), eq(TEAM_ID)))
                .thenReturn(snippet);

        GenerateCodeRequest request = new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, null);

        mockMvc.perform(post(BASE_URL + "/generate")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("CURL"))
                .andExpect(jsonPath("$.displayName").value("cURL"))
                .andExpect(jsonPath("$.code").isNotEmpty());
    }

    @Test
    void generate_invalidBody_400() throws Exception {
        String invalidJson = """
                {"requestId": null, "language": null}
                """;

        mockMvc.perform(post(BASE_URL + "/generate")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(invalidJson)
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── generateAll ───

    @Test
    void generateAll_200() throws Exception {
        CodeSnippetResponse curlSnippet = new CodeSnippetResponse(
                CodeLanguage.CURL, "cURL", "curl ...", ".sh", "text/plain");
        CodeSnippetResponse pythonSnippet = new CodeSnippetResponse(
                CodeLanguage.PYTHON_REQUESTS, "Python Requests", "import requests...", ".py", "text/x-python");

        when(codeGenerationService.generateCode(any(GenerateCodeRequest.class), eq(TEAM_ID)))
                .thenReturn(curlSnippet, pythonSnippet,
                        curlSnippet, curlSnippet, curlSnippet, curlSnippet,
                        curlSnippet, curlSnippet, curlSnippet, curlSnippet, curlSnippet, curlSnippet);

        GenerateCodeRequest request = new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, null);

        mockMvc.perform(post(BASE_URL + "/generate/all")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(CodeLanguage.values().length));
    }

    // ─── getLanguages ───

    @Test
    void getLanguages_200() throws Exception {
        List<CodeSnippetResponse> languages = List.of(
                new CodeSnippetResponse(CodeLanguage.CURL, "cURL", null, ".sh", "text/plain"),
                new CodeSnippetResponse(CodeLanguage.PYTHON_REQUESTS, "Python Requests", null, ".py", "text/x-python"));
        when(codeGenerationService.getAvailableLanguages()).thenReturn(languages);

        mockMvc.perform(get(BASE_URL + "/languages")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].language").value("CURL"))
                .andExpect(jsonPath("$[1].language").value("PYTHON_REQUESTS"));
    }

    // ─── Security ───

    @Test
    void unauthorized_401() throws Exception {
        mockMvc.perform(post(BASE_URL + "/generate")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTeamIdHeader_400() throws Exception {
        GenerateCodeRequest request = new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, null);

        mockMvc.perform(post(BASE_URL + "/generate")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }
}
