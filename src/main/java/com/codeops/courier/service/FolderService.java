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
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.CollectionRepository;
import com.codeops.courier.repository.FolderRepository;
import com.codeops.courier.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service for managing folders within API collections including CRUD operations,
 * tree building, reordering, and move operations with circular reference prevention.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class FolderService {

    private final FolderRepository folderRepository;
    private final CollectionRepository collectionRepository;
    private final RequestRepository requestRepository;
    private final FolderMapper folderMapper;
    private final RequestMapper requestMapper;

    /**
     * Creates a new folder in a collection.
     * Supports root-level folders (parentFolderId=null) and nested folders (unlimited depth).
     *
     * @param teamId  the team owning the collection
     * @param request the folder creation request
     * @return the created folder response with computed counts
     * @throws NotFoundException   if the collection or parent folder is not found
     * @throws ValidationException if the parent folder belongs to a different collection
     */
    public FolderResponse createFolder(UUID teamId, CreateFolderRequest request) {
        Collection collection = findAndValidateCollection(request.collectionId(), teamId);

        Folder parentFolder = null;
        if (request.parentFolderId() != null) {
            parentFolder = folderRepository.findById(request.parentFolderId())
                    .orElseThrow(() -> new NotFoundException("Parent folder not found: " + request.parentFolderId()));
            if (!parentFolder.getCollection().getId().equals(request.collectionId())) {
                throw new ValidationException("Parent folder belongs to a different collection");
            }
        }

        Folder entity = folderMapper.toEntity(request);
        entity.setCollection(collection);
        entity.setParentFolder(parentFolder);

        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        } else {
            entity.setSortOrder(computeNextFolderSortOrder(request.collectionId(), request.parentFolderId()));
        }

        Folder saved = folderRepository.save(entity);
        log.info("Created folder '{}' in collection {}", saved.getName(), request.collectionId());
        return toFolderResponse(saved);
    }

    /**
     * Gets a single folder by ID with computed subfolder and request counts.
     *
     * @param folderId the folder ID
     * @param teamId   the team ID for access validation
     * @return the folder response with computed counts
     * @throws NotFoundException if the folder is not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public FolderResponse getFolder(UUID folderId, UUID teamId) {
        Folder folder = findFolderAndValidateTeam(folderId, teamId);
        return toFolderResponse(folder);
    }

    /**
     * Gets the folder tree for a collection — returns root-level folders with nested subfolders and requests.
     * This is the primary method for building the sidebar tree view.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID for access validation
     * @return the folder tree with nested subfolders and request summaries
     * @throws NotFoundException if the collection is not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public List<FolderTreeResponse> getFolderTree(UUID collectionId, UUID teamId) {
        findAndValidateCollection(collectionId, teamId);
        List<Folder> rootFolders = folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId);
        return rootFolders.stream().map(this::buildTreeNode).toList();
    }

    /**
     * Gets direct child folders of a parent folder.
     *
     * @param parentFolderId the parent folder ID
     * @param teamId         the team ID for access validation
     * @return the list of child folder responses with computed counts
     * @throws NotFoundException if the parent folder is not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public List<FolderResponse> getSubFolders(UUID parentFolderId, UUID teamId) {
        findFolderAndValidateTeam(parentFolderId, teamId);
        List<Folder> subFolders = folderRepository.findByParentFolderIdOrderBySortOrder(parentFolderId);
        return subFolders.stream().map(this::toFolderResponse).toList();
    }

    /**
     * Gets root-level folders for a collection (folders with no parent).
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID for access validation
     * @return the list of root folder responses with computed counts
     * @throws NotFoundException if the collection is not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public List<FolderResponse> getRootFolders(UUID collectionId, UUID teamId) {
        findAndValidateCollection(collectionId, teamId);
        List<Folder> rootFolders = folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId);
        return rootFolders.stream().map(this::toFolderResponse).toList();
    }

    /**
     * Updates a folder with partial update semantics — only non-null fields are applied.
     * If parentFolderId changes, validates no circular reference and same collection.
     *
     * @param folderId the folder ID
     * @param teamId   the team ID for access validation
     * @param request  the update request with optional fields
     * @return the updated folder response
     * @throws NotFoundException   if the folder or new parent is not found
     * @throws ValidationException if the update would create a circular reference or cross-collection move
     */
    public FolderResponse updateFolder(UUID folderId, UUID teamId, UpdateFolderRequest request) {
        Folder folder = findFolderAndValidateTeam(folderId, teamId);

        if (request.name() != null) {
            folder.setName(request.name());
        }
        if (request.description() != null) {
            folder.setDescription(request.description());
        }
        if (request.sortOrder() != null) {
            folder.setSortOrder(request.sortOrder());
        }
        if (request.preRequestScript() != null) {
            folder.setPreRequestScript(request.preRequestScript());
        }
        if (request.postResponseScript() != null) {
            folder.setPostResponseScript(request.postResponseScript());
        }
        if (request.authType() != null) {
            folder.setAuthType(request.authType());
        }
        if (request.authConfig() != null) {
            folder.setAuthConfig(request.authConfig());
        }

        if (request.parentFolderId() != null) {
            if (request.parentFolderId().equals(folderId)) {
                throw new ValidationException("A folder cannot be its own parent");
            }
            Folder newParent = folderRepository.findById(request.parentFolderId())
                    .orElseThrow(() -> new NotFoundException("Parent folder not found: " + request.parentFolderId()));
            if (!newParent.getCollection().getId().equals(folder.getCollection().getId())) {
                throw new ValidationException("Cannot move folder to a different collection");
            }
            if (isDescendant(folderId, request.parentFolderId())) {
                throw new ValidationException("Cannot move folder under its own descendant (circular reference)");
            }
            folder.setParentFolder(newParent);
        }

        Folder saved = folderRepository.save(folder);
        log.info("Updated folder '{}'", saved.getName());
        return toFolderResponse(saved);
    }

    /**
     * Deletes a folder and all its contents via JPA cascade.
     * Subfolders and requests are deleted recursively through cascade configuration.
     *
     * @param folderId the folder ID
     * @param teamId   the team ID for access validation
     * @throws NotFoundException if the folder is not found or belongs to a different team
     */
    public void deleteFolder(UUID folderId, UUID teamId) {
        Folder folder = findFolderAndValidateTeam(folderId, teamId);
        folderRepository.delete(folder);
        log.info("Deleted folder '{}' ({})", folder.getName(), folderId);
    }

    /**
     * Reorders folders within the same parent. Accepts an ordered list of folder IDs
     * representing the new sort order.
     *
     * @param teamId  the team ID for access validation
     * @param request the reorder request containing the ordered folder IDs
     * @return the reordered folder responses
     * @throws NotFoundException   if any folder ID is not found
     * @throws ValidationException if folders belong to different parents
     */
    public List<FolderResponse> reorderFolders(UUID teamId, ReorderFolderRequest request) {
        List<Folder> folders = new ArrayList<>();
        UUID firstParentId = null;
        boolean firstParentResolved = false;

        for (int i = 0; i < request.folderIds().size(); i++) {
            UUID id = request.folderIds().get(i);
            Folder folder = findFolderAndValidateTeam(id, teamId);

            UUID currentParentId = folder.getParentFolder() != null ? folder.getParentFolder().getId() : null;
            if (!firstParentResolved) {
                firstParentId = currentParentId;
                firstParentResolved = true;
            } else if (!Objects.equals(firstParentId, currentParentId)) {
                throw new ValidationException("All folders must belong to the same parent");
            }

            folder.setSortOrder(i);
            folders.add(folder);
        }

        List<Folder> saved = folderRepository.saveAll(folders);
        log.info("Reordered {} folders", saved.size());
        return saved.stream().map(this::toFolderResponse).toList();
    }

    /**
     * Moves a folder to a different parent folder or to the root level.
     *
     * @param folderId          the folder ID to move
     * @param teamId            the team ID for access validation
     * @param newParentFolderId the target parent folder ID (null to move to root)
     * @return the moved folder response
     * @throws NotFoundException   if the folder or target parent is not found
     * @throws ValidationException if the move would create a circular reference or cross-collection move
     */
    public FolderResponse moveFolder(UUID folderId, UUID teamId, UUID newParentFolderId) {
        Folder folder = findFolderAndValidateTeam(folderId, teamId);

        if (newParentFolderId == null) {
            folder.setParentFolder(null);
            folder.setSortOrder(computeNextFolderSortOrder(folder.getCollection().getId(), null));
        } else {
            if (newParentFolderId.equals(folderId)) {
                throw new ValidationException("A folder cannot be its own parent");
            }
            Folder newParent = folderRepository.findById(newParentFolderId)
                    .orElseThrow(() -> new NotFoundException("Target folder not found: " + newParentFolderId));
            if (!newParent.getCollection().getId().equals(folder.getCollection().getId())) {
                throw new ValidationException("Cannot move folder to a different collection");
            }
            if (isDescendant(folderId, newParentFolderId)) {
                throw new ValidationException("Cannot move folder under its own descendant (circular reference)");
            }
            folder.setParentFolder(newParent);
            folder.setSortOrder(computeNextFolderSortOrder(folder.getCollection().getId(), newParentFolderId));
        }

        Folder saved = folderRepository.save(folder);
        log.info("Moved folder '{}' to parent {}", saved.getName(), newParentFolderId);
        return toFolderResponse(saved);
    }

    /**
     * Checks if the target folder is a descendant of the source folder.
     * Walks up the parent chain from the target to detect circular references.
     *
     * @param sourceFolderId the folder being moved
     * @param targetFolderId the potential new parent
     * @return true if the target is a descendant of the source (would create a cycle)
     */
    private boolean isDescendant(UUID sourceFolderId, UUID targetFolderId) {
        UUID currentId = targetFolderId;
        while (currentId != null) {
            if (currentId.equals(sourceFolderId)) {
                return true;
            }
            Folder current = folderRepository.findById(currentId).orElse(null);
            if (current == null || current.getParentFolder() == null) {
                return false;
            }
            currentId = current.getParentFolder().getId();
        }
        return false;
    }

    /**
     * Finds a folder by ID and validates team ownership through the collection chain.
     *
     * @param folderId the folder ID
     * @param teamId   the expected team ID
     * @return the folder entity
     * @throws NotFoundException if not found or wrong team
     */
    Folder findFolderAndValidateTeam(UUID folderId, UUID teamId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new NotFoundException("Folder not found: " + folderId));
        if (!folder.getCollection().getTeamId().equals(teamId)) {
            throw new NotFoundException("Folder not found: " + folderId);
        }
        return folder;
    }

    private Collection findAndValidateCollection(UUID collectionId, UUID teamId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new NotFoundException("Collection not found: " + collectionId));
        if (!collection.getTeamId().equals(teamId)) {
            throw new NotFoundException("Collection not found: " + collectionId);
        }
        return collection;
    }

    private FolderResponse toFolderResponse(Folder folder) {
        int subFolderCount = (int) folderRepository.countByParentFolderId(folder.getId());
        int requestCount = (int) requestRepository.countByFolderId(folder.getId());
        return new FolderResponse(
                folder.getId(),
                folder.getCollection().getId(),
                folder.getParentFolder() != null ? folder.getParentFolder().getId() : null,
                folder.getName(),
                folder.getDescription(),
                folder.getSortOrder(),
                folder.getPreRequestScript(),
                folder.getPostResponseScript(),
                folder.getAuthType(),
                folder.getAuthConfig(),
                subFolderCount,
                requestCount,
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }

    private FolderTreeResponse buildTreeNode(Folder folder) {
        List<Folder> subFolders = folderRepository.findByParentFolderIdOrderBySortOrder(folder.getId());
        List<Request> requests = requestRepository.findByFolderIdOrderBySortOrder(folder.getId());

        List<FolderTreeResponse> childNodes = subFolders.stream()
                .map(this::buildTreeNode)
                .toList();
        List<RequestSummaryResponse> requestSummaries = requests.stream()
                .map(requestMapper::toSummaryResponse)
                .toList();

        return new FolderTreeResponse(
                folder.getId(),
                folder.getName(),
                folder.getSortOrder(),
                childNodes,
                requestSummaries
        );
    }

    private int computeNextFolderSortOrder(UUID collectionId, UUID parentFolderId) {
        List<Folder> siblings;
        if (parentFolderId == null) {
            siblings = folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId);
        } else {
            siblings = folderRepository.findByParentFolderIdOrderBySortOrder(parentFolderId);
        }
        return siblings.stream().mapToInt(Folder::getSortOrder).max().orElse(-1) + 1;
    }
}
