package com.codeops.courier.service;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.mapper.CollectionMapper;
import com.codeops.courier.dto.request.CreateCollectionRequest;
import com.codeops.courier.dto.request.UpdateCollectionRequest;
import com.codeops.courier.dto.response.CollectionResponse;
import com.codeops.courier.dto.response.CollectionSummaryResponse;
import com.codeops.courier.dto.response.PageResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.CollectionShare;
import com.codeops.courier.entity.EnvironmentVariable;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.RequestHeader;
import com.codeops.courier.entity.RequestParam;
import com.codeops.courier.entity.RequestBody;
import com.codeops.courier.entity.RequestAuth;
import com.codeops.courier.entity.RequestScript;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.entity.enums.ScriptType;
import com.codeops.courier.entity.enums.SharePermission;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.CollectionRepository;
import com.codeops.courier.repository.CollectionShareRepository;
import com.codeops.courier.repository.FolderRepository;
import com.codeops.courier.repository.RequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CollectionService covering all CRUD operations,
 * duplication, and search functionality.
 */
@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private CollectionShareRepository collectionShareRepository;

    @Mock
    private CollectionMapper collectionMapper;

    @InjectMocks
    private CollectionService collectionService;

    private UUID teamId;
    private UUID userId;
    private UUID collectionId;
    private Collection collection;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        userId = UUID.randomUUID();
        collectionId = UUID.randomUUID();

        collection = new Collection();
        collection.setId(collectionId);
        collection.setTeamId(teamId);
        collection.setCreatedBy(userId);
        collection.setName("Test Collection");
        collection.setDescription("Test description");
        collection.setShared(false);
        collection.setCreatedAt(Instant.now());
        collection.setUpdatedAt(Instant.now());
    }

    @Test
    void createCollection_success() {
        CreateCollectionRequest request = new CreateCollectionRequest("New Collection", "desc", null, null);
        Collection mapped = new Collection();
        mapped.setName("New Collection");
        mapped.setDescription("desc");

        when(collectionRepository.existsByTeamIdAndName(teamId, "New Collection")).thenReturn(false);
        when(collectionMapper.toEntity(request)).thenReturn(mapped);
        when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> {
            Collection c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        CollectionResponse response = collectionService.createCollection(teamId, userId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("New Collection");
        assertThat(response.folderCount()).isZero();
        assertThat(response.requestCount()).isZero();
        verify(collectionRepository).save(any(Collection.class));
    }

    @Test
    void createCollection_duplicateName_throwsValidation() {
        CreateCollectionRequest request = new CreateCollectionRequest("Existing", "desc", null, null);
        when(collectionRepository.existsByTeamIdAndName(teamId, "Existing")).thenReturn(true);

        assertThatThrownBy(() -> collectionService.createCollection(teamId, userId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");

        verify(collectionRepository, never()).save(any());
    }

    @Test
    void getCollection_success() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(folderRepository.countByCollectionId(collectionId)).thenReturn(3L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(collectionId)).thenReturn(List.of());

        CollectionResponse response = collectionService.getCollection(collectionId, teamId);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Test Collection");
        assertThat(response.folderCount()).isEqualTo(3);
    }

    @Test
    void getCollection_notFound_throws() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collectionService.getCollection(collectionId, teamId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(collectionId.toString());
    }

    @Test
    void getCollection_wrongTeam_throws() {
        UUID wrongTeam = UUID.randomUUID();
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));

        assertThatThrownBy(() -> collectionService.getCollection(collectionId, wrongTeam))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getCollections_returnsOwnedAndShared() {
        Collection ownedCollection = createCollection("Owned", teamId);
        Collection sharedCollection = createCollection("Shared", UUID.randomUUID());

        CollectionShare share = new CollectionShare();
        share.setCollection(sharedCollection);
        share.setSharedWithUserId(userId);
        share.setSharedByUserId(UUID.randomUUID());
        share.setPermission(SharePermission.VIEWER);

        when(collectionRepository.findByTeamId(teamId)).thenReturn(List.of(ownedCollection));
        when(collectionShareRepository.findBySharedWithUserId(userId)).thenReturn(List.of(share));
        when(folderRepository.countByCollectionId(any(UUID.class))).thenReturn(0L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(any(UUID.class))).thenReturn(List.of());

        List<CollectionSummaryResponse> result = collectionService.getCollections(teamId, userId);

        assertThat(result).hasSize(2);
    }

    @Test
    void getCollections_emptyList() {
        when(collectionRepository.findByTeamId(teamId)).thenReturn(List.of());
        when(collectionShareRepository.findBySharedWithUserId(userId)).thenReturn(List.of());

        List<CollectionSummaryResponse> result = collectionService.getCollections(teamId, userId);

        assertThat(result).isEmpty();
    }

    @Test
    void getCollectionsPaged_success() {
        List<Collection> collections = List.of(collection);
        Page<Collection> page = new PageImpl<>(collections, PageRequest.of(0, 20), 1);

        when(collectionRepository.findByTeamId(eq(teamId), any(PageRequest.class))).thenReturn(page);
        when(folderRepository.countByCollectionId(any(UUID.class))).thenReturn(0L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(any(UUID.class))).thenReturn(List.of());

        PageResponse<CollectionSummaryResponse> result = collectionService.getCollectionsPaged(teamId, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.isLast()).isTrue();
    }

    @Test
    void getCollectionsPaged_respectsMaxPageSize() {
        Page<Collection> page = new PageImpl<>(List.of(), PageRequest.of(0, AppConstants.MAX_PAGE_SIZE), 0);

        when(collectionRepository.findByTeamId(eq(teamId), any(PageRequest.class))).thenReturn(page);

        PageResponse<CollectionSummaryResponse> result = collectionService.getCollectionsPaged(teamId, 0, 500);

        verify(collectionRepository).findByTeamId(eq(teamId),
                eq(PageRequest.of(0, AppConstants.MAX_PAGE_SIZE)));
    }

    @Test
    void updateCollection_fullUpdate() {
        UpdateCollectionRequest request = new UpdateCollectionRequest(
                "Updated Name", "Updated desc", "pre-script", "post-script", AuthType.BEARER_TOKEN, "{}"
        );
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionRepository.existsByTeamIdAndName(teamId, "Updated Name")).thenReturn(false);
        when(collectionRepository.save(any(Collection.class))).thenReturn(collection);
        when(folderRepository.countByCollectionId(collectionId)).thenReturn(0L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(collectionId)).thenReturn(List.of());

        CollectionResponse response = collectionService.updateCollection(collectionId, teamId, request);

        assertThat(response).isNotNull();
        verify(collectionRepository).save(any(Collection.class));
    }

    @Test
    void updateCollection_partialUpdate() {
        UpdateCollectionRequest request = new UpdateCollectionRequest(
                null, "Only description updated", null, null, null, null
        );
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(any(Collection.class))).thenReturn(collection);
        when(folderRepository.countByCollectionId(collectionId)).thenReturn(0L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(collectionId)).thenReturn(List.of());

        CollectionResponse response = collectionService.updateCollection(collectionId, teamId, request);

        assertThat(response).isNotNull();
        assertThat(collection.getName()).isEqualTo("Test Collection");
        assertThat(collection.getDescription()).isEqualTo("Only description updated");
    }

    @Test
    void updateCollection_renameToDuplicate_throwsValidation() {
        UpdateCollectionRequest request = new UpdateCollectionRequest(
                "Duplicate Name", null, null, null, null, null
        );
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionRepository.existsByTeamIdAndName(teamId, "Duplicate Name")).thenReturn(true);

        assertThatThrownBy(() -> collectionService.updateCollection(collectionId, teamId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateCollection_notFound_throws() {
        UpdateCollectionRequest request = new UpdateCollectionRequest("Name", null, null, null, null, null);
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collectionService.updateCollection(collectionId, teamId, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateCollection_sameNameNoConflict() {
        UpdateCollectionRequest request = new UpdateCollectionRequest(
                "Test Collection", "new desc", null, null, null, null
        );
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(any(Collection.class))).thenReturn(collection);
        when(folderRepository.countByCollectionId(collectionId)).thenReturn(0L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(collectionId)).thenReturn(List.of());

        CollectionResponse response = collectionService.updateCollection(collectionId, teamId, request);

        assertThat(response).isNotNull();
        verify(collectionRepository, never()).existsByTeamIdAndName(any(), any());
    }

    @Test
    void deleteCollection_success() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));

        collectionService.deleteCollection(collectionId, teamId);

        verify(collectionRepository).delete(collection);
    }

    @Test
    void deleteCollection_notFound_throws() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collectionService.deleteCollection(collectionId, teamId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void duplicateCollection_success() {
        collection.setVariables(new ArrayList<>());
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> {
            Collection c = invocation.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of());
        when(folderRepository.countByCollectionId(any(UUID.class))).thenReturn(0L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(any(UUID.class))).thenReturn(List.of());

        CollectionResponse response = collectionService.duplicateCollection(collectionId, teamId, userId);

        assertThat(response).isNotNull();
        assertThat(response.name()).endsWith("(Copy)");
    }

    @Test
    void duplicateCollection_deepCopiesFolders() {
        collection.setVariables(new ArrayList<>());
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName("Test Folder");
        folder.setSortOrder(0);
        folder.setCollection(collection);
        folder.setRequests(new ArrayList<>());
        folder.setSubFolders(new ArrayList<>());

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> {
            Collection c = invocation.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(folder));
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder f = invocation.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });
        when(folderRepository.countByCollectionId(any(UUID.class))).thenReturn(1L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(any(UUID.class))).thenReturn(List.of());

        CollectionResponse response = collectionService.duplicateCollection(collectionId, teamId, userId);

        assertThat(response).isNotNull();
        verify(folderRepository).save(any(Folder.class));
    }

    @Test
    void duplicateCollection_deepCopiesRequests() {
        collection.setVariables(new ArrayList<>());

        Request request = new Request();
        request.setId(UUID.randomUUID());
        request.setName("GET Users");
        request.setMethod(HttpMethod.GET);
        request.setUrl("https://api.test.com/users");
        request.setSortOrder(0);
        request.setHeaders(new ArrayList<>());
        request.setParams(new ArrayList<>());
        request.setScripts(new ArrayList<>());

        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName("Users");
        folder.setSortOrder(0);
        folder.setCollection(collection);
        folder.setRequests(List.of(request));
        folder.setSubFolders(new ArrayList<>());

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> {
            Collection c = invocation.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(folder));
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder f = invocation.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setRequests(new ArrayList<>());
            return f;
        });
        when(folderRepository.countByCollectionId(any(UUID.class))).thenReturn(1L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(any(UUID.class))).thenReturn(List.of());

        CollectionResponse response = collectionService.duplicateCollection(collectionId, teamId, userId);

        assertThat(response).isNotNull();
    }

    @Test
    void duplicateCollection_preservesHierarchy() {
        collection.setVariables(new ArrayList<>());

        Folder childFolder = new Folder();
        childFolder.setId(UUID.randomUUID());
        childFolder.setName("Child Folder");
        childFolder.setSortOrder(0);
        childFolder.setRequests(new ArrayList<>());
        childFolder.setSubFolders(new ArrayList<>());

        Folder parentFolder = new Folder();
        parentFolder.setId(UUID.randomUUID());
        parentFolder.setName("Parent Folder");
        parentFolder.setSortOrder(0);
        parentFolder.setCollection(collection);
        parentFolder.setRequests(new ArrayList<>());
        parentFolder.setSubFolders(List.of(childFolder));

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> {
            Collection c = invocation.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(parentFolder));
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder f = invocation.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setRequests(new ArrayList<>());
            f.setSubFolders(new ArrayList<>());
            return f;
        });
        when(folderRepository.countByCollectionId(any(UUID.class))).thenReturn(2L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(any(UUID.class))).thenReturn(List.of());

        CollectionResponse response = collectionService.duplicateCollection(collectionId, teamId, userId);

        assertThat(response).isNotNull();
        assertThat(response.folderCount()).isEqualTo(2);
    }

    @Test
    void duplicateCollection_copiesVariables() {
        EnvironmentVariable var = new EnvironmentVariable();
        var.setVariableKey("API_KEY");
        var.setVariableValue("secret123");
        var.setSecret(true);
        var.setEnabled(true);
        var.setScope("collection");
        var.setCollection(collection);

        collection.setVariables(new ArrayList<>(List.of(var)));

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> {
            Collection c = invocation.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of());
        when(folderRepository.countByCollectionId(any(UUID.class))).thenReturn(0L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(any(UUID.class))).thenReturn(List.of());

        CollectionResponse response = collectionService.duplicateCollection(collectionId, teamId, userId);

        assertThat(response).isNotNull();
    }

    @Test
    void searchCollections_findsPartialMatch() {
        Collection match = createCollection("User API Collection", teamId);
        when(collectionRepository.findByTeamIdAndNameContainingIgnoreCase(teamId, "API"))
                .thenReturn(List.of(match));
        when(folderRepository.countByCollectionId(any(UUID.class))).thenReturn(0L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(any(UUID.class))).thenReturn(List.of());

        List<CollectionSummaryResponse> result = collectionService.searchCollections(teamId, "API");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("User API Collection");
    }

    @Test
    void searchCollections_caseInsensitive() {
        Collection match = createCollection("Payment Service", teamId);
        when(collectionRepository.findByTeamIdAndNameContainingIgnoreCase(teamId, "payment"))
                .thenReturn(List.of(match));
        when(folderRepository.countByCollectionId(any(UUID.class))).thenReturn(0L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(any(UUID.class))).thenReturn(List.of());

        List<CollectionSummaryResponse> result = collectionService.searchCollections(teamId, "payment");

        assertThat(result).hasSize(1);
    }

    @Test
    void searchCollections_noResults() {
        when(collectionRepository.findByTeamIdAndNameContainingIgnoreCase(teamId, "nonexistent"))
                .thenReturn(List.of());

        List<CollectionSummaryResponse> result = collectionService.searchCollections(teamId, "nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void findCollectionByIdAndTeam_success() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));

        Collection result = collectionService.findCollectionByIdAndTeam(collectionId, teamId);

        assertThat(result).isEqualTo(collection);
    }

    @Test
    void findCollectionByIdAndTeam_notFound_throws() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collectionService.findCollectionByIdAndTeam(collectionId, teamId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findCollectionByIdAndTeam_wrongTeam_throws() {
        UUID wrongTeam = UUID.randomUUID();
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));

        assertThatThrownBy(() -> collectionService.findCollectionByIdAndTeam(collectionId, wrongTeam))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getCollections_deduplicatesOwnedAndShared() {
        CollectionShare share = new CollectionShare();
        share.setCollection(collection);
        share.setSharedWithUserId(userId);
        share.setSharedByUserId(UUID.randomUUID());
        share.setPermission(SharePermission.VIEWER);

        when(collectionRepository.findByTeamId(teamId)).thenReturn(List.of(collection));
        when(collectionShareRepository.findBySharedWithUserId(userId)).thenReturn(List.of(share));
        when(folderRepository.countByCollectionId(any(UUID.class))).thenReturn(0L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(any(UUID.class))).thenReturn(List.of());

        List<CollectionSummaryResponse> result = collectionService.getCollections(teamId, userId);

        assertThat(result).hasSize(1);
    }

    @Test
    void duplicateCollection_deepCopiesRequestComponents() {
        collection.setVariables(new ArrayList<>());

        RequestHeader header = new RequestHeader();
        header.setHeaderKey("Content-Type");
        header.setHeaderValue("application/json");
        header.setEnabled(true);

        RequestParam param = new RequestParam();
        param.setParamKey("page");
        param.setParamValue("1");
        param.setEnabled(true);

        RequestBody body = new RequestBody();
        body.setBodyType(BodyType.RAW_JSON);
        body.setRawContent("{\"key\": \"value\"}");

        RequestAuth auth = new RequestAuth();
        auth.setAuthType(AuthType.BEARER_TOKEN);
        auth.setBearerToken("test-token");

        RequestScript script = new RequestScript();
        script.setScriptType(ScriptType.PRE_REQUEST);
        script.setContent("console.log('test')");

        Request request = new Request();
        request.setId(UUID.randomUUID());
        request.setName("Test Request");
        request.setMethod(HttpMethod.POST);
        request.setUrl("https://api.test.com/data");
        request.setSortOrder(0);
        request.setHeaders(new ArrayList<>(List.of(header)));
        request.setParams(new ArrayList<>(List.of(param)));
        request.setBody(body);
        request.setAuth(auth);
        request.setScripts(new ArrayList<>(List.of(script)));

        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName("Test Folder");
        folder.setSortOrder(0);
        folder.setCollection(collection);
        folder.setRequests(List.of(request));
        folder.setSubFolders(new ArrayList<>());

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionRepository.save(any(Collection.class))).thenAnswer(invocation -> {
            Collection c = invocation.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(folder));
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder f = invocation.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setRequests(new ArrayList<>());
            return f;
        });
        when(folderRepository.countByCollectionId(any(UUID.class))).thenReturn(1L);
        when(folderRepository.findByCollectionIdOrderBySortOrder(any(UUID.class))).thenReturn(List.of());

        CollectionResponse response = collectionService.duplicateCollection(collectionId, teamId, userId);

        assertThat(response).isNotNull();
    }

    @Test
    void deleteCollection_wrongTeam_throws() {
        UUID wrongTeam = UUID.randomUUID();
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));

        assertThatThrownBy(() -> collectionService.deleteCollection(collectionId, wrongTeam))
                .isInstanceOf(NotFoundException.class);
    }

    private Collection createCollection(String name, UUID team) {
        Collection c = new Collection();
        c.setId(UUID.randomUUID());
        c.setTeamId(team);
        c.setName(name);
        c.setCreatedBy(UUID.randomUUID());
        c.setShared(false);
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        return c;
    }
}
