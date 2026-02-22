package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.ShareCollectionRequest;
import com.codeops.courier.dto.request.UpdateSharePermissionRequest;
import com.codeops.courier.dto.response.CollectionShareResponse;
import com.codeops.courier.security.SecurityUtils;
import com.codeops.courier.service.ShareService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing collection sharing and permissions.
 * Provides endpoints for sharing collections with users, listing shares,
 * updating permissions, revoking access, and viewing collections shared
 * with the current user. All endpoints require authentication and the ADMIN role.
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Shares", description = "Collection sharing, permissions, and access management")
public class ShareController {

    private final ShareService shareService;

    /**
     * Shares a collection with another user at the specified permission level.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID from the request header
     * @param request      the share request with target user and permission level
     * @return the created share response
     */
    @PostMapping("/collections/{collectionId}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionShareResponse shareCollection(@PathVariable UUID collectionId,
                                                    @RequestHeader("X-Team-ID") UUID teamId,
                                                    @Valid @RequestBody ShareCollectionRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Sharing collection {} with user {} at {} for team {}",
                collectionId, request.sharedWithUserId(), request.permission(), teamId);
        return shareService.shareCollection(collectionId, teamId, userId, request);
    }

    /**
     * Returns all shares for a collection.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID from the request header
     * @return list of share responses
     */
    @GetMapping("/collections/{collectionId}/shares")
    public List<CollectionShareResponse> getCollectionShares(@PathVariable UUID collectionId,
                                                              @RequestHeader("X-Team-ID") UUID teamId) {
        return shareService.getCollectionShares(collectionId, teamId);
    }

    /**
     * Updates the permission level for an existing share.
     *
     * @param collectionId     the collection ID
     * @param sharedWithUserId the user whose permission is being updated
     * @param teamId           the team ID from the request header
     * @param request          the update request with the new permission level
     * @return the updated share response
     */
    @PutMapping("/collections/{collectionId}/shares/{sharedWithUserId}")
    public CollectionShareResponse updateSharePermission(@PathVariable UUID collectionId,
                                                          @PathVariable UUID sharedWithUserId,
                                                          @RequestHeader("X-Team-ID") UUID teamId,
                                                          @Valid @RequestBody UpdateSharePermissionRequest request) {
        log.info("Updating share for collection {} user {} to {} for team {}",
                collectionId, sharedWithUserId, request.permission(), teamId);
        return shareService.updateSharePermission(collectionId, sharedWithUserId, teamId, request);
    }

    /**
     * Revokes a user's access to a collection.
     *
     * @param collectionId     the collection ID
     * @param sharedWithUserId the user whose access is being revoked
     * @param teamId           the team ID from the request header
     */
    @DeleteMapping("/collections/{collectionId}/shares/{sharedWithUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeShare(@PathVariable UUID collectionId,
                             @PathVariable UUID sharedWithUserId,
                             @RequestHeader("X-Team-ID") UUID teamId) {
        log.info("Revoking share for collection {} user {} for team {}", collectionId, sharedWithUserId, teamId);
        shareService.revokeShare(collectionId, sharedWithUserId, teamId);
    }

    /**
     * Returns all collections shared with the current user.
     *
     * @return list of share responses for collections shared with the authenticated user
     */
    @GetMapping("/shared-with-me")
    public List<CollectionShareResponse> getSharedWithMe() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return shareService.getSharedWithUser(userId);
    }
}
