package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.response.GlobalVariableResponse;
import com.codeops.courier.entity.GlobalVariable;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper for GlobalVariable entity to response DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface GlobalVariableMapper {

    /**
     * Maps a GlobalVariable entity to a response DTO.
     *
     * @param entity the GlobalVariable entity
     * @return the response DTO
     */
    @Mapping(target = "isSecret", source = "secret")
    @Mapping(target = "isEnabled", source = "enabled")
    GlobalVariableResponse toResponse(GlobalVariable entity);

    /**
     * Maps a list of GlobalVariable entities to response DTOs.
     *
     * @param entities the GlobalVariable entities
     * @return the list of response DTOs
     */
    List<GlobalVariableResponse> toResponseList(List<GlobalVariable> entities);
}
