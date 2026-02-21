package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.response.RequestBodyResponse;
import com.codeops.courier.entity.RequestBody;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for RequestBody entity to response DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface RequestBodyMapper {

    /**
     * Maps a RequestBody entity to a response DTO.
     *
     * @param entity the RequestBody entity
     * @return the response DTO
     */
    RequestBodyResponse toResponse(RequestBody entity);
}
