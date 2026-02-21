package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.response.RequestHeaderResponse;
import com.codeops.courier.entity.RequestHeader;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper for RequestHeader entity to response DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface RequestHeaderMapper {

    /**
     * Maps a RequestHeader entity to a response DTO.
     *
     * @param entity the RequestHeader entity
     * @return the response DTO
     */
    @Mapping(target = "isEnabled", source = "enabled")
    RequestHeaderResponse toResponse(RequestHeader entity);

    /**
     * Maps a list of RequestHeader entities to response DTOs.
     *
     * @param entities the RequestHeader entities
     * @return the list of response DTOs
     */
    List<RequestHeaderResponse> toResponseList(List<RequestHeader> entities);
}
