package com.codeops.courier.controller;

import com.codeops.courier.dto.request.ExecuteGraphQLRequest;
import com.codeops.courier.dto.request.IntrospectGraphQLRequest;
import com.codeops.courier.dto.response.GraphQLResponse;
import com.codeops.courier.dto.response.ProxyResponse;
import com.codeops.courier.security.JwtAuthFilter;
import com.codeops.courier.security.JwtTokenValidator;
import com.codeops.courier.security.RateLimitFilter;
import com.codeops.courier.config.RequestCorrelationFilter;
import com.codeops.courier.service.GraphQLService;
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
 * Controller-layer tests for {@link GraphQLController}.
 *
 * <p>Uses {@code @WebMvcTest} slice with a lightweight security configuration.
 * Security filter beans are disabled via {@link FilterRegistrationBean}.</p>
 */
@WebMvcTest(GraphQLController.class)
@Import(GraphQLControllerTest.TestSecurityConfig.class)
class GraphQLControllerTest {

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

    @MockBean GraphQLService graphQLService;

    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean RequestCorrelationFilter requestCorrelationFilter;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String BASE_URL = "/api/v1/courier/graphql";

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "test@test.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── executeQuery ───

    @Test
    void executeQuery_200() throws Exception {
        GraphQLResponse response = buildGraphQLResponse();
        when(graphQLService.executeQuery(any(ExecuteGraphQLRequest.class), eq(TEAM_ID), eq(USER_ID)))
                .thenReturn(response);

        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql", "{ users { id name } }",
                null, null, List.of(), null, null);

        mockMvc.perform(post(BASE_URL + "/execute")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpResponse.statusCode").value(200))
                .andExpect(jsonPath("$.schema").doesNotExist());
    }

    @Test
    void executeQuery_invalidBody_400() throws Exception {
        String invalidJson = """
                {"url": "", "query": ""}
                """;

        mockMvc.perform(post(BASE_URL + "/execute")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(invalidJson)
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── introspect ───

    @Test
    void introspect_200() throws Exception {
        ProxyResponse httpResponse = new ProxyResponse(200, "OK",
                Map.of("Content-Type", List.of("application/json")),
                "{\"data\":{\"__schema\":{}}}", 200L, 1024L, "application/json", List.of(), UUID.randomUUID());
        GraphQLResponse response = new GraphQLResponse(httpResponse, "{\"__schema\":{}}");
        when(graphQLService.introspect(any(IntrospectGraphQLRequest.class), eq(TEAM_ID), eq(USER_ID)))
                .thenReturn(response);

        IntrospectGraphQLRequest request = new IntrospectGraphQLRequest(
                "https://api.example.com/graphql", List.of(), null);

        mockMvc.perform(post(BASE_URL + "/introspect")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema").value("{\"__schema\":{}}"));
    }

    // ─── validateQuery ───

    @Test
    void validateQuery_valid_200() throws Exception {
        when(graphQLService.validateQuery("{ users { id } }")).thenReturn(List.of());

        mockMvc.perform(post(BASE_URL + "/validate")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", "{ users { id } }")))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void validateQuery_withErrors_200() throws Exception {
        when(graphQLService.validateQuery("{ invalid")).thenReturn(List.of("Syntax error at position 9"));

        mockMvc.perform(post(BASE_URL + "/validate")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", "{ invalid")))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Syntax error at position 9"));
    }

    // ─── formatQuery ───

    @Test
    void formatQuery_200() throws Exception {
        when(graphQLService.formatQuery("{ users{id name}}"))
                .thenReturn("{\n  users {\n    id\n    name\n  }\n}");

        mockMvc.perform(post(BASE_URL + "/format")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", "{ users{id name}}")))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("{\n  users {\n    id\n    name\n  }\n}"));
    }

    // ─── Security ───

    @Test
    void unauthorized_401() throws Exception {
        mockMvc.perform(post(BASE_URL + "/execute")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTeamIdHeader_400() throws Exception {
        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql", "{ users { id } }",
                null, null, List.of(), null, null);

        mockMvc.perform(post(BASE_URL + "/execute")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ───

    private GraphQLResponse buildGraphQLResponse() {
        ProxyResponse httpResponse = new ProxyResponse(200, "OK",
                Map.of("Content-Type", List.of("application/json")),
                "{\"data\":{\"users\":[]}}", 150L, 64L, "application/json", List.of(), UUID.randomUUID());
        return new GraphQLResponse(httpResponse, null);
    }
}
