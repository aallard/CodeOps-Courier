package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.response.RequestScriptResponse;
import com.codeops.courier.entity.RequestScript;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper for RequestScript entity to response DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface RequestScriptMapper {

    /**
     * Maps a RequestScript entity to a response DTO.
     *
     * @param entity the RequestScript entity
     * @return the response DTO
     */
    RequestScriptResponse toResponse(RequestScript entity);

    /**
     * Maps a list of RequestScript entities to response DTOs.
     *
     * @param entities the RequestScript entities
     * @return the list of response DTOs
     */
    List<RequestScriptResponse> toResponseList(List<RequestScript> entities);
}
