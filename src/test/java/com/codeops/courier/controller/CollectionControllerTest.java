package com.codeops.courier.controller;

import com.codeops.courier.dto.request.CreateCollectionRequest;
import com.codeops.courier.dto.request.CreateForkRequest;
import com.codeops.courier.dto.request.UpdateCollectionRequest;
import com.codeops.courier.dto.response.*;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.security.JwtAuthFilter;
import com.codeops.courier.security.JwtTokenValidator;
import com.codeops.courier.security.RateLimitFilter;
import com.codeops.courier.config.RequestCorrelationFilter;
import com.codeops.courier.service.*;
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

@WebMvcTest(CollectionController.class)
@Import(CollectionControllerTest.TestSecurityConfig.class)
class CollectionControllerTest {

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

    @MockBean CollectionService collectionService;
    @MockBean ForkService forkService;
    @MockBean MergeService mergeService;
    @MockBean ExportService exportService;
    @MockBean FolderService folderService;

    // Security chain mocks (not used directly but needed by component scan)
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean RequestCorrelationFilter requestCorrelationFilter;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COLLECTION_ID = UUID.randomUUID();
    private static final String BASE_URL = "/api/v1/courier/collections";

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "test@test.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── createCollection ───

    @Test
    void createCollection_201() throws Exception {
        CreateCollectionRequest request = new CreateCollectionRequest("Test API", "Description", null, null);
        CollectionResponse response = buildCollectionResponse();
        when(collectionService.createCollection(eq(TEAM_ID), eq(USER_ID), any())).thenReturn(response);

        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(COLLECTION_ID.toString()))
                .andExpect(jsonPath("$.name").value("Test API"));
    }

    @Test
    void createCollection_invalidBody_400() throws Exception {
        CreateCollectionRequest request = new CreateCollectionRequest(null, null, null, null);

        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCollection_unauthorized_401() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ─── getCollections ───

    @Test
    void getCollections_200() throws Exception {
        CollectionSummaryResponse summary = buildSummaryResponse();
        when(collectionService.getCollections(TEAM_ID, USER_ID)).thenReturn(List.of(summary));

        mockMvc.perform(get(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test API"));
    }

    @Test
    void getCollections_emptyList_200() throws Exception {
        when(collectionService.getCollections(TEAM_ID, USER_ID)).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── getCollectionsPaged ───

    @Test
    void getCollectionsPaged_200() throws Exception {
        PageResponse<CollectionSummaryResponse> page = new PageResponse<>(
                List.of(buildSummaryResponse()), 0, 20, 1, 1, true);
        when(collectionService.getCollectionsPaged(TEAM_ID, 0, 20)).thenReturn(page);

        mockMvc.perform(get(BASE_URL + "/paged")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .param("page", "0")
                        .param("size", "20")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getCollectionsPaged_defaultParams() throws Exception {
        PageResponse<CollectionSummaryResponse> page = new PageResponse<>(List.of(), 0, 20, 0, 0, true);
        when(collectionService.getCollectionsPaged(TEAM_ID, 0, 20)).thenReturn(page);

        mockMvc.perform(get(BASE_URL + "/paged")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    // ─── searchCollections ───

    @Test
    void searchCollections_200() throws Exception {
        when(collectionService.searchCollections(TEAM_ID, "test")).thenReturn(List.of(buildSummaryResponse()));

        mockMvc.perform(get(BASE_URL + "/search")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .param("query", "test")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test API"));
    }

    @Test
    void searchCollections_emptyResults_200() throws Exception {
        when(collectionService.searchCollections(TEAM_ID, "xyz")).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/search")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .param("query", "xyz")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── getCollection ───

    @Test
    void getCollection_200() throws Exception {
        when(collectionService.getCollection(COLLECTION_ID, TEAM_ID)).thenReturn(buildCollectionResponse());

        mockMvc.perform(get(BASE_URL + "/" + COLLECTION_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COLLECTION_ID.toString()));
    }

    @Test
    void getCollection_notFound_404() throws Exception {
        when(collectionService.getCollection(COLLECTION_ID, TEAM_ID))
                .thenThrow(new NotFoundException("Collection not found"));

        mockMvc.perform(get(BASE_URL + "/" + COLLECTION_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── updateCollection ───

    @Test
    void updateCollection_200() throws Exception {
        UpdateCollectionRequest request = new UpdateCollectionRequest("Updated", "Desc", null, null, null, null);
        when(collectionService.updateCollection(eq(COLLECTION_ID), eq(TEAM_ID), any())).thenReturn(buildCollectionResponse());

        mockMvc.perform(put(BASE_URL + "/" + COLLECTION_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void updateCollection_notFound_404() throws Exception {
        UpdateCollectionRequest request = new UpdateCollectionRequest("Updated", null, null, null, null, null);
        when(collectionService.updateCollection(eq(COLLECTION_ID), eq(TEAM_ID), any()))
                .thenThrow(new NotFoundException("Collection not found"));

        mockMvc.perform(put(BASE_URL + "/" + COLLECTION_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── deleteCollection ───

    @Test
    void deleteCollection_204() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + COLLECTION_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNoContent());

        verify(collectionService).deleteCollection(COLLECTION_ID, TEAM_ID);
    }

    @Test
    void deleteCollection_notFound_404() throws Exception {
        doThrow(new NotFoundException("Collection not found"))
                .when(collectionService).deleteCollection(COLLECTION_ID, TEAM_ID);

        mockMvc.perform(delete(BASE_URL + "/" + COLLECTION_ID)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    // ─── duplicateCollection ───

    @Test
    void duplicateCollection_201() throws Exception {
        when(collectionService.duplicateCollection(COLLECTION_ID, TEAM_ID, USER_ID))
                .thenReturn(buildCollectionResponse());

        mockMvc.perform(post(BASE_URL + "/" + COLLECTION_ID + "/duplicate")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated());
    }

    // ─── forkCollection ───

    @Test
    void forkCollection_201() throws Exception {
        CreateForkRequest request = new CreateForkRequest("my-fork");
        ForkResponse forkResponse = new ForkResponse(UUID.randomUUID(), COLLECTION_ID, "Test API",
                UUID.randomUUID(), USER_ID, "my-fork", Instant.now(), Instant.now());
        when(forkService.forkCollection(eq(COLLECTION_ID), eq(TEAM_ID), eq(USER_ID), any())).thenReturn(forkResponse);

        mockMvc.perform(post(BASE_URL + "/" + COLLECTION_ID + "/fork")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("my-fork"));
    }

    // ─── getCollectionForks ───

    @Test
    void getCollectionForks_200() throws Exception {
        when(forkService.getForksForCollection(COLLECTION_ID, TEAM_ID)).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/" + COLLECTION_ID + "/forks")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ─── exportCollection ───

    @Test
    void exportCollection_postman_200() throws Exception {
        ExportCollectionResponse export = new ExportCollectionResponse("postman", "{}", "collection.json");
        when(exportService.exportAsPostman(COLLECTION_ID, TEAM_ID)).thenReturn(export);

        mockMvc.perform(get(BASE_URL + "/" + COLLECTION_ID + "/export/postman")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("postman"));
    }

    @Test
    void exportCollection_openapi_200() throws Exception {
        ExportCollectionResponse export = new ExportCollectionResponse("openapi", "{}", "openapi.yaml");
        when(exportService.exportAsOpenApi(COLLECTION_ID, TEAM_ID)).thenReturn(export);

        mockMvc.perform(get(BASE_URL + "/" + COLLECTION_ID + "/export/openapi")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("openapi"));
    }

    @Test
    void exportCollection_native_200() throws Exception {
        ExportCollectionResponse export = new ExportCollectionResponse("native", "{}", "collection.courier");
        when(exportService.exportAsNative(COLLECTION_ID, TEAM_ID)).thenReturn(export);

        mockMvc.perform(get(BASE_URL + "/" + COLLECTION_ID + "/export/native")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("native"));
    }

    @Test
    void exportCollection_invalidFormat_400() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + COLLECTION_ID + "/export/swagger")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── getCollectionTree ───

    @Test
    void getCollectionTree_200() throws Exception {
        FolderTreeResponse tree = new FolderTreeResponse(UUID.randomUUID(), "Root", 0, List.of(), List.of());
        when(folderService.getFolderTree(COLLECTION_ID, TEAM_ID)).thenReturn(List.of(tree));

        mockMvc.perform(get(BASE_URL + "/" + COLLECTION_ID + "/tree")
                        .header("X-Team-ID", TEAM_ID.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Root"));
    }

    // ─── Missing Header ───

    @Test
    void missingTeamIdHeader_400() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── createCollection Missing Name ───

    @Test
    void createCollection_missingName_400() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("X-Team-ID", TEAM_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"no name\"}")
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ───

    private CollectionResponse buildCollectionResponse() {
        return new CollectionResponse(COLLECTION_ID, TEAM_ID, "Test API", "Description",
                null, null, AuthType.NO_AUTH, null, false, USER_ID, 2, 5,
                Instant.now(), Instant.now());
    }

    private CollectionSummaryResponse buildSummaryResponse() {
        return new CollectionSummaryResponse(COLLECTION_ID, "Test API", "Description",
                false, 2, 5, Instant.now());
    }
}
