package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.HttpMethod;

import java.time.Instant;
import java.util.UUID;

public record RequestHistoryResponse(
        UUID id,
        UUID userId,
        HttpMethod requestMethod,
        String requestUrl,
        int responseStatus,
        long responseTimeMs,
        long responseSizeBytes,
        String contentType,
        UUID collectionId,
        UUID requestId,
        UUID environmentId,
        Instant createdAt
) {}
