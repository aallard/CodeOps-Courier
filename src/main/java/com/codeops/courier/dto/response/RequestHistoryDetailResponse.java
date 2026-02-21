package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.HttpMethod;

import java.time.Instant;
import java.util.UUID;

public record RequestHistoryDetailResponse(
        UUID id,
        UUID userId,
        HttpMethod requestMethod,
        String requestUrl,
        String requestHeaders,
        String requestBody,
        int responseStatus,
        String responseHeaders,
        String responseBody,
        long responseSizeBytes,
        long responseTimeMs,
        String contentType,
        UUID collectionId,
        UUID requestId,
        UUID environmentId,
        Instant createdAt
) {}
