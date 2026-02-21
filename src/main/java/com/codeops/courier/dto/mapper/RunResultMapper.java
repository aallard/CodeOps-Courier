package com.codeops.courier.dto.mapper;

import com.codeops.courier.dto.response.RunIterationResponse;
import com.codeops.courier.dto.response.RunResultResponse;
import com.codeops.courier.entity.RunIteration;
import com.codeops.courier.entity.RunResult;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper for RunResult and RunIteration entities to response DTOs.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface RunResultMapper {

    /**
     * Maps a RunResult entity to a response DTO.
     *
     * @param entity the RunResult entity
     * @return the response DTO
     */
    RunResultResponse toResponse(RunResult entity);

    /**
     * Maps a RunIteration entity to a response DTO.
     *
     * @param entity the RunIteration entity
     * @return the response DTO
     */
    RunIterationResponse toIterationResponse(RunIteration entity);

    /**
     * Maps a list of RunIteration entities to response DTOs.
     *
     * @param entities the RunIteration entities
     * @return the list of response DTOs
     */
    List<RunIterationResponse> toIterationResponseList(List<RunIteration> entities);
}
