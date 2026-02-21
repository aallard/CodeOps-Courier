package com.codeops.courier.service;

import com.codeops.courier.dto.request.CreateMergeRequestRequest;
import com.codeops.courier.dto.request.ResolveMergeRequest;
import com.codeops.courier.dto.response.MergeRequestResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Fork;
import com.codeops.courier.entity.MergeRequest;
import com.codeops.courier.entity.Request;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.FolderRepository;
import com.codeops.courier.repository.ForkRepository;
import com.codeops.courier.repository.MergeRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing merge requests between forked and source collections.
 * Supports conflict detection and resolution.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class MergeService {

    private final MergeRequestRepository mergeRequestRepository;
    private final ForkRepository forkRepository;
    private final FolderRepository folderRepository;
    private final CollectionService collectionService;
    private final ObjectMapper objectMapper;

    /**
     * Creates a merge request from a fork back to its source collection.
     *
     * @param teamId  the team ID for access validation
     * @param userId  the user creating the merge request
     * @param request the merge request details
     * @return the merge request response
     * @throws NotFoundException if the fork does not exist
     */
    public MergeRequestResponse createMergeRequest(UUID teamId, UUID userId, CreateMergeRequestRequest request) {
        Fork fork = forkRepository.findById(request.forkId())
                .orElseThrow(() -> new NotFoundException("Fork not found: " + request.forkId()));

        Collection target = fork.getSourceCollection();
        collectionService.findCollectionByIdAndTeam(target.getId(), teamId);

        MergeRequest mergeRequest = new MergeRequest();
        mergeRequest.setTitle(request.title());
        mergeRequest.setDescription(request.description());
        mergeRequest.setStatus("OPEN");
        mergeRequest.setRequestedByUserId(userId);
        mergeRequest.setSourceFork(fork);
        mergeRequest.setTargetCollection(target);

        MergeRequest saved = mergeRequestRepository.save(mergeRequest);
        log.info("Created merge request '{}' from fork {} to collection '{}'",
                saved.getTitle(), fork.getId(), target.getName());
        return toResponse(saved);
    }

    /**
     * Lists all merge requests for a collection.
     *
     * @param collectionId the target collection ID
     * @param teamId       the team ID for access validation
     * @return list of merge request responses
     */
    @Transactional(readOnly = true)
    public List<MergeRequestResponse> getMergeRequests(UUID collectionId, UUID teamId) {
        collectionService.findCollectionByIdAndTeam(collectionId, teamId);
        return mergeRequestRepository.findByTargetCollectionId(collectionId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Gets a single merge request by ID.
     *
     * @param mergeRequestId the merge request ID
     * @return the merge request response
     * @throws NotFoundException if the merge request does not exist
     */
    @Transactional(readOnly = true)
    public MergeRequestResponse getMergeRequest(UUID mergeRequestId) {
        MergeRequest mr = mergeRequestRepository.findById(mergeRequestId)
                .orElseThrow(() -> new NotFoundException("Merge request not found: " + mergeRequestId));
        return toResponse(mr);
    }

    /**
     * Resolves a merge request by either merging or closing it.
     *
     * @param mergeRequestId the merge request ID
     * @param userId         the user resolving the request
     * @param request        the resolution action ("MERGE" or "CLOSE")
     * @return the updated merge request response
     * @throws NotFoundException   if the merge request does not exist
     * @throws ValidationException if the action is invalid
     */
    public MergeRequestResponse resolveMergeRequest(UUID mergeRequestId, UUID userId, ResolveMergeRequest request) {
        MergeRequest mr = mergeRequestRepository.findById(mergeRequestId)
                .orElseThrow(() -> new NotFoundException("Merge request not found: " + mergeRequestId));

        String action = request.action().toUpperCase();

        if ("CLOSE".equals(action)) {
            mr.setStatus("CLOSED");
            mr.setReviewedByUserId(userId);
            MergeRequest saved = mergeRequestRepository.save(mr);
            log.info("Closed merge request '{}'", mr.getTitle());
            return toResponse(saved);
        }

        if ("MERGE".equals(action)) {
            Collection forkCollection = mr.getSourceFork().getForkedCollection();
            Collection targetCollection = mr.getTargetCollection();

            List<ConflictDetail> conflicts = detectConflicts(forkCollection, targetCollection);

            if (!conflicts.isEmpty()) {
                mr.setStatus("CONFLICT");
                mr.setConflictDetails(serializeConflicts(conflicts));
                MergeRequest saved = mergeRequestRepository.save(mr);
                log.warn("Merge request '{}' has {} conflicts", mr.getTitle(), conflicts.size());
                return toResponse(saved);
            }

            mergeForkIntoTarget(forkCollection, targetCollection);
            mr.setStatus("MERGED");
            mr.setReviewedByUserId(userId);
            mr.setMergedAt(Instant.now());
            MergeRequest saved = mergeRequestRepository.save(mr);
            log.info("Merged merge request '{}' into collection '{}'", mr.getTitle(), targetCollection.getName());
            return toResponse(saved);
        }

        throw new ValidationException("Invalid merge action: '" + request.action() + "'. Must be 'MERGE' or 'CLOSE'");
    }

    /**
     * Detects conflicts between a fork and its target collection.
     * A conflict occurs when the same folder path + request name exists in both
     * with different content.
     */
    List<ConflictDetail> detectConflicts(Collection fork, Collection target) {
        List<ConflictDetail> conflicts = new ArrayList<>();

        List<Folder> forkFolders = folderRepository.findByCollectionIdOrderBySortOrder(fork.getId());
        List<Folder> targetFolders = folderRepository.findByCollectionIdOrderBySortOrder(target.getId());

        Map<String, Folder> targetFolderMap = targetFolders.stream()
                .collect(Collectors.toMap(Folder::getName, f -> f, (a, b) -> a));

        for (Folder forkFolder : forkFolders) {
            Folder matchingTarget = targetFolderMap.get(forkFolder.getName());
            if (matchingTarget != null) {
                Map<String, Request> targetRequests = matchingTarget.getRequests().stream()
                        .collect(Collectors.toMap(Request::getName, r -> r, (a, b) -> a));

                for (Request forkReq : forkFolder.getRequests()) {
                    Request targetReq = targetRequests.get(forkReq.getName());
                    if (targetReq != null && hasContentDifference(forkReq, targetReq)) {
                        conflicts.add(new ConflictDetail(
                                forkFolder.getName() + "/" + forkReq.getName(),
                                "MODIFIED",
                                forkReq.getMethod() + " " + forkReq.getUrl(),
                                targetReq.getMethod() + " " + targetReq.getUrl()
                        ));
                    }
                }
            }
        }

        return conflicts;
    }

    private boolean hasContentDifference(Request fork, Request target) {
        if (fork.getMethod() != target.getMethod()) return true;
        if (!fork.getUrl().equals(target.getUrl())) return true;
        return false;
    }

    private void mergeForkIntoTarget(Collection fork, Collection target) {
        List<Folder> forkFolders = folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(fork.getId());
        List<Folder> targetFolders = folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(target.getId());

        Map<String, Folder> targetFolderMap = targetFolders.stream()
                .collect(Collectors.toMap(Folder::getName, f -> f, (a, b) -> a));

        for (Folder forkFolder : forkFolders) {
            if (!targetFolderMap.containsKey(forkFolder.getName())) {
                Folder newFolder = new Folder();
                newFolder.setName(forkFolder.getName());
                newFolder.setDescription(forkFolder.getDescription());
                newFolder.setSortOrder(forkFolder.getSortOrder());
                newFolder.setCollection(target);
                folderRepository.save(newFolder);
            }
        }
    }

    private String serializeConflicts(List<ConflictDetail> conflicts) {
        try {
            return objectMapper.writeValueAsString(conflicts);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize conflict details", e);
            return "[]";
        }
    }

    private MergeRequestResponse toResponse(MergeRequest mr) {
        return new MergeRequestResponse(
                mr.getId(),
                mr.getSourceFork().getId(),
                mr.getTargetCollection().getId(),
                mr.getTargetCollection().getName(),
                mr.getTitle(),
                mr.getDescription(),
                mr.getStatus(),
                mr.getRequestedByUserId(),
                mr.getReviewedByUserId(),
                mr.getMergedAt(),
                mr.getConflictDetails(),
                mr.getCreatedAt(),
                mr.getUpdatedAt()
        );
    }

    record ConflictDetail(
            String path,
            String conflictType,
            String forkValue,
            String targetValue
    ) {}
}
