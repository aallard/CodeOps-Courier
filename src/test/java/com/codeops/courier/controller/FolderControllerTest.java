package com.codeops.courier.controller;

import com.codeops.courier.dto.request.CreateFolderRequest;
import com.codeops.courier.dto.request.ReorderFolderRequest;
import com.codeops.courier.dto.request.UpdateFolderRequest;
import com.codeops.courier.dto.response.FolderResponse;
import com.codeops.courier.dto.response.RequestSummaryResponse;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.security.JwtAuthFilter;
import com.codeops.courier.security.JwtTokenValidator;
import com.codeops.courier.security.RateLimitFilter;
import com.codeops.courier.config.RequestCorrelationFilter;
import com.codeops.courier.service.FolderService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FolderController.class)
@Import(FolderControllerTest.TestSecurityConfig.class)
class FolderControllerTest {

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

    @MockBean FolderService folderService;
    @MockBean RequestService requestService;

    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean RequestCorrelationFilter requestCorrelationFilter;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FOLDER_ID = UUID.randomUUID();
    private static final UUID COLLECTION_ID = UUID.randomUUID();
    private static final String BASE_URL = "/api/v1/courier/folders";

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "test@test.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── createFolder ───

    @Test
    void createFolder_201() throws Exception {
        CreateFolderRequest request = new CreateFolderRequest(COLLECTION_ID, null, "Users", null, null);
        when(folderService.createFolder(eq(TEAM_ID), any())).thenReturn(buildFolderResponse());

        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Users"));
    }

    @Test
    void createFolder_invalidBody_400() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFolder_missingName_400() throws Exception {
        CreateFolderRequest request = new CreateFolderRequest(COLLECTION_ID, null, null, null, null);

        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── getFolder ───

    @Test
    void getFolder_200() throws Exception {
        when(folderService.getFolder(FOLDER_ID, TEAM_ID)).thenReturn(buildFolderResponse());

        mockMvc.perform(get(BASE_URL + "/" + FOLDER_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(FOLDER_ID.toString()));
    }

    @Test
    void getFolder_notFound_404() throws Exception {
        when(folderService.getFolder(FOLDER_ID, TEAM_ID)).thenThrow(new NotFoundException("Folder not found"));

        mockMvc.perform(get(BASE_URL + "/" + FOLDER_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── getSubFolders ───

    @Test
    void getSubFolders_200() throws Exception {
        when(folderService.getSubFolders(FOLDER_ID, TEAM_ID)).thenReturn(List.of(buildFolderResponse()));

        mockMvc.perform(get(BASE_URL + "/" + FOLDER_ID + "/subfolders")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Users"));
    }

    @Test
    void getSubFolders_empty_200() throws Exception {
        when(folderService.getSubFolders(FOLDER_ID, TEAM_ID)).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/" + FOLDER_ID + "/subfolders")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── getRequestsInFolder ───

    @Test
    void getRequestsInFolder_200() throws Exception {
        RequestSummaryResponse summary = new RequestSummaryResponse(UUID.randomUUID(),
                "Get Users", HttpMethod.GET, "https://api.example.com/users", 0);
        when(requestService.getRequestsInFolder(FOLDER_ID, TEAM_ID)).thenReturn(List.of(summary));

        mockMvc.perform(get(BASE_URL + "/" + FOLDER_ID + "/requests")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Get Users"));
    }

    @Test
    void getRequestsInFolder_empty_200() throws Exception {
        when(requestService.getRequestsInFolder(FOLDER_ID, TEAM_ID)).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/" + FOLDER_ID + "/requests")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── updateFolder ───

    @Test
    void updateFolder_200() throws Exception {
        UpdateFolderRequest request = new UpdateFolderRequest("Updated", null, null, null, null, null, null, null);
        when(folderService.updateFolder(eq(FOLDER_ID), eq(TEAM_ID), any())).thenReturn(buildFolderResponse());

        mockMvc.perform(put(BASE_URL + "/" + FOLDER_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void updateFolder_notFound_404() throws Exception {
        UpdateFolderRequest request = new UpdateFolderRequest("Updated", null, null, null, null, null, null, null);
        when(folderService.updateFolder(eq(FOLDER_ID), eq(TEAM_ID), any()))
                .thenThrow(new NotFoundException("Folder not found"));

        mockMvc.perform(put(BASE_URL + "/" + FOLDER_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── deleteFolder ───

    @Test
    void deleteFolder_204() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + FOLDER_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNoContent());

        verify(folderService).deleteFolder(FOLDER_ID, TEAM_ID);
    }

    @Test
    void deleteFolder_notFound_404() throws Exception {
        doThrow(new NotFoundException("Folder not found")).when(folderService).deleteFolder(FOLDER_ID, TEAM_ID);

        mockMvc.perform(delete(BASE_URL + "/" + FOLDER_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── moveFolder ───

    @Test
    void moveFolder_toNewParent_200() throws Exception {
        UUID newParent = UUID.randomUUID();
        when(folderService.moveFolder(FOLDER_ID, TEAM_ID, newParent)).thenReturn(buildFolderResponse());

        mockMvc.perform(put(BASE_URL + "/" + FOLDER_ID + "/move")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .param("newParentFolderId", newParent.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void moveFolder_toRoot_200() throws Exception {
        when(folderService.moveFolder(eq(FOLDER_ID), eq(TEAM_ID), eq(null))).thenReturn(buildFolderResponse());

        mockMvc.perform(put(BASE_URL + "/" + FOLDER_ID + "/move")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void moveFolder_notFound_404() throws Exception {
        when(folderService.moveFolder(eq(FOLDER_ID), eq(TEAM_ID), any()))
                .thenThrow(new NotFoundException("Folder not found"));

        mockMvc.perform(put(BASE_URL + "/" + FOLDER_ID + "/move")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── reorderFolders ───

    @Test
    void reorderFolders_200() throws Exception {
        ReorderFolderRequest request = new ReorderFolderRequest(List.of(FOLDER_ID, UUID.randomUUID()));
        when(folderService.reorderFolders(eq(TEAM_ID), any())).thenReturn(List.of(buildFolderResponse()));

        mockMvc.perform(put(BASE_URL + "/reorder")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ─── Security ───

    @Test
    void unauthorized_401() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + FOLDER_ID)
                        .header("X-Team-ID", TEAM_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTeamIdHeader_400() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + FOLDER_ID)
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ───

    private FolderResponse buildFolderResponse() {
        return new FolderResponse(FOLDER_ID, COLLECTION_ID, null, "Users", "User endpoints",
                0, null, null, AuthType.NO_AUTH, null, 0, 3, Instant.now(), Instant.now());
    }
}
