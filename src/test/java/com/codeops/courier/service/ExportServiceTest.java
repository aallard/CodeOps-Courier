package com.codeops.courier.service;

import com.codeops.courier.dto.response.ExportCollectionResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.EnvironmentVariable;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.RequestAuth;
import com.codeops.courier.entity.RequestBody;
import com.codeops.courier.entity.RequestHeader;
import com.codeops.courier.entity.RequestParam;
import com.codeops.courier.entity.RequestScript;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.entity.enums.ScriptType;
import com.codeops.courier.repository.FolderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ExportService covering Postman, OpenAPI, and native export formats.
 */
@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private CollectionService collectionService;

    @Mock
    private FolderRepository folderRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ExportService exportService;

    private UUID teamId;
    private UUID collectionId;
    private Collection collection;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        collectionId = UUID.randomUUID();

        collection = new Collection();
        collection.setId(collectionId);
        collection.setTeamId(teamId);
        collection.setName("Test API Collection");
        collection.setDescription("A test collection");
        collection.setCreatedBy(UUID.randomUUID());
        collection.setVariables(new ArrayList<>());
        collection.setCreatedAt(Instant.now());
        collection.setUpdatedAt(Instant.now());
    }

    @Test
    void exportAsPostman_success_validJson() {
        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of());

        ExportCollectionResponse response = exportService.exportAsPostman(collectionId, teamId);

        assertThat(response).isNotNull();
        assertThat(response.format()).isEqualTo("POSTMAN_V2_1");
        assertThat(response.content()).contains("\"schema\"");
        assertThat(response.content()).contains("collection/v2.1.0");
        assertThat(response.content()).contains("Test API Collection");
        assertThat(response.filename()).endsWith(".postman_collection.json");
    }

    @Test
    void exportAsPostman_includesFolders() {
        Folder folder = createFolder("Users", 0);

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(folder));

        ExportCollectionResponse response = exportService.exportAsPostman(collectionId, teamId);

        assertThat(response.content()).contains("\"Users\"");
        assertThat(response.content()).contains("\"item\"");
    }

    @Test
    void exportAsPostman_includesRequests() {
        Request request = createRequest("Get Users", HttpMethod.GET, "https://api.test.com/users");

        Folder folder = createFolder("Users", 0);
        folder.setRequests(List.of(request));

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(folder));

        ExportCollectionResponse response = exportService.exportAsPostman(collectionId, teamId);

        assertThat(response.content()).contains("\"Get Users\"");
        assertThat(response.content()).contains("\"GET\"");
        assertThat(response.content()).contains("https://api.test.com/users");
    }

    @Test
    void exportAsPostman_includesAuth() {
        collection.setAuthType(AuthType.BEARER_TOKEN);

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of());

        ExportCollectionResponse response = exportService.exportAsPostman(collectionId, teamId);

        assertThat(response.content()).contains("\"bearer\"");
        assertThat(response.content()).contains("\"auth\"");
    }

    @Test
    void exportAsPostman_includesScripts() {
        RequestScript preScript = new RequestScript();
        preScript.setScriptType(ScriptType.PRE_REQUEST);
        preScript.setContent("console.log('pre-request')");

        RequestScript postScript = new RequestScript();
        postScript.setScriptType(ScriptType.POST_RESPONSE);
        postScript.setContent("pm.test('status 200')");

        Request request = createRequest("Test Req", HttpMethod.GET, "https://api.test.com");
        request.setScripts(new ArrayList<>(List.of(preScript, postScript)));

        Folder folder = createFolder("Scripts", 0);
        folder.setRequests(List.of(request));

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(folder));

        ExportCollectionResponse response = exportService.exportAsPostman(collectionId, teamId);

        assertThat(response.content()).contains("prerequest");
        assertThat(response.content()).contains("test");
        assertThat(response.content()).contains("console.log");
    }

    @Test
    void exportAsPostman_emptyCollection() {
        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of());

        ExportCollectionResponse response = exportService.exportAsPostman(collectionId, teamId);

        assertThat(response.content()).contains("\"item\" : [ ]");
        assertThat(response.format()).isEqualTo("POSTMAN_V2_1");
    }

    @Test
    void exportAsPostman_includesVariables() {
        EnvironmentVariable var = new EnvironmentVariable();
        var.setVariableKey("base_url");
        var.setVariableValue("https://api.test.com");
        var.setEnabled(true);
        var.setSecret(false);
        var.setScope("collection");

        collection.setVariables(new ArrayList<>(List.of(var)));

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of());

        ExportCollectionResponse response = exportService.exportAsPostman(collectionId, teamId);

        assertThat(response.content()).contains("\"base_url\"");
        assertThat(response.content()).contains("\"variable\"");
    }

    @Test
    void exportAsOpenApi_success_validYaml() {
        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdOrderBySortOrder(collectionId))
                .thenReturn(List.of());

        ExportCollectionResponse response = exportService.exportAsOpenApi(collectionId, teamId);

        assertThat(response).isNotNull();
        assertThat(response.format()).isEqualTo("OPENAPI_3");
        assertThat(response.content()).contains("openapi: 3.0.3");
        assertThat(response.content()).contains("Test API Collection");
        assertThat(response.content()).contains("version: 1.0.0");
        assertThat(response.filename()).endsWith(".openapi.yaml");
    }

    @Test
    void exportAsOpenApi_groupsByPath() {
        Request getReq = createRequest("Get Users", HttpMethod.GET, "https://api.test.com/users");
        Request postReq = createRequest("Create User", HttpMethod.POST, "https://api.test.com/users");

        Folder folder = createFolder("Users", 0);
        folder.setRequests(List.of(getReq, postReq));

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdOrderBySortOrder(collectionId))
                .thenReturn(List.of(folder));

        ExportCollectionResponse response = exportService.exportAsOpenApi(collectionId, teamId);

        assertThat(response.content()).contains("/users");
        assertThat(response.content()).contains("get:");
        assertThat(response.content()).contains("post:");
    }

    @Test
    void exportAsOpenApi_includesSecurityScheme() {
        collection.setAuthType(AuthType.BEARER_TOKEN);

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdOrderBySortOrder(collectionId))
                .thenReturn(List.of());

        ExportCollectionResponse response = exportService.exportAsOpenApi(collectionId, teamId);

        assertThat(response.content()).contains("securitySchemes");
        assertThat(response.content()).contains("bearerAuth");
        assertThat(response.content()).contains("scheme: bearer");
    }

    @Test
    void exportAsOpenApi_emptyPaths() {
        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdOrderBySortOrder(collectionId))
                .thenReturn(List.of());

        ExportCollectionResponse response = exportService.exportAsOpenApi(collectionId, teamId);

        assertThat(response.content()).contains("paths: {}");
    }

    @Test
    void exportAsNative_success() {
        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of());

        ExportCollectionResponse response = exportService.exportAsNative(collectionId, teamId);

        assertThat(response).isNotNull();
        assertThat(response.format()).isEqualTo("COURIER_NATIVE");
        assertThat(response.content()).contains("COURIER_NATIVE");
        assertThat(response.content()).contains("Test API Collection");
        assertThat(response.filename()).endsWith(".courier.json");
    }

    @Test
    void exportAsNative_includesAllData() {
        RequestHeader header = new RequestHeader();
        header.setHeaderKey("Authorization");
        header.setHeaderValue("Bearer token");
        header.setEnabled(true);

        RequestParam param = new RequestParam();
        param.setParamKey("page");
        param.setParamValue("1");
        param.setEnabled(true);

        RequestBody body = new RequestBody();
        body.setBodyType(BodyType.RAW_JSON);
        body.setRawContent("{\"name\": \"test\"}");

        RequestAuth auth = new RequestAuth();
        auth.setAuthType(AuthType.BEARER_TOKEN);
        auth.setBearerToken("my-token");

        RequestScript script = new RequestScript();
        script.setScriptType(ScriptType.PRE_REQUEST);
        script.setContent("console.log('test')");

        Request request = new Request();
        request.setId(UUID.randomUUID());
        request.setName("Create User");
        request.setMethod(HttpMethod.POST);
        request.setUrl("https://api.test.com/users");
        request.setSortOrder(0);
        request.setHeaders(new ArrayList<>(List.of(header)));
        request.setParams(new ArrayList<>(List.of(param)));
        request.setBody(body);
        request.setAuth(auth);
        request.setScripts(new ArrayList<>(List.of(script)));

        Folder folder = createFolder("Users", 0);
        folder.setRequests(List.of(request));

        EnvironmentVariable var = new EnvironmentVariable();
        var.setVariableKey("base_url");
        var.setVariableValue("https://api.test.com");
        var.setSecret(false);
        var.setEnabled(true);
        var.setScope("collection");
        collection.setVariables(new ArrayList<>(List.of(var)));

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(folder));

        ExportCollectionResponse response = exportService.exportAsNative(collectionId, teamId);

        assertThat(response.content()).contains("Create User");
        assertThat(response.content()).contains("POST");
        assertThat(response.content()).contains("Authorization");
        assertThat(response.content()).contains("page");
        assertThat(response.content()).contains("RAW_JSON");
        assertThat(response.content()).contains("BEARER_TOKEN");
        assertThat(response.content()).contains("PRE_REQUEST");
        assertThat(response.content()).contains("base_url");
    }

    @Test
    void exportAsPostman_includesHeadersAndParams() {
        RequestHeader header = new RequestHeader();
        header.setHeaderKey("Content-Type");
        header.setHeaderValue("application/json");
        header.setEnabled(true);

        RequestParam param = new RequestParam();
        param.setParamKey("limit");
        param.setParamValue("10");
        param.setDescription("Limit results");
        param.setEnabled(true);

        Request request = createRequest("List Items", HttpMethod.GET, "https://api.test.com/items");
        request.setHeaders(new ArrayList<>(List.of(header)));
        request.setParams(new ArrayList<>(List.of(param)));

        Folder folder = createFolder("Items", 0);
        folder.setRequests(List.of(request));

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(folder));

        ExportCollectionResponse response = exportService.exportAsPostman(collectionId, teamId);

        assertThat(response.content()).contains("Content-Type");
        assertThat(response.content()).contains("limit");
    }

    @Test
    void exportAsPostman_includesRequestBody() {
        RequestBody body = new RequestBody();
        body.setBodyType(BodyType.RAW_JSON);
        body.setRawContent("{\"name\": \"test\"}");

        Request request = createRequest("Create Item", HttpMethod.POST, "https://api.test.com/items");
        request.setBody(body);

        Folder folder = createFolder("Items", 0);
        folder.setRequests(List.of(request));

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(folder));

        ExportCollectionResponse response = exportService.exportAsPostman(collectionId, teamId);

        assertThat(response.content()).contains("\"raw\"");
        assertThat(response.content()).contains("\"json\"");
    }

    @Test
    void exportAsNative_includesSubFolders() {
        Folder childFolder = createFolder("Child", 0);

        Folder parentFolder = createFolder("Parent", 0);
        parentFolder.setSubFolders(List.of(childFolder));

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(collection);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(parentFolder));

        ExportCollectionResponse response = exportService.exportAsNative(collectionId, teamId);

        assertThat(response.content()).contains("Parent");
        assertThat(response.content()).contains("Child");
        assertThat(response.content()).contains("subFolders");
    }

    private Folder createFolder(String name, int sortOrder) {
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName(name);
        folder.setSortOrder(sortOrder);
        folder.setCollection(collection);
        folder.setRequests(new ArrayList<>());
        folder.setSubFolders(new ArrayList<>());
        return folder;
    }

    private Request createRequest(String name, HttpMethod method, String url) {
        Request request = new Request();
        request.setId(UUID.randomUUID());
        request.setName(name);
        request.setMethod(method);
        request.setUrl(url);
        request.setSortOrder(0);
        request.setHeaders(new ArrayList<>());
        request.setParams(new ArrayList<>());
        request.setScripts(new ArrayList<>());
        return request;
    }
}
