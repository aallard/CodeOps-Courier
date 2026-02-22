package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.CreateFolderRequest;
import com.codeops.courier.dto.request.ReorderFolderRequest;
import com.codeops.courier.dto.request.UpdateFolderRequest;
import com.codeops.courier.dto.response.FolderResponse;
import com.codeops.courier.dto.response.RequestSummaryResponse;
import com.codeops.courier.service.FolderService;
import com.codeops.courier.service.RequestService;
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
 * REST controller for managing folders within collections. Provides CRUD operations,
 * subfolder listing, request listing, move, and reorder capabilities.
 * All endpoints require authentication and the ADMIN role.
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/folders")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Folders", description = "Folder CRUD, move, reorder, and content listing")
public class FolderController {

    private final FolderService folderService;
    private final RequestService requestService;

    /**
     * Creates a new folder within a collection.
     *
     * @param teamId  the team ID from the request header
     * @param request the folder creation request
     * @return the created folder response
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderResponse createFolder(@RequestHeader("X-Team-ID") UUID teamId,
                                       @Valid @RequestBody CreateFolderRequest request) {
        log.info("Creating folder '{}' in collection {} for team {}", request.name(), request.collectionId(), teamId);
        return folderService.createFolder(teamId, request);
    }

    /**
     * Returns the detail of a specific folder.
     *
     * @param folderId the folder ID
     * @param teamId   the team ID from the request header
     * @return the folder response
     */
    @GetMapping("/{folderId}")
    public FolderResponse getFolder(@PathVariable UUID folderId,
                                    @RequestHeader("X-Team-ID") UUID teamId) {
        return folderService.getFolder(folderId, teamId);
    }

    /**
     * Returns the direct subfolders of a folder.
     *
     * @param folderId the parent folder ID
     * @param teamId   the team ID from the request header
     * @return list of subfolder responses
     */
    @GetMapping("/{folderId}/subfolders")
    public List<FolderResponse> getSubFolders(@PathVariable UUID folderId,
                                              @RequestHeader("X-Team-ID") UUID teamId) {
        return folderService.getSubFolders(folderId, teamId);
    }

    /**
     * Returns all requests within a folder.
     *
     * @param folderId the folder ID
     * @param teamId   the team ID from the request header
     * @return list of request summaries
     */
    @GetMapping("/{folderId}/requests")
    public List<RequestSummaryResponse> getRequestsInFolder(@PathVariable UUID folderId,
                                                            @RequestHeader("X-Team-ID") UUID teamId) {
        return requestService.getRequestsInFolder(folderId, teamId);
    }

    /**
     * Updates a folder's metadata.
     *
     * @param folderId the folder ID
     * @param teamId   the team ID from the request header
     * @param request  the update request
     * @return the updated folder response
     */
    @PutMapping("/{folderId}")
    public FolderResponse updateFolder(@PathVariable UUID folderId,
                                       @RequestHeader("X-Team-ID") UUID teamId,
                                       @Valid @RequestBody UpdateFolderRequest request) {
        log.info("Updating folder {} for team {}", folderId, teamId);
        return folderService.updateFolder(folderId, teamId, request);
    }

    /**
     * Deletes a folder and all its nested subfolders and requests.
     *
     * @param folderId the folder ID
     * @param teamId   the team ID from the request header
     */
    @DeleteMapping("/{folderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@PathVariable UUID folderId,
                             @RequestHeader("X-Team-ID") UUID teamId) {
        log.info("Deleting folder {} for team {}", folderId, teamId);
        folderService.deleteFolder(folderId, teamId);
    }

    /**
     * Moves a folder to a new parent folder, or to the collection root if no parent is specified.
     *
     * @param folderId          the folder ID to move
     * @param teamId            the team ID from the request header
     * @param newParentFolderId the target parent folder ID (null for root)
     * @return the updated folder response
     */
    @PutMapping("/{folderId}/move")
    public FolderResponse moveFolder(@PathVariable UUID folderId,
                                     @RequestHeader("X-Team-ID") UUID teamId,
                                     @RequestParam(required = false) UUID newParentFolderId) {
        log.info("Moving folder {} to parent {} for team {}", folderId, newParentFolderId, teamId);
        return folderService.moveFolder(folderId, teamId, newParentFolderId);
    }

    /**
     * Reorders folders by providing the desired order of folder IDs.
     *
     * @param teamId  the team ID from the request header
     * @param request the reorder request with ordered folder IDs
     * @return the reordered folder responses
     */
    @PutMapping("/reorder")
    public List<FolderResponse> reorderFolders(@RequestHeader("X-Team-ID") UUID teamId,
                                               @Valid @RequestBody ReorderFolderRequest request) {
        log.info("Reordering {} folders for team {}", request.folderIds().size(), teamId);
        return folderService.reorderFolders(teamId, request);
    }
}
