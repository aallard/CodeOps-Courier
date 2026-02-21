package com.codeops.courier.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProxyResponse(
        int statusCode,
        String statusText,
        Map<String, List<String>> responseHeaders,
        String responseBody,
        long responseTimeMs,
        long responseSizeBytes,
        String contentType,
        List<String> redirectChain,
        UUID historyId
) {}
