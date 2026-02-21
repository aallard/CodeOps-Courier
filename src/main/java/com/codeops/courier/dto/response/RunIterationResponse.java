package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.HttpMethod;

import java.util.UUID;

public record RunIterationResponse(
        UUID id,
        int iterationNumber,
        String requestName,
        HttpMethod requestMethod,
        String requestUrl,
        int responseStatus,
        long responseTimeMs,
        long responseSizeBytes,
        boolean passed,
        String assertionResults,
        String errorMessage
) {}
