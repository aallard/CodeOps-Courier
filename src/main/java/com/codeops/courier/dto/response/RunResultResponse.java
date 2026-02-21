package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.RunStatus;

import java.time.Instant;
import java.util.UUID;

public record RunResultResponse(
        UUID id,
        UUID teamId,
        UUID collectionId,
        UUID environmentId,
        RunStatus status,
        int totalRequests,
        int passedRequests,
        int failedRequests,
        int totalAssertions,
        int passedAssertions,
        int failedAssertions,
        long totalDurationMs,
        int iterationCount,
        int delayBetweenRequestsMs,
        String dataFilename,
        Instant startedAt,
        Instant completedAt,
        UUID startedByUserId,
        Instant createdAt
) {}
