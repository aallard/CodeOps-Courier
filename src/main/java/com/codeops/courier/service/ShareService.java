package com.codeops.courier.service;

import com.codeops.courier.dto.request.ShareCollectionRequest;
import com.codeops.courier.dto.request.UpdateSharePermissionRequest;
import com.codeops.courier.dto.response.CollectionShareResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.CollectionShare;
import com.codeops.courier.entity.enums.SharePermission;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.CollectionRepository;
import com.codeops.courier.repository.CollectionShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing collection sharing with Viewer/Editor/Admin permissions.
 * Handles sharing CRUD operations and permission-level checks for access control.
 * Permission hierarchy: ADMIN > EDITOR > VIEWER.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ShareService {

    private final CollectionShareRepository shareRepository;
    private final CollectionRepository collectionRepository;

    /**
     * Shares a collection with another user at the specified permission level.
     * Validates that the collection exists, belongs to the team, the user is not
     * sharing with themselves, and the collection is not already shared with the target user.
     *
     * @param collectionId   the collection to share
     * @param teamId         the team ID for access validation
     * @param sharedByUserId the user performing the share
     * @param request        the share request with target user ID and permission level
     * @return the created share response
     * @throws NotFoundException   if the collection is not found or belongs to a different team
     * @throws ValidationException if sharing with self or already shared with this user
     */
    public CollectionShareResponse shareCollection(UUID collectionId, UUID teamId,
                                                   UUID sharedByUserId, ShareCollectionRequest request) {
        Collection collection = findCollectionAndValidateTeam(collectionId, teamId);

        if (request.sharedWithUserId().equals(sharedByUserId)) {
            throw new ValidationException("Cannot share a collection with yourself");
        }

        if (shareRepository.existsByCollectionIdAndSharedWithUserId(collectionId, request.sharedWithUserId())) {
            throw new ValidationException("Collection already shared with this user");
        }

        CollectionShare share = new CollectionShare();
        share.setCollection(collection);
        share.setSharedWithUserId(request.sharedWithUserId());
        share.setSharedByUserId(sharedByUserId);
        share.setPermission(request.permission());

        CollectionShare saved = shareRepository.save(share);
        log.info("Shared collection {} with user {} (permission: {})",
                collectionId, request.sharedWithUserId(), request.permission());
        return toResponse(saved);
    }

    /**
     * Returns all shares for a collection.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID for access validation
     * @return list of share responses
     * @throws NotFoundException if the collection is not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public List<CollectionShareResponse> getCollectionShares(UUID collectionId, UUID teamId) {
        findCollectionAndValidateTeam(collectionId, teamId);
        return shareRepository.findByCollectionId(collectionId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns all collections shared with a specific user.
     *
     * @param userId the user ID to look up shares for
     * @return list of share responses for collections shared with this user
     */
    @Transactional(readOnly = true)
    public List<CollectionShareResponse> getSharedWithUser(UUID userId) {
        return shareRepository.findBySharedWithUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Updates the permission level for an existing share.
     *
     * @param collectionId   the collection ID
     * @param sharedWithUserId the user whose permission is being updated
     * @param teamId         the team ID for access validation
     * @param request        the update request with the new permission level
     * @return the updated share response
     * @throws NotFoundException if the collection or share is not found
     */
    public CollectionShareResponse updateSharePermission(UUID collectionId, UUID sharedWithUserId,
                                                         UUID teamId, UpdateSharePermissionRequest request) {
        findCollectionAndValidateTeam(collectionId, teamId);

        CollectionShare share = shareRepository.findByCollectionIdAndSharedWithUserId(collectionId, sharedWithUserId)
                .orElseThrow(() -> new NotFoundException(
                        "Share not found for collection " + collectionId + " and user " + sharedWithUserId));

        share.setPermission(request.permission());
        CollectionShare saved = shareRepository.save(share);
        log.info("Updated share permission for collection {} user {} to {}",
                collectionId, sharedWithUserId, request.permission());
        return toResponse(saved);
    }

    /**
     * Revokes a share, removing a user's access to a collection.
     *
     * @param collectionId   the collection ID
     * @param sharedWithUserId the user whose access is being revoked
     * @param teamId         the team ID for access validation
     * @throws NotFoundException if the collection or share is not found
     */
    public void revokeShare(UUID collectionId, UUID sharedWithUserId, UUID teamId) {
        findCollectionAndValidateTeam(collectionId, teamId);

        CollectionShare share = shareRepository.findByCollectionIdAndSharedWithUserId(collectionId, sharedWithUserId)
                .orElseThrow(() -> new NotFoundException(
                        "Share not found for collection " + collectionId + " and user " + sharedWithUserId));

        shareRepository.delete(share);
        log.info("Revoked share for collection {} from user {}", collectionId, sharedWithUserId);
    }

    /**
     * Checks if a user has at least the specified permission on a collection.
     * The collection owner (createdBy) always has full permission.
     * Permission hierarchy: ADMIN (2) > EDITOR (1) > VIEWER (0).
     *
     * @param collectionId       the collection ID
     * @param userId             the user ID to check
     * @param requiredPermission the minimum required permission level
     * @return true if the user has sufficient permission
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID collectionId, UUID userId, SharePermission requiredPermission) {
        Collection collection = collectionRepository.findById(collectionId).orElse(null);
        if (collection == null) {
            return false;
        }

        if (userId.equals(collection.getCreatedBy())) {
            return true;
        }

        return shareRepository.findByCollectionIdAndSharedWithUserId(collectionId, userId)
                .map(share -> share.getPermission().ordinal() >= requiredPermission.ordinal())
                .orElse(false);
    }

    /**
     * Checks if a user can view a collection (VIEWER or higher).
     *
     * @param collectionId the collection ID
     * @param userId       the user ID
     * @return true if the user has at least VIEWER permission
     */
    @Transactional(readOnly = true)
    public boolean canView(UUID collectionId, UUID userId) {
        return hasPermission(collectionId, userId, SharePermission.VIEWER);
    }

    /**
     * Checks if a user can edit a collection (EDITOR or higher).
     *
     * @param collectionId the collection ID
     * @param userId       the user ID
     * @return true if the user has at least EDITOR permission
     */
    @Transactional(readOnly = true)
    public boolean canEdit(UUID collectionId, UUID userId) {
        return hasPermission(collectionId, userId, SharePermission.EDITOR);
    }

    /**
     * Checks if a user can administer a collection (ADMIN only).
     *
     * @param collectionId the collection ID
     * @param userId       the user ID
     * @return true if the user has ADMIN permission
     */
    @Transactional(readOnly = true)
    public boolean canAdmin(UUID collectionId, UUID userId) {
        return hasPermission(collectionId, userId, SharePermission.ADMIN);
    }

    /**
     * Finds a collection by ID and validates it belongs to the specified team.
     *
     * @param collectionId the collection ID
     * @param teamId       the expected team ID
     * @return the collection entity
     * @throws NotFoundException if not found or wrong team
     */
    private Collection findCollectionAndValidateTeam(UUID collectionId, UUID teamId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new NotFoundException("Collection not found: " + collectionId));
        if (!collection.getTeamId().equals(teamId)) {
            throw new NotFoundException("Collection not found: " + collectionId);
        }
        return collection;
    }

    /**
     * Converts a CollectionShare entity to a CollectionShareResponse.
     *
     * @param share the entity to convert
     * @return the response DTO
     */
    private CollectionShareResponse toResponse(CollectionShare share) {
        return new CollectionShareResponse(
                share.getId(),
                share.getCollection().getId(),
                share.getSharedWithUserId(),
                share.getSharedByUserId(),
                share.getPermission(),
                share.getCreatedAt()
        );
    }
}
