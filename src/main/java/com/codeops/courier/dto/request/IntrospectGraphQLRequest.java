package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record IntrospectGraphQLRequest(
        @NotBlank String url,
        List<SaveRequestHeadersRequest.RequestHeaderEntry> headers,
        SaveRequestAuthRequest auth
) {}
