package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.response.RequestHistoryDetailResponse;
import com.codeops.courier.dto.response.RequestHistoryResponse;
import com.codeops.courier.entity.RequestHistory;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for RequestHistory entity to response DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface RequestHistoryMapper {

    /**
     * Maps a RequestHistory entity to a summary response DTO.
     *
     * @param entity the RequestHistory entity
     * @return the response DTO
     */
    RequestHistoryResponse toResponse(RequestHistory entity);

    /**
     * Maps a RequestHistory entity to a detail response DTO with full request/response data.
     *
     * @param entity the RequestHistory entity
     * @return the detail response DTO
     */
    RequestHistoryDetailResponse toDetailResponse(RequestHistory entity);
}
