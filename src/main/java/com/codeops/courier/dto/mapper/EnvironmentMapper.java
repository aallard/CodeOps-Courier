package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.request.CreateEnvironmentRequest;
import com.codeops.courier.dto.response.EnvironmentResponse;
import com.codeops.courier.entity.Environment;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Environment entity to/from DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface EnvironmentMapper {

    /**
     * Maps a create request to a new Environment entity.
     *
     * @param request the create request
     * @return the Environment entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "teamId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "active", constant = "false")
    @Mapping(target = "variables", ignore = true)
    Environment toEntity(CreateEnvironmentRequest request);

    /**
     * Maps an Environment entity to a response DTO.
     *
     * @param entity the Environment entity
     * @return the response DTO
     */
    @Mapping(target = "variableCount", ignore = true)
    @Mapping(target = "isActive", source = "active")
    EnvironmentResponse toResponse(Environment entity);
}
