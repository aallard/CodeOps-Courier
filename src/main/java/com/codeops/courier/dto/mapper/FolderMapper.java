package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.request.CreateFolderRequest;
import com.codeops.courier.dto.response.FolderResponse;
import com.codeops.courier.entity.Folder;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Folder entity to/from DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface FolderMapper {

    /**
     * Maps a create request to a new Folder entity.
     *
     * @param request the create request
     * @return the Folder entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "collection", ignore = true)
    @Mapping(target = "parentFolder", ignore = true)
    @Mapping(target = "subFolders", ignore = true)
    @Mapping(target = "requests", ignore = true)
    @Mapping(target = "preRequestScript", ignore = true)
    @Mapping(target = "postResponseScript", ignore = true)
    @Mapping(target = "authType", ignore = true)
    @Mapping(target = "authConfig", ignore = true)
    @Mapping(target = "sortOrder", ignore = true)
    Folder toEntity(CreateFolderRequest request);

    /**
     * Maps a Folder entity to a response DTO.
     *
     * @param entity the Folder entity
     * @return the response DTO
     */
    @Mapping(target = "collectionId", source = "collection.id")
    @Mapping(target = "parentFolderId", source = "parentFolder.id")
    @Mapping(target = "subFolderCount", ignore = true)
    @Mapping(target = "requestCount", ignore = true)
    FolderResponse toResponse(Folder entity);
}
