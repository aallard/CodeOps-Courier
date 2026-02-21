package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.response.EnvironmentVariableResponse;
import com.codeops.courier.entity.EnvironmentVariable;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper for EnvironmentVariable entity to response DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface EnvironmentVariableMapper {

    /**
     * Maps an EnvironmentVariable entity to a response DTO.
     *
     * @param entity the EnvironmentVariable entity
     * @return the response DTO
     */
    @Mapping(target = "isSecret", source = "secret")
    @Mapping(target = "isEnabled", source = "enabled")
    EnvironmentVariableResponse toResponse(EnvironmentVariable entity);

    /**
     * Maps a list of EnvironmentVariable entities to response DTOs.
     *
     * @param entities the EnvironmentVariable entities
     * @return the list of response DTOs
     */
    List<EnvironmentVariableResponse> toResponseList(List<EnvironmentVariable> entities);
}
