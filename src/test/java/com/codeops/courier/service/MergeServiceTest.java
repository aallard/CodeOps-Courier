package com.codeops.courier.service;

import com.codeops.courier.dto.request.CreateMergeRequestRequest;
import com.codeops.courier.dto.request.ResolveMergeRequest;
import com.codeops.courier.dto.response.MergeRequestResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Fork;
import com.codeops.courier.entity.MergeRequest;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.FolderRepository;
import com.codeops.courier.repository.ForkRepository;
import com.codeops.courier.repository.MergeRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Unit tests for MergeService covering merge request creation,
 * listing, resolution, and conflict detection.
 */
@ExtendWith(MockitoExtension.class)
class MergeServiceTest {

    @Mock
    private MergeRequestRepository mergeRequestRepository;

    @Mock
    private ForkRepository forkRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private CollectionService collectionService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MergeService mergeService;

    private UUID teamId;
    private UUID userId;
    private UUID forkId;
    private UUID mergeRequestId;
    private UUID collectionId;
    private Collection sourceCollection;
    private Collection forkedCollection;
    private Fork fork;
    private MergeRequest mergeRequest;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        userId = UUID.randomUUID();
        forkId = UUID.randomUUID();
        mergeRequestId = UUID.randomUUID();
        collectionId = UUID.randomUUID();

        sourceCollection = new Collection();
        sourceCollection.setId(collectionId);
        sourceCollection.setTeamId(teamId);
        sourceCollection.setName("Source Collection");
        sourceCollection.setCreatedAt(Instant.now());
        sourceCollection.setUpdatedAt(Instant.now());

        UUID forkedCollectionId = UUID.randomUUID();
        forkedCollection = new Collection();
        forkedCollection.setId(forkedCollectionId);
        forkedCollection.setTeamId(teamId);
        forkedCollection.setName("Source Collection (Copy)");
        forkedCollection.setCreatedAt(Instant.now());
        forkedCollection.setUpdatedAt(Instant.now());

        fork = new Fork();
        fork.setId(forkId);
        fork.setSourceCollection(sourceCollection);
        fork.setForkedCollection(forkedCollection);
        fork.setForkedByUserId(userId);
        fork.setForkedAt(Instant.now());

