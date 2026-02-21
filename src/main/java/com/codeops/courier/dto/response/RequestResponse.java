package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.HttpMethod;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RequestResponse(
        UUID id,
        UUID folderId,
        String name,
        String description,
        HttpMethod method,
        String url,
        int sortOrder,
        List<RequestHeaderResponse> headers,
        List<RequestParamResponse> params,
        RequestBodyResponse body,
        RequestAuthResponse auth,
        List<RequestScriptResponse> scripts,
        Instant createdAt,
        Instant updatedAt
) {}
