package com.codeops.courier.service;

import com.codeops.courier.dto.request.CreateForkRequest;
import com.codeops.courier.dto.response.CollectionResponse;
import com.codeops.courier.dto.response.ForkResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Fork;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.ForkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
 * Unit tests for ForkService covering fork creation, listing, and retrieval.
 */
@ExtendWith(MockitoExtension.class)
class ForkServiceTest {

    @Mock
    private ForkRepository forkRepository;

    @Mock
    private CollectionService collectionService;

    @InjectMocks
    private ForkService forkService;

    private UUID teamId;
    private UUID userId;
    private UUID collectionId;
    private UUID forkId;
    private Collection sourceCollection;
    private Collection forkedCollection;
    private Fork fork;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        userId = UUID.randomUUID();
        collectionId = UUID.randomUUID();
        forkId = UUID.randomUUID();

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
        fork.setLabel("my-fork");
        fork.setCreatedAt(Instant.now());
    }

    @Test
    void forkCollection_success() {
        CreateForkRequest request = new CreateForkRequest("my-fork");
        CollectionResponse dupResponse = new CollectionResponse(
                forkedCollection.getId(), teamId, "Source Collection (Copy)", null,
                null, null, null, null, false, userId, 0, 0,
                Instant.now(), Instant.now()
        );

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(sourceCollection);
        when(forkRepository.existsBySourceCollectionIdAndForkedByUserId(collectionId, userId)).thenReturn(false);
        when(collectionService.duplicateCollection(collectionId, teamId, userId)).thenReturn(dupResponse);
        when(collectionService.findCollectionByIdAndTeam(forkedCollection.getId(), teamId))
                .thenReturn(forkedCollection);
        when(forkRepository.save(any(Fork.class))).thenAnswer(invocation -> {
            Fork f = invocation.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setCreatedAt(Instant.now());
            return f;
        });

        ForkResponse response = forkService.forkCollection(collectionId, teamId, userId, request);

        assertThat(response).isNotNull();
        assertThat(response.sourceCollectionId()).isEqualTo(collectionId);
        assertThat(response.label()).isEqualTo("my-fork");
        verify(forkRepository).save(any(Fork.class));
    }

    @Test
    void forkCollection_alreadyForked_throwsValidation() {
        CreateForkRequest request = new CreateForkRequest("my-fork");

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(sourceCollection);
        when(forkRepository.existsBySourceCollectionIdAndForkedByUserId(collectionId, userId)).thenReturn(true);

        assertThatThrownBy(() -> forkService.forkCollection(collectionId, teamId, userId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already forked");

        verify(forkRepository, never()).save(any());
    }

    @Test
    void forkCollection_notFound_throws() {
        CreateForkRequest request = new CreateForkRequest("my-fork");

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId))
                .thenThrow(new NotFoundException("Collection not found: " + collectionId));

        assertThatThrownBy(() -> forkService.forkCollection(collectionId, teamId, userId, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void forkCollection_withNullRequest() {
        CollectionResponse dupResponse = new CollectionResponse(
                forkedCollection.getId(), teamId, "Source Collection (Copy)", null,
                null, null, null, null, false, userId, 0, 0,
                Instant.now(), Instant.now()
        );

        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(sourceCollection);
        when(forkRepository.existsBySourceCollectionIdAndForkedByUserId(collectionId, userId)).thenReturn(false);
        when(collectionService.duplicateCollection(collectionId, teamId, userId)).thenReturn(dupResponse);
        when(collectionService.findCollectionByIdAndTeam(forkedCollection.getId(), teamId))
                .thenReturn(forkedCollection);
        when(forkRepository.save(any(Fork.class))).thenAnswer(invocation -> {
            Fork f = invocation.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setCreatedAt(Instant.now());
            return f;
        });

        ForkResponse response = forkService.forkCollection(collectionId, teamId, userId, null);

        assertThat(response).isNotNull();
        assertThat(response.label()).isNull();
    }

    @Test
    void getForksForCollection_success() {
        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(sourceCollection);
        when(forkRepository.findBySourceCollectionId(collectionId)).thenReturn(List.of(fork));

        List<ForkResponse> result = forkService.getForksForCollection(collectionId, teamId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceCollectionName()).isEqualTo("Source Collection");
    }

    @Test
    void getForksForCollection_emptyList() {
        when(collectionService.findCollectionByIdAndTeam(collectionId, teamId)).thenReturn(sourceCollection);
        when(forkRepository.findBySourceCollectionId(collectionId)).thenReturn(List.of());

        List<ForkResponse> result = forkService.getForksForCollection(collectionId, teamId);

        assertThat(result).isEmpty();
    }

    @Test
    void getUserForks_success() {
        when(forkRepository.findByForkedByUserId(userId)).thenReturn(List.of(fork));

        List<ForkResponse> result = forkService.getUserForks(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).forkedByUserId()).isEqualTo(userId);
    }

    @Test
    void getFork_success() {
        when(forkRepository.findById(forkId)).thenReturn(Optional.of(fork));

        ForkResponse response = forkService.getFork(forkId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(forkId);
        assertThat(response.sourceCollectionId()).isEqualTo(collectionId);
        assertThat(response.sourceCollectionName()).isEqualTo("Source Collection");
    }

    @Test
    void getFork_notFound_throws() {
        when(forkRepository.findById(forkId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> forkService.getFork(forkId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(forkId.toString());
    }
}
