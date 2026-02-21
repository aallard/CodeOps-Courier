package com.codeops.courier.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartCollectionRunRequest(
        @NotNull UUID collectionId,
        UUID environmentId,
        @Min(1) @Max(1000) int iterationCount,
        @Min(0) @Max(60000) int delayBetweenRequestsMs,
        String dataFilename,
        String dataContent
) {}
