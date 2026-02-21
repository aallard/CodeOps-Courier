package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.request.CreateCollectionRequest;
import com.codeops.courier.dto.response.CollectionResponse;
import com.codeops.courier.dto.response.CollectionSummaryResponse;
import com.codeops.courier.entity.Collection;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Collection entity to/from DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface CollectionMapper {

    /**
     * Maps a create request to a new Collection entity.
     *
     * @param request the create request
     * @return the Collection entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "teamId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "shared", constant = "false")
    @Mapping(target = "preRequestScript", ignore = true)
    @Mapping(target = "postResponseScript", ignore = true)
    @Mapping(target = "folders", ignore = true)
    @Mapping(target = "variables", ignore = true)
    Collection toEntity(CreateCollectionRequest request);

    /**
     * Maps a Collection entity to a full response DTO.
     *
     * @param entity the Collection entity
     * @return the response DTO
     */
    @Mapping(target = "folderCount", ignore = true)
    @Mapping(target = "requestCount", ignore = true)
    @Mapping(target = "isShared", source = "shared")
    CollectionResponse toResponse(Collection entity);

    /**
     * Maps a Collection entity to a summary response DTO.
     *
     * @param entity the Collection entity
     * @return the summary response DTO
     */
    @Mapping(target = "folderCount", ignore = true)
    @Mapping(target = "requestCount", ignore = true)
    @Mapping(target = "isShared", source = "shared")
    CollectionSummaryResponse toSummaryResponse(Collection entity);
}
