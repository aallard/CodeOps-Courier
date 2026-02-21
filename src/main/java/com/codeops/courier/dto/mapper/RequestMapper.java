package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.request.CreateRequestRequest;
import com.codeops.courier.dto.response.RequestResponse;
import com.codeops.courier.dto.response.RequestSummaryResponse;
import com.codeops.courier.entity.Request;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Request entity to/from DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface RequestMapper {

    /**
     * Maps a create request to a new Request entity.
     *
     * @param request the create request
     * @return the Request entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "folder", ignore = true)
    @Mapping(target = "headers", ignore = true)
    @Mapping(target = "params", ignore = true)
    @Mapping(target = "body", ignore = true)
    @Mapping(target = "auth", ignore = true)
    @Mapping(target = "scripts", ignore = true)
    @Mapping(target = "sortOrder", ignore = true)
    Request toEntity(CreateRequestRequest request);

    /**
     * Maps a Request entity to a full response DTO.
     * Nested headers/params/body/auth/scripts are assembled in the service layer.
     *
     * @param entity the Request entity
     * @return the response DTO
     */
    @Mapping(target = "folderId", source = "folder.id")
    @Mapping(target = "headers", ignore = true)
    @Mapping(target = "params", ignore = true)
    @Mapping(target = "body", ignore = true)
    @Mapping(target = "auth", ignore = true)
    @Mapping(target = "scripts", ignore = true)
    RequestResponse toResponse(Request entity);

    /**
     * Maps a Request entity to a summary response DTO.
     *
     * @param entity the Request entity
     * @return the summary response DTO
     */
    RequestSummaryResponse toSummaryResponse(Request entity);
}
