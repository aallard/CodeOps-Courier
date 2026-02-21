package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record ExecuteGraphQLRequest(
        @NotBlank String url,
        @NotBlank String query,
        String variables,
        String operationName,
        List<SaveRequestHeadersRequest.RequestHeaderEntry> headers,
        SaveRequestAuthRequest auth,
        UUID environmentId
) {}
