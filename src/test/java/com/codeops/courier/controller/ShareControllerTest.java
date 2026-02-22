package com.codeops.courier.controller;

import com.codeops.courier.dto.request.ShareCollectionRequest;
import com.codeops.courier.dto.request.UpdateSharePermissionRequest;
import com.codeops.courier.dto.response.CollectionShareResponse;
import com.codeops.courier.entity.enums.SharePermission;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.security.JwtAuthFilter;
import com.codeops.courier.security.JwtTokenValidator;
import com.codeops.courier.security.RateLimitFilter;
import com.codeops.courier.config.RequestCorrelationFilter;
import com.codeops.courier.service.ShareService;
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
 * Controller-layer tests for {@link ShareController}.
 *
 * <p>Uses {@code @WebMvcTest} slice with a lightweight security configuration.
 * Security filter beans are disabled via {@link FilterRegistrationBean}.</p>
 */
@WebMvcTest(ShareController.class)
@Import(ShareControllerTest.TestSecurityConfig.class)
class ShareControllerTest {

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

    @MockBean ShareService shareService;

    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean RequestCorrelationFilter requestCorrelationFilter;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COLLECTION_ID = UUID.randomUUID();
    private static final UUID SHARED_WITH_USER_ID = UUID.randomUUID();
    private static final String SHARES_URL = "/api/v1/courier/collections/" + COLLECTION_ID + "/shares";

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "test@test.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── shareCollection ───

    @Test
    void shareCollection_201() throws Exception {
        ShareCollectionRequest request = new ShareCollectionRequest(SHARED_WITH_USER_ID, SharePermission.EDITOR);
        when(shareService.shareCollection(eq(COLLECTION_ID), eq(TEAM_ID), any(UUID.class), any()))
                .thenReturn(buildShareResponse());

        mockMvc.perform(post(SHARES_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permission").value("EDITOR"));
    }

    @Test
    void shareCollection_invalidBody_400() throws Exception {
        mockMvc.perform(post(SHARES_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shareCollection_alreadyShared_400() throws Exception {
        ShareCollectionRequest request = new ShareCollectionRequest(SHARED_WITH_USER_ID, SharePermission.EDITOR);
        when(shareService.shareCollection(eq(COLLECTION_ID), eq(TEAM_ID), any(UUID.class), any()))
                .thenThrow(new ValidationException("Already shared with this user"));

        mockMvc.perform(post(SHARES_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shareCollection_collectionNotFound_404() throws Exception {
        ShareCollectionRequest request = new ShareCollectionRequest(SHARED_WITH_USER_ID, SharePermission.VIEWER);
        when(shareService.shareCollection(eq(COLLECTION_ID), eq(TEAM_ID), any(UUID.class), any()))
                .thenThrow(new NotFoundException("Collection not found"));

        mockMvc.perform(post(SHARES_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── getCollectionShares ───

    @Test
    void getCollectionShares_200() throws Exception {
        when(shareService.getCollectionShares(COLLECTION_ID, TEAM_ID)).thenReturn(List.of(buildShareResponse()));

        mockMvc.perform(get(SHARES_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].permission").value("EDITOR"));
    }

    @Test
    void getCollectionShares_empty_200() throws Exception {
        when(shareService.getCollectionShares(COLLECTION_ID, TEAM_ID)).thenReturn(List.of());

        mockMvc.perform(get(SHARES_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── updateSharePermission ───

    @Test
    void updateSharePermission_200() throws Exception {
        UpdateSharePermissionRequest request = new UpdateSharePermissionRequest(SharePermission.ADMIN);
        when(shareService.updateSharePermission(eq(COLLECTION_ID), eq(SHARED_WITH_USER_ID), eq(TEAM_ID), any()))
                .thenReturn(buildShareResponse());

        mockMvc.perform(put(SHARES_URL + "/" + SHARED_WITH_USER_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void updateSharePermission_notFound_404() throws Exception {
        UpdateSharePermissionRequest request = new UpdateSharePermissionRequest(SharePermission.ADMIN);
        when(shareService.updateSharePermission(eq(COLLECTION_ID), eq(SHARED_WITH_USER_ID), eq(TEAM_ID), any()))
                .thenThrow(new NotFoundException("Share not found"));

        mockMvc.perform(put(SHARES_URL + "/" + SHARED_WITH_USER_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── revokeShare ───

    @Test
    void revokeShare_204() throws Exception {
        mockMvc.perform(delete(SHARES_URL + "/" + SHARED_WITH_USER_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNoContent());

        verify(shareService).revokeShare(COLLECTION_ID, SHARED_WITH_USER_ID, TEAM_ID);
    }

    @Test
    void revokeShare_notFound_404() throws Exception {
        doThrow(new NotFoundException("Share not found"))
                .when(shareService).revokeShare(COLLECTION_ID, SHARED_WITH_USER_ID, TEAM_ID);

        mockMvc.perform(delete(SHARES_URL + "/" + SHARED_WITH_USER_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── getSharedWithMe ───

    @Test
    void getSharedWithMe_200() throws Exception {
        when(shareService.getSharedWithUser(any(UUID.class))).thenReturn(List.of(buildShareResponse()));

        mockMvc.perform(get("/api/v1/courier/shared-with-me")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ─── Security ───

    @Test
    void unauthorized_401() throws Exception {
        mockMvc.perform(get(SHARES_URL)
                        .header("X-Team-ID", TEAM_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    // ─── Helpers ───

    private CollectionShareResponse buildShareResponse() {
        return new CollectionShareResponse(UUID.randomUUID(), COLLECTION_ID,
                SHARED_WITH_USER_ID, USER_ID, SharePermission.EDITOR, Instant.now());
    }
}
