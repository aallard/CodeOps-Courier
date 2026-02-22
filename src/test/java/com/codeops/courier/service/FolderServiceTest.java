package com.codeops.courier.service;

import com.codeops.courier.dto.mapper.FolderMapper;
import com.codeops.courier.dto.mapper.RequestMapper;
import com.codeops.courier.dto.request.CreateFolderRequest;
import com.codeops.courier.dto.request.ReorderFolderRequest;
import com.codeops.courier.dto.request.UpdateFolderRequest;
import com.codeops.courier.dto.response.FolderResponse;
import com.codeops.courier.dto.response.FolderTreeResponse;
import com.codeops.courier.dto.response.RequestSummaryResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.CollectionRepository;
import com.codeops.courier.repository.FolderRepository;
import com.codeops.courier.repository.RequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FolderService covering all CRUD operations,
 * tree building, reordering, and move operations with circular reference detection.
 */
@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private FolderMapper folderMapper;

    @Mock
    private RequestMapper requestMapper;

    @InjectMocks
    private FolderService folderService;

    private UUID teamId;
    private UUID collectionId;
    private UUID folderId;
    private Collection collection;
    private Folder folder;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        collectionId = UUID.randomUUID();
        folderId = UUID.randomUUID();

        collection = new Collection();
        collection.setId(collectionId);
        collection.setTeamId(teamId);
        collection.setName("Test Collection");
        collection.setCreatedBy(UUID.randomUUID());
        collection.setCreatedAt(Instant.now());
        collection.setUpdatedAt(Instant.now());

        folder = new Folder();
        folder.setId(folderId);
        folder.setName("Test Folder");
        folder.setDescription("Test description");
        folder.setSortOrder(0);
        folder.setCollection(collection);
        folder.setParentFolder(null);
        folder.setSubFolders(new ArrayList<>());
        folder.setRequests(new ArrayList<>());
        folder.setCreatedAt(Instant.now());
        folder.setUpdatedAt(Instant.now());
    }

    @Test
    void createFolder_rootLevel_success() {
        CreateFolderRequest request = new CreateFolderRequest(collectionId, null, "New Folder", "desc", null);
        Folder mapped = new Folder();
        mapped.setName("New Folder");
        mapped.setDescription("desc");

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(folderMapper.toEntity(request)).thenReturn(mapped);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of());
        when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> {
            Folder f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setCreatedAt(Instant.now());
            f.setUpdatedAt(Instant.now());
            return f;
        });
        when(folderRepository.countByParentFolderId(any())).thenReturn(0L);
        when(requestRepository.countByFolderId(any())).thenReturn(0L);

        FolderResponse response = folderService.createFolder(teamId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("New Folder");
        assertThat(response.subFolderCount()).isZero();
        assertThat(response.requestCount()).isZero();
        verify(folderRepository).save(any(Folder.class));
    }

    @Test
    void createFolder_nested_success() {
        UUID parentId = UUID.randomUUID();
        Folder parent = createTestFolder(parentId, null);

        CreateFolderRequest request = new CreateFolderRequest(collectionId, parentId, "Child Folder", null, null);
        Folder mapped = new Folder();
        mapped.setName("Child Folder");

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(folderRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(folderMapper.toEntity(request)).thenReturn(mapped);
        when(folderRepository.findByParentFolderIdOrderBySortOrder(parentId)).thenReturn(List.of());
        when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> {
            Folder f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setCreatedAt(Instant.now());
            f.setUpdatedAt(Instant.now());
            return f;
        });
        when(folderRepository.countByParentFolderId(any())).thenReturn(0L);
        when(requestRepository.countByFolderId(any())).thenReturn(0L);

        FolderResponse response = folderService.createFolder(teamId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Child Folder");
        verify(folderRepository).save(any(Folder.class));
    }

    @Test
    void createFolder_collectionNotFound_throws() {
        CreateFolderRequest request = new CreateFolderRequest(collectionId, null, "Folder", null, null);
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> folderService.createFolder(teamId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(collectionId.toString());

        verify(folderRepository, never()).save(any());
    }

    @Test
    void createFolder_parentInDifferentCollection_throws() {
        UUID parentId = UUID.randomUUID();
        UUID otherCollectionId = UUID.randomUUID();
        Collection otherCollection = new Collection();
        otherCollection.setId(otherCollectionId);
        otherCollection.setTeamId(teamId);

        Folder parent = new Folder();
        parent.setId(parentId);
        parent.setCollection(otherCollection);

        CreateFolderRequest request = new CreateFolderRequest(collectionId, parentId, "Folder", null, null);
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(folderRepository.findById(parentId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> folderService.createFolder(teamId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("different collection");

        verify(folderRepository, never()).save(any());
    }

    @Test
    void createFolder_autoSortOrder() {
        Folder existing1 = createTestFolder(UUID.randomUUID(), null);
        existing1.setSortOrder(0);
        Folder existing2 = createTestFolder(UUID.randomUUID(), null);
        existing2.setSortOrder(1);

        CreateFolderRequest request = new CreateFolderRequest(collectionId, null, "New Folder", null, null);
        Folder mapped = new Folder();
        mapped.setName("New Folder");

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(folderMapper.toEntity(request)).thenReturn(mapped);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(existing1, existing2));
        when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> {
            Folder f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setCreatedAt(Instant.now());
            f.setUpdatedAt(Instant.now());
            return f;
        });
        when(folderRepository.countByParentFolderId(any())).thenReturn(0L);
        when(requestRepository.countByFolderId(any())).thenReturn(0L);

        FolderResponse response = folderService.createFolder(teamId, request);

        assertThat(response.sortOrder()).isEqualTo(2);
    }

    @Test
    void getFolder_success() {
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.countByParentFolderId(folderId)).thenReturn(2L);
        when(requestRepository.countByFolderId(folderId)).thenReturn(5L);

        FolderResponse response = folderService.getFolder(folderId, teamId);

        assertThat(response.name()).isEqualTo("Test Folder");
        assertThat(response.subFolderCount()).isEqualTo(2);
        assertThat(response.requestCount()).isEqualTo(5);
    }

    @Test
    void getFolder_notFound_throws() {
        when(folderRepository.findById(folderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> folderService.getFolder(folderId, teamId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(folderId.toString());
    }

    @Test
    void getFolder_wrongTeam_throws() {
        UUID wrongTeam = UUID.randomUUID();
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));

        assertThatThrownBy(() -> folderService.getFolder(folderId, wrongTeam))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getFolderTree_emptyCollection() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of());

        List<FolderTreeResponse> tree = folderService.getFolderTree(collectionId, teamId);

        assertThat(tree).isEmpty();
    }

    @Test
    void getFolderTree_withNestedFolders() {
        UUID childId = UUID.randomUUID();
        Folder rootFolder = createTestFolder(folderId, null);
        rootFolder.setName("Root");
        Folder childFolder = createTestFolder(childId, folderId);
        childFolder.setName("Child");

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(rootFolder));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(folderId))
                .thenReturn(List.of(childFolder));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(childId))
                .thenReturn(List.of());
        when(requestRepository.findByFolderIdOrderBySortOrder(folderId)).thenReturn(List.of());
        when(requestRepository.findByFolderIdOrderBySortOrder(childId)).thenReturn(List.of());

        List<FolderTreeResponse> tree = folderService.getFolderTree(collectionId, teamId);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).name()).isEqualTo("Root");
        assertThat(tree.get(0).subFolders()).hasSize(1);
        assertThat(tree.get(0).subFolders().get(0).name()).isEqualTo("Child");
    }

    @Test
    void getFolderTree_includesRequests() {
        Folder rootFolder = createTestFolder(folderId, null);

        Request req = new Request();
        req.setId(UUID.randomUUID());
        req.setName("GET Users");
        req.setMethod(HttpMethod.GET);
        req.setUrl("https://api.test.com/users");
        req.setSortOrder(0);

        RequestSummaryResponse summary = new RequestSummaryResponse(
                req.getId(), "GET Users", HttpMethod.GET, "https://api.test.com/users", 0);

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(rootFolder));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(folderId)).thenReturn(List.of());
        when(requestRepository.findByFolderIdOrderBySortOrder(folderId)).thenReturn(List.of(req));
        when(requestMapper.toSummaryResponse(req)).thenReturn(summary);

        List<FolderTreeResponse> tree = folderService.getFolderTree(collectionId, teamId);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).requests()).hasSize(1);
        assertThat(tree.get(0).requests().get(0).name()).isEqualTo("GET Users");
    }

    @Test
    void getSubFolders_success() {
        UUID childId1 = UUID.randomUUID();
        UUID childId2 = UUID.randomUUID();
        Folder child1 = createTestFolder(childId1, folderId);
        child1.setName("Child 1");
        Folder child2 = createTestFolder(childId2, folderId);
        child2.setName("Child 2");

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(folderId))
                .thenReturn(List.of(child1, child2));
        when(folderRepository.countByParentFolderId(any())).thenReturn(0L);
        when(requestRepository.countByFolderId(any())).thenReturn(0L);

        List<FolderResponse> result = folderService.getSubFolders(folderId, teamId);

        assertThat(result).hasSize(2);
    }

    @Test
    void getRootFolders_success() {
        Folder root1 = createTestFolder(UUID.randomUUID(), null);
        Folder root2 = createTestFolder(UUID.randomUUID(), null);

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of(root1, root2));
        when(folderRepository.countByParentFolderId(any())).thenReturn(0L);
        when(requestRepository.countByFolderId(any())).thenReturn(0L);

        List<FolderResponse> result = folderService.getRootFolders(collectionId, teamId);

        assertThat(result).hasSize(2);
    }

    @Test
    void updateFolder_fullUpdate() {
        UpdateFolderRequest request = new UpdateFolderRequest(
                "Updated Name", "Updated desc", 5, null,
                "pre-script", "post-script", AuthType.BEARER_TOKEN, "{config}");

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.save(any(Folder.class))).thenReturn(folder);
        when(folderRepository.countByParentFolderId(folderId)).thenReturn(0L);
        when(requestRepository.countByFolderId(folderId)).thenReturn(0L);

        FolderResponse response = folderService.updateFolder(folderId, teamId, request);

        assertThat(response).isNotNull();
        assertThat(folder.getName()).isEqualTo("Updated Name");
        assertThat(folder.getDescription()).isEqualTo("Updated desc");
        assertThat(folder.getSortOrder()).isEqualTo(5);
        assertThat(folder.getPreRequestScript()).isEqualTo("pre-script");
        assertThat(folder.getPostResponseScript()).isEqualTo("post-script");
        assertThat(folder.getAuthType()).isEqualTo(AuthType.BEARER_TOKEN);
        assertThat(folder.getAuthConfig()).isEqualTo("{config}");
        verify(folderRepository).save(folder);
    }

    @Test
    void updateFolder_partialUpdate() {
        UpdateFolderRequest request = new UpdateFolderRequest(
                "New Name", null, null, null, null, null, null, null);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.save(any(Folder.class))).thenReturn(folder);
        when(folderRepository.countByParentFolderId(folderId)).thenReturn(0L);
        when(requestRepository.countByFolderId(folderId)).thenReturn(0L);

        FolderResponse response = folderService.updateFolder(folderId, teamId, request);

        assertThat(response).isNotNull();
        assertThat(folder.getName()).isEqualTo("New Name");
        assertThat(folder.getDescription()).isEqualTo("Test description");
    }

    @Test
    void updateFolder_moveToNewParent() {
        UUID newParentId = UUID.randomUUID();
        Folder newParent = createTestFolder(newParentId, null);

        UpdateFolderRequest request = new UpdateFolderRequest(
                null, null, null, newParentId, null, null, null, null);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.findById(newParentId)).thenReturn(Optional.of(newParent));
        when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(folderRepository.countByParentFolderId(folderId)).thenReturn(0L);
        when(requestRepository.countByFolderId(folderId)).thenReturn(0L);

        FolderResponse response = folderService.updateFolder(folderId, teamId, request);

        assertThat(response).isNotNull();
        assertThat(folder.getParentFolder()).isEqualTo(newParent);
    }

    @Test
    void updateFolder_circularReference_throws() {
        UUID childId = UUID.randomUUID();
        Folder child = createTestFolder(childId, folderId);

        UpdateFolderRequest request = new UpdateFolderRequest(
                null, null, null, childId, null, null, null, null);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.findById(childId)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> folderService.updateFolder(folderId, teamId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("circular");

        verify(folderRepository, never()).save(any());
    }

    @Test
    void deleteFolder_success() {
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));

        folderService.deleteFolder(folderId, teamId);

        verify(folderRepository).delete(folder);
    }

    @Test
    void deleteFolder_cascadesChildren() {
        folder.getSubFolders().add(createTestFolder(UUID.randomUUID(), folderId));
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));

        folderService.deleteFolder(folderId, teamId);

        verify(folderRepository).delete(folder);
    }

    @Test
    void reorderFolders_success() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        Folder f1 = createTestFolder(id1, null);
        f1.setSortOrder(2);
        Folder f2 = createTestFolder(id2, null);
        f2.setSortOrder(0);
        Folder f3 = createTestFolder(id3, null);
        f3.setSortOrder(1);

        ReorderFolderRequest request = new ReorderFolderRequest(List.of(id1, id2, id3));

        when(folderRepository.findById(id1)).thenReturn(Optional.of(f1));
        when(folderRepository.findById(id2)).thenReturn(Optional.of(f2));
        when(folderRepository.findById(id3)).thenReturn(Optional.of(f3));
        when(folderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(folderRepository.countByParentFolderId(any())).thenReturn(0L);
        when(requestRepository.countByFolderId(any())).thenReturn(0L);

        List<FolderResponse> result = folderService.reorderFolders(teamId, request);

        assertThat(result).hasSize(3);
        assertThat(f1.getSortOrder()).isZero();
        assertThat(f2.getSortOrder()).isEqualTo(1);
        assertThat(f3.getSortOrder()).isEqualTo(2);
    }

    @Test
    void reorderFolders_missingId_throws() {
        UUID missingId = UUID.randomUUID();
        ReorderFolderRequest request = new ReorderFolderRequest(List.of(folderId, missingId));

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> folderService.reorderFolders(teamId, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void moveFolder_toRoot_success() {
        Folder nestedFolder = createTestFolder(folderId, UUID.randomUUID());

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(nestedFolder));
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId))
                .thenReturn(List.of());
        when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(folderRepository.countByParentFolderId(folderId)).thenReturn(0L);
        when(requestRepository.countByFolderId(folderId)).thenReturn(0L);

        FolderResponse response = folderService.moveFolder(folderId, teamId, null);

        assertThat(response).isNotNull();
        assertThat(nestedFolder.getParentFolder()).isNull();
        assertThat(nestedFolder.getSortOrder()).isZero();
    }

    @Test
    void moveFolder_toNewParent_success() {
        UUID targetId = UUID.randomUUID();
        Folder target = createTestFolder(targetId, null);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(targetId)).thenReturn(List.of());
        when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(folderRepository.countByParentFolderId(folderId)).thenReturn(0L);
        when(requestRepository.countByFolderId(folderId)).thenReturn(0L);

        FolderResponse response = folderService.moveFolder(folderId, teamId, targetId);

        assertThat(response).isNotNull();
        assertThat(folder.getParentFolder()).isEqualTo(target);
    }

    @Test
    void moveFolder_circularReference_throws() {
        UUID childId = UUID.randomUUID();
        Folder child = createTestFolder(childId, folderId);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.findById(childId)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> folderService.moveFolder(folderId, teamId, childId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("circular");

        verify(folderRepository, never()).save(any());
    }

    @Test
    void moveFolder_crossCollection_throws() {
        UUID targetId = UUID.randomUUID();
        Collection otherCollection = new Collection();
        otherCollection.setId(UUID.randomUUID());
        otherCollection.setTeamId(teamId);

        Folder target = new Folder();
        target.setId(targetId);
        target.setCollection(otherCollection);
        target.setCreatedAt(Instant.now());
        target.setUpdatedAt(Instant.now());

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> folderService.moveFolder(folderId, teamId, targetId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("different collection");
    }

    private Folder createTestFolder(UUID id, UUID parentFolderId) {
        Folder f = new Folder();
        f.setId(id);
        f.setName("Test Folder");
        f.setSortOrder(0);
        f.setCollection(collection);
        f.setSubFolders(new ArrayList<>());
        f.setRequests(new ArrayList<>());
        f.setCreatedAt(Instant.now());
        f.setUpdatedAt(Instant.now());

        if (parentFolderId != null) {
            Folder parent = new Folder();
            parent.setId(parentFolderId);
            f.setParentFolder(parent);
        }

        return f;
    }
}
