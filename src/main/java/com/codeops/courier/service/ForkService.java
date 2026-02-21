package com.codeops.courier.service;

import com.codeops.courier.dto.request.CreateForkRequest;
import com.codeops.courier.dto.response.CollectionResponse;
import com.codeops.courier.dto.response.ForkResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Fork;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.ForkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing collection forks. A fork creates a deep copy of a collection
 * that can be independently modified and later merged back.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ForkService {

    private final ForkRepository forkRepository;
    private final CollectionService collectionService;

    /**
     * Forks a collection by creating a deep copy owned by the forking user.
     *
     * @param collectionId the source collection to fork
     * @param teamId       the team owning the collection
     * @param userId       the user performing the fork
     * @param request      the fork request with optional label
     * @return the fork response
     * @throws NotFoundException   if the source collection does not exist
     * @throws ValidationException if the user has already forked this collection
     */
    public ForkResponse forkCollection(UUID collectionId, UUID teamId, UUID userId, CreateForkRequest request) {
        Collection source = collectionService.findCollectionByIdAndTeam(collectionId, teamId);

        if (forkRepository.existsBySourceCollectionIdAndForkedByUserId(collectionId, userId)) {
            throw new ValidationException("You have already forked this collection");
        }

        CollectionResponse duplicated = collectionService.duplicateCollection(collectionId, teamId, userId);

        Fork fork = new Fork();
        fork.setSourceCollection(source);
        fork.setForkedCollection(
                collectionService.findCollectionByIdAndTeam(duplicated.id(), teamId)
        );
        fork.setForkedByUserId(userId);
        fork.setForkedAt(Instant.now());
        fork.setLabel(request != null ? request.label() : null);

        Fork saved = forkRepository.save(fork);
        log.info("User {} forked collection '{}' as '{}'", userId, source.getName(), duplicated.name());
        return toResponse(saved);
    }

    /**
     * Lists all forks of a given collection.
     *
     * @param collectionId the source collection ID
     * @param teamId       the team ID for access validation
     * @return list of fork responses
     */
    @Transactional(readOnly = true)
    public List<ForkResponse> getForksForCollection(UUID collectionId, UUID teamId) {
        collectionService.findCollectionByIdAndTeam(collectionId, teamId);
        return forkRepository.findBySourceCollectionId(collectionId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Lists all forks created by a specific user.
     *
     * @param userId the user ID
     * @return list of fork responses
     */
    @Transactional(readOnly = true)
    public List<ForkResponse> getUserForks(UUID userId) {
        return forkRepository.findByForkedByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Gets a specific fork by ID.
     *
     * @param forkId the fork ID
     * @return the fork response
     * @throws NotFoundException if the fork does not exist
     */
    @Transactional(readOnly = true)
    public ForkResponse getFork(UUID forkId) {
        Fork fork = forkRepository.findById(forkId)
                .orElseThrow(() -> new NotFoundException("Fork not found: " + forkId));
        return toResponse(fork);
    }

    private ForkResponse toResponse(Fork fork) {
        return new ForkResponse(
                fork.getId(),
                fork.getSourceCollection().getId(),
                fork.getSourceCollection().getName(),
                fork.getForkedCollection().getId(),
                fork.getForkedByUserId(),
                fork.getLabel(),
                fork.getForkedAt(),
                fork.getCreatedAt()
        );
    }
}
