package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.response.RequestAuthResponse;
import com.codeops.courier.entity.RequestAuth;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for RequestAuth entity to response DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface RequestAuthMapper {

    /**
     * Maps a RequestAuth entity to a response DTO.
     *
     * @param entity the RequestAuth entity
     * @return the response DTO
     */
    RequestAuthResponse toResponse(RequestAuth entity);
}
