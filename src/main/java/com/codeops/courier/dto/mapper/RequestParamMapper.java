package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.response.RequestParamResponse;
import com.codeops.courier.entity.RequestParam;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper for RequestParam entity to response DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface RequestParamMapper {

    /**
     * Maps a RequestParam entity to a response DTO.
     *
     * @param entity the RequestParam entity
     * @return the response DTO
     */
    @Mapping(target = "isEnabled", source = "enabled")
    RequestParamResponse toResponse(RequestParam entity);

    /**
     * Maps a list of RequestParam entities to response DTOs.
     *
     * @param entities the RequestParam entities
     * @return the list of response DTOs
     */
    List<RequestParamResponse> toResponseList(List<RequestParam> entities);
}
