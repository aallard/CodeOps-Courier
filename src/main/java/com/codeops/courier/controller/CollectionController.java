package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.CreateCollectionRequest;
import com.codeops.courier.dto.request.CreateForkRequest;
import com.codeops.courier.dto.request.UpdateCollectionRequest;
import com.codeops.courier.dto.response.*;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.security.SecurityUtils;
import com.codeops.courier.service.CollectionService;
import com.codeops.courier.service.ExportService;
import com.codeops.courier.service.FolderService;
import com.codeops.courier.service.ForkService;
import com.codeops.courier.service.MergeService;
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
 * REST controller for managing API collections. Provides CRUD operations,
 * duplication, forking, export in multiple formats, search, and folder tree retrieval.
 * All endpoints require authentication and the ADMIN role.
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/collections")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Collections", description = "Collection CRUD, forking, export, and search")
public class CollectionController {

    private final CollectionService collectionService;
    private final ForkService forkService;
    private final MergeService mergeService;
    private final ExportService exportService;
    private final FolderService folderService;

    /**
     * Creates a new collection for the authenticated user's team.
     *
     * @param teamId  the team ID from the request header
     * @param request the collection creation request
     * @return the created collection response
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionResponse createCollection(@RequestHeader("X-Team-ID") UUID teamId,
                                               @Valid @RequestBody CreateCollectionRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Creating collection '{}' for team {}", request.name(), teamId);
        return collectionService.createCollection(teamId, userId, request);
    }

    /**
     * Returns all collections accessible to the authenticated user within the team.
     *
     * @param teamId the team ID from the request header
     * @return list of collection summaries
     */
    @GetMapping
    public List<CollectionSummaryResponse> getCollections(@RequestHeader("X-Team-ID") UUID teamId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return collectionService.getCollections(teamId, userId);
    }

    /**
     * Returns paginated collections for the team.
     *
     * @param teamId the team ID from the request header
     * @param page   zero-based page number (default 0)
     * @param size   page size (default 20)
     * @return paginated collection summaries
     */
    @GetMapping("/paged")
    public PageResponse<CollectionSummaryResponse> getCollectionsPaged(
            @RequestHeader("X-Team-ID") UUID teamId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return collectionService.getCollectionsPaged(teamId, page, size);
    }

    /**
     * Searches collections by name within the team.
     *
     * @param teamId the team ID from the request header
     * @param query  the search query string
     * @return matching collection summaries
     */
    @GetMapping("/search")
    public List<CollectionSummaryResponse> searchCollections(@RequestHeader("X-Team-ID") UUID teamId,
                                                             @RequestParam String query) {
        return collectionService.searchCollections(teamId, query);
    }

    /**
     * Returns the full detail of a specific collection.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID from the request header
     * @return the collection response with full detail
     */
    @GetMapping("/{collectionId}")
    public CollectionResponse getCollection(@PathVariable UUID collectionId,
                                            @RequestHeader("X-Team-ID") UUID teamId) {
        return collectionService.getCollection(collectionId, teamId);
    }

    /**
     * Updates a collection's metadata.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID from the request header
     * @param request      the update request
     * @return the updated collection response
     */
    @PutMapping("/{collectionId}")
    public CollectionResponse updateCollection(@PathVariable UUID collectionId,
                                               @RequestHeader("X-Team-ID") UUID teamId,
                                               @Valid @RequestBody UpdateCollectionRequest request) {
        log.info("Updating collection {} for team {}", collectionId, teamId);
        return collectionService.updateCollection(collectionId, teamId, request);
    }

    /**
     * Deletes a collection and all its nested folders, requests, and components.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID from the request header
     */
    @DeleteMapping("/{collectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCollection(@PathVariable UUID collectionId,
                                 @RequestHeader("X-Team-ID") UUID teamId) {
        log.info("Deleting collection {} for team {}", collectionId, teamId);
        collectionService.deleteCollection(collectionId, teamId);
    }

    /**
     * Duplicates a collection with all its contents.
     *
     * @param collectionId the source collection ID
     * @param teamId       the team ID from the request header
     * @return the duplicated collection response
     */
    @PostMapping("/{collectionId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionResponse duplicateCollection(@PathVariable UUID collectionId,
                                                  @RequestHeader("X-Team-ID") UUID teamId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Duplicating collection {} for team {}", collectionId, teamId);
        return collectionService.duplicateCollection(collectionId, teamId, userId);
    }

    /**
     * Creates a fork of a collection.
     *
     * @param collectionId the source collection ID
     * @param teamId       the team ID from the request header
     * @param request      the fork creation request with label
     * @return the fork response
     */
    @PostMapping("/{collectionId}/fork")
    @ResponseStatus(HttpStatus.CREATED)
    public ForkResponse forkCollection(@PathVariable UUID collectionId,
                                       @RequestHeader("X-Team-ID") UUID teamId,
                                       @Valid @RequestBody CreateForkRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Forking collection {} for team {}", collectionId, teamId);
        return forkService.forkCollection(collectionId, teamId, userId, request);
    }

    /**
     * Returns all forks of a collection.
     *
     * @param collectionId the source collection ID
     * @param teamId       the team ID from the request header
     * @return list of fork responses
     */
    @GetMapping("/{collectionId}/forks")
    public List<ForkResponse> getCollectionForks(@PathVariable UUID collectionId,
                                                 @RequestHeader("X-Team-ID") UUID teamId) {
        return forkService.getForksForCollection(collectionId, teamId);
    }

    /**
     * Exports a collection in the specified format.
     *
     * @param collectionId the collection ID
     * @param format       the export format: "postman", "openapi", or "native"
     * @param teamId       the team ID from the request header
     * @return the export response with format, content, and filename
     */
    @GetMapping("/{collectionId}/export/{format}")
    public ExportCollectionResponse exportCollection(@PathVariable UUID collectionId,
                                                     @PathVariable String format,
                                                     @RequestHeader("X-Team-ID") UUID teamId) {
        log.info("Exporting collection {} as {} for team {}", collectionId, format, teamId);
        return switch (format.toLowerCase()) {
            case "postman" -> exportService.exportAsPostman(collectionId, teamId);
            case "openapi" -> exportService.exportAsOpenApi(collectionId, teamId);
            case "native" -> exportService.exportAsNative(collectionId, teamId);
            default -> throw new ValidationException("Unsupported export format: " + format);
        };
    }

    /**
     * Returns the folder tree structure of a collection including nested folders and request summaries.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID from the request header
     * @return the hierarchical folder tree
     */
    @GetMapping("/{collectionId}/tree")
    public List<FolderTreeResponse> getCollectionTree(@PathVariable UUID collectionId,
                                                      @RequestHeader("X-Team-ID") UUID teamId) {
        return folderService.getFolderTree(collectionId, teamId);
    }
}