        mergeRequest = new MergeRequest();
        mergeRequest.setId(mergeRequestId);
        mergeRequest.setTitle("Merge feature changes");
        mergeRequest.setDescription("Added new endpoints");
        mergeRequest.setStatus("OPEN");
        mergeRequest.setRequestedByUserId(userId);
        mergeRequest.setSourceFork(fork);
        mergeRequest.setTargetCollection(sourceCollection);
        mergeRequest.setCreatedAt(Instant.now());
        mergeRequest.setUpdatedAt(Instant.now());
    }

    @Test
    void createMergeRequest_success() {
        CreateMergeRequestRequest request = new CreateMergeRequestRequest(
                forkId, "Merge feature changes", "Added new endpoints"
        );

        when(forkRepository.findById(forkId)).thenReturn(Optional.of(fork));
        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(sourceCollection);
        when(mergeRequestRepository.save(any(MergeRequest.class))).thenAnswer(invocation -> {
            MergeRequest mr = invocation.getArgument(0);
            mr.setId(UUID.randomUUID());
            mr.setCreatedAt(Instant.now());
            mr.setUpdatedAt(Instant.now());
            return mr;
        });

        MergeRequestResponse response = mergeService.createMergeRequest(teamId, userId, request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Merge feature changes");
        assertThat(response.status()).isEqualTo("OPEN");
        verify(mergeRequestRepository).save(any(MergeRequest.class));
    }

    @Test
    void createMergeRequest_forkNotFound_throws() {
        CreateMergeRequestRequest request = new CreateMergeRequestRequest(
                forkId, "Title", "Desc"
        );

        when(forkRepository.findById(forkId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mergeService.createMergeRequest(teamId, userId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(forkId.toString());
    }

    @Test
    void getMergeRequests_success() {
        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(sourceCollection);
        when(mergeRequestRepository.findByTargetCollectionId(collectionId))
                .thenReturn(List.of(mergeRequest));

        List<MergeRequestResponse> result = mergeService.getMergeRequests(collectionId, teamId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Merge feature changes");
    }

    @Test
    void getMergeRequests_emptyList() {
        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(sourceCollection);
        when(mergeRequestRepository.findByTargetCollectionId(collectionId)).thenReturn(List.of());

        List<MergeRequestResponse> result = mergeService.getMergeRequests(collectionId, teamId);

        assertThat(result).isEmpty();
    }

    @Test
    void getMergeRequest_success() {
        when(mergeRequestRepository.findById(mergeRequestId)).thenReturn(Optional.of(mergeRequest));

        MergeRequestResponse response = mergeService.getMergeRequest(mergeRequestId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(mergeRequestId);
        assertThat(response.targetCollectionName()).isEqualTo("Source Collection");
    }

    @Test
    void getMergeRequest_notFound_throws() {
        when(mergeRequestRepository.findById(mergeRequestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mergeService.getMergeRequest(mergeRequestId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(mergeRequestId.toString());
    }

    @Test
    void resolveMergeRequest_close_success() {
        ResolveMergeRequest request = new ResolveMergeRequest("CLOSE");

        when(mergeRequestRepository.findById(mergeRequestId)).thenReturn(Optional.of(mergeRequest));
        when(mergeRequestRepository.save(any(MergeRequest.class))).thenReturn(mergeRequest);

        MergeRequestResponse response = mergeService.resolveMergeRequest(mergeRequestId, userId, request);

        assertThat(response).isNotNull();
        assertThat(mergeRequest.getStatus()).isEqualTo("CLOSED");
        assertThat(mergeRequest.getReviewedByUserId()).isEqualTo(userId);
    }

    @Test
    void resolveMergeRequest_merge_noConflicts_success() {
        ResolveMergeRequest request = new ResolveMergeRequest("MERGE");

        when(mergeRequestRepository.findById(mergeRequestId)).thenReturn(Optional.of(mergeRequest));
        when(folderRepository.findByCollectionIdOrderBySortOrder(forkedCollection.getId()))
                .thenReturn(List.of());
        when(folderRepository.findByCollectionIdOrderBySortOrder(sourceCollection.getId()))
                .thenReturn(List.of());
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(forkedCollection.getId()))
                .thenReturn(List.of());
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(sourceCollection.getId()))
                .thenReturn(List.of());
        when(mergeRequestRepository.save(any(MergeRequest.class))).thenReturn(mergeRequest);

        MergeRequestResponse response = mergeService.resolveMergeRequest(mergeRequestId, userId, request);

        assertThat(response).isNotNull();
        assertThat(mergeRequest.getStatus()).isEqualTo("MERGED");
        assertThat(mergeRequest.getMergedAt()).isNotNull();
        assertThat(mergeRequest.getReviewedByUserId()).isEqualTo(userId);
    }

    @Test
    void resolveMergeRequest_merge_withConflicts_returnsConflicts() throws Exception {
        ResolveMergeRequest request = new ResolveMergeRequest("MERGE");

        Request forkReq = new Request();
        forkReq.setName("Get Users");
        forkReq.setMethod(HttpMethod.POST);
        forkReq.setUrl("https://api.test.com/users");

        Folder forkFolder = new Folder();
        forkFolder.setName("Users");
        forkFolder.setRequests(List.of(forkReq));

        Request targetReq = new Request();
        targetReq.setName("Get Users");
        targetReq.setMethod(HttpMethod.GET);
        targetReq.setUrl("https://api.test.com/users");

        Folder targetFolder = new Folder();
        targetFolder.setName("Users");
        targetFolder.setRequests(List.of(targetReq));

        when(mergeRequestRepository.findById(mergeRequestId)).thenReturn(Optional.of(mergeRequest));
        when(folderRepository.findByCollectionIdOrderBySortOrder(forkedCollection.getId()))
                .thenReturn(List.of(forkFolder));
        when(folderRepository.findByCollectionIdOrderBySortOrder(sourceCollection.getId()))
                .thenReturn(List.of(targetFolder));
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"path\":\"Users/Get Users\"}]");
        when(mergeRequestRepository.save(any(MergeRequest.class))).thenReturn(mergeRequest);

        MergeRequestResponse response = mergeService.resolveMergeRequest(mergeRequestId, userId, request);

        assertThat(response).isNotNull();
        assertThat(mergeRequest.getStatus()).isEqualTo("CONFLICT");
        assertThat(mergeRequest.getConflictDetails()).isNotNull();
    }

    @Test
    void resolveMergeRequest_invalidAction_throws() {
        ResolveMergeRequest request = new ResolveMergeRequest("INVALID");

        when(mergeRequestRepository.findById(mergeRequestId)).thenReturn(Optional.of(mergeRequest));

        assertThatThrownBy(() -> mergeService.resolveMergeRequest(mergeRequestId, userId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid merge action");
    }

    @Test
    void resolveMergeRequest_notFound_throws() {
        ResolveMergeRequest request = new ResolveMergeRequest("MERGE");

        when(mergeRequestRepository.findById(mergeRequestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mergeService.resolveMergeRequest(mergeRequestId, userId, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resolveMergeRequest_merge_addsNewFolders() {
        ResolveMergeRequest request = new ResolveMergeRequest("MERGE");

        Folder forkFolder = new Folder();
        forkFolder.setName("New Folder");
        forkFolder.setSortOrder(0);
        forkFolder.setRequests(new ArrayList<>());

        when(mergeRequestRepository.findById(mergeRequestId)).thenReturn(Optional.of(mergeRequest));
        when(folderRepository.findByCollectionIdOrderBySortOrder(forkedCollection.getId()))
                .thenReturn(List.of(forkFolder));
        when(folderRepository.findByCollectionIdOrderBySortOrder(sourceCollection.getId()))
                .thenReturn(List.of());
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(forkedCollection.getId()))
                .thenReturn(List.of(forkFolder));
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(sourceCollection.getId()))
                .thenReturn(List.of());
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder f = invocation.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });
        when(mergeRequestRepository.save(any(MergeRequest.class))).thenReturn(mergeRequest);

        MergeRequestResponse response = mergeService.resolveMergeRequest(mergeRequestId, userId, request);

        assertThat(response).isNotNull();
        assertThat(mergeRequest.getStatus()).isEqualTo("MERGED");
        verify(folderRepository).save(any(Folder.class));
    }

    @Test
    void resolveMergeRequest_close_caseInsensitive() {
        ResolveMergeRequest request = new ResolveMergeRequest("close");

        when(mergeRequestRepository.findById(mergeRequestId)).thenReturn(Optional.of(mergeRequest));
        when(mergeRequestRepository.save(any(MergeRequest.class))).thenReturn(mergeRequest);

        MergeRequestResponse response = mergeService.resolveMergeRequest(mergeRequestId, userId, request);

        assertThat(mergeRequest.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void detectConflicts_noConflictsWhenFoldersDisjoint() {
        Folder forkFolder = new Folder();
        forkFolder.setName("Fork Folder");
        forkFolder.setRequests(new ArrayList<>());

        Folder targetFolder = new Folder();
        targetFolder.setName("Target Folder");
        targetFolder.setRequests(new ArrayList<>());

        when(folderRepository.findByCollectionIdOrderBySortOrder(forkedCollection.getId()))
                .thenReturn(List.of(forkFolder));
        when(folderRepository.findByCollectionIdOrderBySortOrder(sourceCollection.getId()))
                .thenReturn(List.of(targetFolder));

        List<MergeService.ConflictDetail> conflicts = mergeService.detectConflicts(forkedCollection, sourceCollection);

        assertThat(conflicts).isEmpty();
    }

    @Test
    void detectConflicts_noConflictsWhenSameContent() {
        Request req = new Request();
        req.setName("Get Users");
        req.setMethod(HttpMethod.GET);
        req.setUrl("https://api.test.com/users");

        Folder forkFolder = new Folder();
        forkFolder.setName("Users");
        forkFolder.setRequests(List.of(req));

        Folder targetFolder = new Folder();
        targetFolder.setName("Users");
        targetFolder.setRequests(List.of(req));

        when(folderRepository.findByCollectionIdOrderBySortOrder(forkedCollection.getId()))
                .thenReturn(List.of(forkFolder));
        when(folderRepository.findByCollectionIdOrderBySortOrder(sourceCollection.getId()))
                .thenReturn(List.of(targetFolder));

        List<MergeService.ConflictDetail> conflicts = mergeService.detectConflicts(forkedCollection, sourceCollection);

        assertThat(conflicts).isEmpty();
    }
}
