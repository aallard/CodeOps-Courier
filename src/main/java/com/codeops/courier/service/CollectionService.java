package com.codeops.courier.service;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.mapper.CollectionMapper;
import com.codeops.courier.dto.request.CreateCollectionRequest;
import com.codeops.courier.dto.request.UpdateCollectionRequest;
import com.codeops.courier.dto.response.CollectionResponse;
import com.codeops.courier.dto.response.CollectionSummaryResponse;
import com.codeops.courier.dto.response.PageResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.EnvironmentVariable;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.RequestAuth;
import com.codeops.courier.entity.RequestBody;
import com.codeops.courier.entity.RequestHeader;
import com.codeops.courier.entity.RequestParam;
import com.codeops.courier.entity.RequestScript;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.CollectionRepository;
import com.codeops.courier.repository.CollectionShareRepository;
import com.codeops.courier.repository.FolderRepository;
import com.codeops.courier.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing API collections including CRUD operations,
 * duplication, and search functionality.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final FolderRepository folderRepository;
    private final RequestRepository requestRepository;
    private final CollectionShareRepository collectionShareRepository;
    private final CollectionMapper collectionMapper;

    /**
     * Creates a new collection for the given team.
     * Validates name uniqueness within the team.
     *
     * @param teamId  the team owning the collection
     * @param userId  the user creating the collection
     * @param request the creation request
     * @return the created collection response
     * @throws ValidationException if a collection with the same name exists in the team
     */
    public CollectionResponse createCollection(UUID teamId, UUID userId, CreateCollectionRequest request) {
        if (collectionRepository.existsByTeamIdAndName(teamId, request.name())) {
            throw new ValidationException("Collection with name '" + request.name() + "' already exists in this team");
        }

        Collection entity = collectionMapper.toEntity(request);
        entity.setTeamId(teamId);
        entity.setCreatedBy(userId);

        Collection saved = collectionRepository.save(entity);
        log.info("Created collection '{}' for team {}", saved.getName(), teamId);
        return toResponse(saved, 0, 0);
    }

    /**
     * Gets a single collection by ID, validating team membership.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID for access validation
     * @return the collection response with computed counts
     * @throws NotFoundException if the collection does not exist or belongs to a different team
     */
    @Transactional(readOnly = true)
    public CollectionResponse getCollection(UUID collectionId, UUID teamId) {
        Collection entity = findCollectionByIdAndTeam(collectionId, teamId);
        return enrichResponse(entity);
    }

    /**
     * Lists all collections accessible to a user (owned by team + shared with user).
     *
     * @param teamId the team ID
     * @param userId the user ID for shared collection lookup
     * @return list of collection summaries
     */
    @Transactional(readOnly = true)
    public List<CollectionSummaryResponse> getCollections(UUID teamId, UUID userId) {
        List<Collection> owned = collectionRepository.findByTeamId(teamId);

        Map<UUID, Collection> merged = new LinkedHashMap<>();
        owned.forEach(c -> merged.put(c.getId(), c));

        collectionShareRepository.findBySharedWithUserId(userId).forEach(share -> {
            Collection sharedCollection = share.getCollection();
            merged.putIfAbsent(sharedCollection.getId(), sharedCollection);
        });

        return merged.values().stream()
                .map(this::enrichSummaryResponse)
                .toList();
    }

    /**
     * Lists collections with pagination.
     *
     * @param teamId the team ID
     * @param page   zero-based page number
     * @param size   page size
     * @return paged collection summaries
     */
    @Transactional(readOnly = true)
    public PageResponse<CollectionSummaryResponse> getCollectionsPaged(UUID teamId, int page, int size) {
        int clampedSize = Math.min(size, AppConstants.MAX_PAGE_SIZE);
        Page<Collection> pagedResult = collectionRepository.findByTeamId(teamId, PageRequest.of(page, clampedSize));

        List<CollectionSummaryResponse> content = pagedResult.getContent().stream()
                .map(this::enrichSummaryResponse)
                .toList();

        return new PageResponse<>(
                content,
                pagedResult.getNumber(),
                pagedResult.getSize(),
                pagedResult.getTotalElements(),
                pagedResult.getTotalPages(),
                pagedResult.isLast()
        );
    }

    /**
     * Updates an existing collection. Only non-null fields in the request are applied.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID for access validation
     * @param request      the update request with optional fields
     * @return the updated collection response
     * @throws NotFoundException   if the collection does not exist
     * @throws ValidationException if renaming would create a duplicate
     */
    public CollectionResponse updateCollection(UUID collectionId, UUID teamId, UpdateCollectionRequest request) {
        Collection entity = findCollectionByIdAndTeam(collectionId, teamId);

        if (request.name() != null) {
            if (!request.name().equals(entity.getName()) && collectionRepository.existsByTeamIdAndName(teamId, request.name())) {
                throw new ValidationException("Collection with name '" + request.name() + "' already exists in this team");
            }
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.preRequestScript() != null) {
            entity.setPreRequestScript(request.preRequestScript());
        }
        if (request.postResponseScript() != null) {
            entity.setPostResponseScript(request.postResponseScript());
        }
        if (request.authType() != null) {
            entity.setAuthType(request.authType());
        }
        if (request.authConfig() != null) {
            entity.setAuthConfig(request.authConfig());
        }

        Collection saved = collectionRepository.save(entity);
        log.info("Updated collection '{}'", saved.getName());
        return enrichResponse(saved);
    }

    /**
     * Deletes a collection and all its contents via cascade.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID for access validation
     * @throws NotFoundException if the collection does not exist
     */
    public void deleteCollection(UUID collectionId, UUID teamId) {
        Collection entity = findCollectionByIdAndTeam(collectionId, teamId);
        collectionRepository.delete(entity);
        log.info("Deleted collection '{}' ({})", entity.getName(), collectionId);
    }

    /**
     * Duplicates a collection with deep copy of all folders, requests, and components.
     *
     * @param collectionId the source collection ID
     * @param teamId       the team ID
     * @param userId       the user performing the duplication
     * @return the new duplicated collection response
     * @throws NotFoundException if the source collection does not exist
     */
    public CollectionResponse duplicateCollection(UUID collectionId, UUID teamId, UUID userId) {
        Collection source = findCollectionByIdAndTeam(collectionId, teamId);

        Collection copy = new Collection();
        copy.setTeamId(teamId);
        copy.setCreatedBy(userId);
        copy.setName(source.getName() + " (Copy)");
        copy.setDescription(source.getDescription());
        copy.setPreRequestScript(source.getPreRequestScript());
        copy.setPostResponseScript(source.getPostResponseScript());
        copy.setAuthType(source.getAuthType());
        copy.setAuthConfig(source.getAuthConfig());
        copy.setShared(false);

        Collection savedCopy = collectionRepository.save(copy);

        List<Folder> rootFolders = folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId);
        for (Folder rootFolder : rootFolders) {
            deepCopyFolder(rootFolder, savedCopy, null);
        }

        for (EnvironmentVariable sourceVar : source.getVariables()) {
            EnvironmentVariable varCopy = new EnvironmentVariable();
            varCopy.setVariableKey(sourceVar.getVariableKey());
            varCopy.setVariableValue(sourceVar.getVariableValue());
            varCopy.setSecret(sourceVar.isSecret());
            varCopy.setEnabled(sourceVar.isEnabled());
            varCopy.setScope(sourceVar.getScope());
            varCopy.setCollection(savedCopy);
            savedCopy.getVariables().add(varCopy);
        }

        Collection result = collectionRepository.save(savedCopy);
        log.info("Duplicated collection '{}' as '{}'", source.getName(), result.getName());
        return enrichResponse(result);
    }

    /**
     * Searches collections by name with case-insensitive partial matching.
     *
     * @param teamId the team ID
     * @param query  the search query
     * @return matching collection summaries
     */
    @Transactional(readOnly = true)
    public List<CollectionSummaryResponse> searchCollections(UUID teamId, String query) {
        return collectionRepository.findByTeamIdAndNameContainingIgnoreCase(teamId, query).stream()
                .map(this::enrichSummaryResponse)
                .toList();
    }

    /**
     * Finds a collection by ID and validates team ownership.
     *
     * @param collectionId the collection ID
     * @param teamId       the expected team ID
     * @return the collection entity
     * @throws NotFoundException if not found or wrong team
     */
    Collection findCollectionByIdAndTeam(UUID collectionId, UUID teamId) {
        Collection entity = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new NotFoundException("Collection not found: " + collectionId));
        if (!entity.getTeamId().equals(teamId)) {
            throw new NotFoundException("Collection not found: " + collectionId);
        }
        return entity;
    }

    private CollectionResponse toResponse(Collection entity, int folderCount, int requestCount) {
        return new CollectionResponse(
                entity.getId(),
                entity.getTeamId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPreRequestScript(),
                entity.getPostResponseScript(),
                entity.getAuthType(),
                entity.getAuthConfig(),
                entity.isShared(),
                entity.getCreatedBy(),
                folderCount,
                requestCount,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private CollectionResponse enrichResponse(Collection entity) {
        int folderCount = (int) folderRepository.countByCollectionId(entity.getId());
        int requestCount = countTotalRequests(entity.getId());
        return toResponse(entity, folderCount, requestCount);
    }

    private CollectionSummaryResponse enrichSummaryResponse(Collection entity) {
        int folderCount = (int) folderRepository.countByCollectionId(entity.getId());
        int requestCount = countTotalRequests(entity.getId());
        return new CollectionSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.isShared(),
                folderCount,
                requestCount,
                entity.getUpdatedAt()
        );
    }

    private int countTotalRequests(UUID collectionId) {
        List<Folder> folders = folderRepository.findByCollectionIdOrderBySortOrder(collectionId);
        return folders.stream()
                .mapToInt(f -> (int) requestRepository.countByFolderId(f.getId()))
                .sum();
    }

    private void deepCopyFolder(Folder source, Collection targetCollection, Folder parentFolder) {
        Folder folderCopy = new Folder();
        folderCopy.setName(source.getName());
        folderCopy.setDescription(source.getDescription());
        folderCopy.setSortOrder(source.getSortOrder());
        folderCopy.setPreRequestScript(source.getPreRequestScript());
        folderCopy.setPostResponseScript(source.getPostResponseScript());
        folderCopy.setAuthType(source.getAuthType());
        folderCopy.setAuthConfig(source.getAuthConfig());
        folderCopy.setCollection(targetCollection);
        folderCopy.setParentFolder(parentFolder);

        Folder savedFolder = folderRepository.save(folderCopy);

        for (Request sourceReq : source.getRequests()) {
            deepCopyRequest(sourceReq, savedFolder);
        }

        for (Folder subFolder : source.getSubFolders()) {
            deepCopyFolder(subFolder, targetCollection, savedFolder);
        }
    }

    private void deepCopyRequest(Request source, Folder targetFolder) {
        Request reqCopy = new Request();
        reqCopy.setName(source.getName());
        reqCopy.setDescription(source.getDescription());
        reqCopy.setMethod(source.getMethod());
        reqCopy.setUrl(source.getUrl());
        reqCopy.setSortOrder(source.getSortOrder());
        reqCopy.setFolder(targetFolder);

        for (RequestHeader h : source.getHeaders()) {
            RequestHeader hCopy = new RequestHeader();
            hCopy.setHeaderKey(h.getHeaderKey());
            hCopy.setHeaderValue(h.getHeaderValue());
            hCopy.setDescription(h.getDescription());
            hCopy.setEnabled(h.isEnabled());
            hCopy.setRequest(reqCopy);
            reqCopy.getHeaders().add(hCopy);
        }

        for (RequestParam p : source.getParams()) {
            RequestParam pCopy = new RequestParam();
            pCopy.setParamKey(p.getParamKey());
            pCopy.setParamValue(p.getParamValue());
            pCopy.setDescription(p.getDescription());
            pCopy.setEnabled(p.isEnabled());
            pCopy.setRequest(reqCopy);
            reqCopy.getParams().add(pCopy);
        }

        if (source.getBody() != null) {
            RequestBody bCopy = new RequestBody();
            bCopy.setBodyType(source.getBody().getBodyType());
            bCopy.setRawContent(source.getBody().getRawContent());
            bCopy.setFormData(source.getBody().getFormData());
            bCopy.setGraphqlQuery(source.getBody().getGraphqlQuery());
            bCopy.setGraphqlVariables(source.getBody().getGraphqlVariables());
            bCopy.setBinaryFileName(source.getBody().getBinaryFileName());
            bCopy.setRequest(reqCopy);
            reqCopy.setBody(bCopy);
        }

        if (source.getAuth() != null) {
            RequestAuth aCopy = new RequestAuth();
            aCopy.setAuthType(source.getAuth().getAuthType());
            aCopy.setApiKeyHeader(source.getAuth().getApiKeyHeader());
            aCopy.setApiKeyValue(source.getAuth().getApiKeyValue());
            aCopy.setApiKeyAddTo(source.getAuth().getApiKeyAddTo());
            aCopy.setBearerToken(source.getAuth().getBearerToken());
            aCopy.setBasicUsername(source.getAuth().getBasicUsername());
            aCopy.setBasicPassword(source.getAuth().getBasicPassword());
            aCopy.setOauth2GrantType(source.getAuth().getOauth2GrantType());
            aCopy.setOauth2AuthUrl(source.getAuth().getOauth2AuthUrl());
            aCopy.setOauth2TokenUrl(source.getAuth().getOauth2TokenUrl());
            aCopy.setOauth2ClientId(source.getAuth().getOauth2ClientId());
            aCopy.setOauth2ClientSecret(source.getAuth().getOauth2ClientSecret());
            aCopy.setOauth2Scope(source.getAuth().getOauth2Scope());
            aCopy.setOauth2CallbackUrl(source.getAuth().getOauth2CallbackUrl());
            aCopy.setOauth2AccessToken(source.getAuth().getOauth2AccessToken());
            aCopy.setJwtSecret(source.getAuth().getJwtSecret());
            aCopy.setJwtPayload(source.getAuth().getJwtPayload());
            aCopy.setJwtAlgorithm(source.getAuth().getJwtAlgorithm());
            aCopy.setRequest(reqCopy);
            reqCopy.setAuth(aCopy);
        }

        for (RequestScript s : source.getScripts()) {
            RequestScript sCopy = new RequestScript();
            sCopy.setScriptType(s.getScriptType());
            sCopy.setContent(s.getContent());
            sCopy.setRequest(reqCopy);
            reqCopy.getScripts().add(sCopy);
        }

        targetFolder.getRequests().add(reqCopy);
    }
}
