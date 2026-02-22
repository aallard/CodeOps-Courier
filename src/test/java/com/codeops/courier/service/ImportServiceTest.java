package com.codeops.courier.service;

import com.codeops.courier.dto.request.ImportCollectionRequest;
import com.codeops.courier.dto.response.ImportResultResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.CollectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private PostmanImporter postmanImporter;

    @Mock
    private OpenApiImporter openApiImporter;

    @Mock
    private CurlImporter curlImporter;

    @InjectMocks
    private ImportService service;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    // ─── Postman Import ───

    @Test
    void importCollection_postmanFormat() {
        String content = "{\"info\":{\"name\":\"Test\"},\"item\":[]}";
        ImportCollectionRequest request = new ImportCollectionRequest("postman", content);

        Collection collection = new Collection();
        collection.setId(UUID.randomUUID());
        collection.setName("Test");
        collection.setTeamId(TEAM_ID);
        collection.setFolders(new ArrayList<>());
        collection.setVariables(new ArrayList<>());

        when(postmanImporter.parse(content, TEAM_ID, USER_ID))
                .thenReturn(new PostmanImporter.PostmanImportResult(collection, 2, 5, 1, List.of()));
        when(collectionRepository.existsByTeamIdAndName(TEAM_ID, "Test")).thenReturn(false);
        when(collectionRepository.save(any(Collection.class))).thenReturn(collection);

        ImportResultResponse result = service.importCollection(TEAM_ID, USER_ID, request);

        assertThat(result.collectionName()).isEqualTo("Test");
        assertThat(result.foldersImported()).isEqualTo(2);
        assertThat(result.requestsImported()).isEqualTo(5);
        assertThat(result.environmentsImported()).isEqualTo(1);
        verify(collectionRepository).save(any(Collection.class));
    }

    // ─── OpenAPI Import ───

    @Test
    void importCollection_openapiFormat() {
        String content = "{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"API\"},\"paths\":{}}";
        ImportCollectionRequest request = new ImportCollectionRequest("openapi", content);

        Collection collection = new Collection();
        collection.setId(UUID.randomUUID());
        collection.setName("API");
        collection.setTeamId(TEAM_ID);
        collection.setFolders(new ArrayList<>());
        collection.setVariables(new ArrayList<>());

        when(openApiImporter.parse(content, false, TEAM_ID, USER_ID))
                .thenReturn(new OpenApiImporter.OpenApiImportResult(collection, 3, 10, 0, List.of()));
        when(collectionRepository.existsByTeamIdAndName(TEAM_ID, "API")).thenReturn(false);
        when(collectionRepository.save(any(Collection.class))).thenReturn(collection);

        ImportResultResponse result = service.importCollection(TEAM_ID, USER_ID, request);

        assertThat(result.collectionName()).isEqualTo("API");
        assertThat(result.foldersImported()).isEqualTo(3);
        assertThat(result.requestsImported()).isEqualTo(10);
    }

    // ─── cURL Import ───

    @Test
    void importCollection_curlFormat() {
        String content = "curl https://api.example.com/users";
        ImportCollectionRequest request = new ImportCollectionRequest("curl", content);

        Request curlRequest = new Request();
        curlRequest.setName("GET /users");
        curlRequest.setMethod(HttpMethod.GET);
        curlRequest.setUrl("https://api.example.com/users");
        curlRequest.setHeaders(new ArrayList<>());
        curlRequest.setParams(new ArrayList<>());
        curlRequest.setScripts(new ArrayList<>());

        when(curlImporter.parseCurl(eq(content), any(Folder.class), eq(0))).thenReturn(curlRequest);
        when(collectionRepository.existsByTeamIdAndName(TEAM_ID, "cURL Import")).thenReturn(false);
        when(collectionRepository.save(any(Collection.class))).thenAnswer(inv -> {
            Collection c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        ImportResultResponse result = service.importCollection(TEAM_ID, USER_ID, request);

        assertThat(result.collectionName()).isEqualTo("cURL Import");
        assertThat(result.foldersImported()).isEqualTo(1);
        assertThat(result.requestsImported()).isEqualTo(1);
    }

    // ─── Unsupported Format ───

    @Test
    void importCollection_unsupportedFormat_throws() {
        ImportCollectionRequest request = new ImportCollectionRequest("swagger", "content");

        assertThatThrownBy(() -> service.importCollection(TEAM_ID, USER_ID, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported import format");
    }

    // ─── Unique Name Conflict ───

    @Test
    void importCollection_nameConflict_appendsSuffix() {
        String content = "{\"info\":{\"name\":\"Test\"},\"item\":[]}";
        ImportCollectionRequest request = new ImportCollectionRequest("postman", content);

        Collection collection = new Collection();
        collection.setId(UUID.randomUUID());
        collection.setName("Test");
        collection.setTeamId(TEAM_ID);
        collection.setFolders(new ArrayList<>());
        collection.setVariables(new ArrayList<>());

        when(postmanImporter.parse(content, TEAM_ID, USER_ID))
                .thenReturn(new PostmanImporter.PostmanImportResult(collection, 0, 0, 0, List.of()));
        when(collectionRepository.existsByTeamIdAndName(TEAM_ID, "Test")).thenReturn(true);
        when(collectionRepository.existsByTeamIdAndName(TEAM_ID, "Test (2)")).thenReturn(false);
        when(collectionRepository.save(any(Collection.class))).thenAnswer(inv -> inv.getArgument(0));

        ImportResultResponse result = service.importCollection(TEAM_ID, USER_ID, request);

        assertThat(result.collectionName()).isEqualTo("Test (2)");
    }

    // ─── Auto-Detect: Postman ───

    @Test
    void resolveFormat_autoDetectsPostman() {
        String format = service.resolveFormat("auto", "{\"info\":{\"name\":\"Test\"},\"item\":[]}");
        assertThat(format).isEqualTo("postman");
    }

    // ─── Auto-Detect: OpenAPI JSON ───

    @Test
    void resolveFormat_autoDetectsOpenApiJson() {
        String format = service.resolveFormat("auto", "{\"openapi\":\"3.0.3\",\"info\":{}}");
        assertThat(format).isEqualTo("openapi");
    }

    // ─── Auto-Detect: OpenAPI YAML ───

    @Test
    void resolveFormat_autoDetectsOpenApiYaml() {
        String format = service.resolveFormat("auto", "openapi: \"3.0.3\"\ninfo:\n  title: Test");
        assertThat(format).isEqualTo("openapi-yaml");
    }

    // ─── Auto-Detect: cURL ───

    @Test
    void resolveFormat_autoDetectsCurl() {
        String format = service.resolveFormat("auto", "curl https://api.example.com/users");
        assertThat(format).isEqualTo("curl");
    }

    // ─── Auto-Detect: Unknown ───

    @Test
    void resolveFormat_autoDetectUnknown_throws() {
        assertThatThrownBy(() -> service.resolveFormat("auto", "some random text"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unable to auto-detect");
    }

    // ─── OpenAPI YAML Format Normalization ───

    @Test
    void resolveFormat_normalizesOpenapiYaml() {
        assertThat(service.resolveFormat("openapi-yaml", "")).isEqualTo("openapi-yaml");
        assertThat(service.resolveFormat("openapi-json", "")).isEqualTo("openapi");
    }
}
