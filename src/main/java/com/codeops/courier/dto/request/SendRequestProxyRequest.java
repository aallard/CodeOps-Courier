package com.codeops.courier.dto.request;

import com.codeops.courier.entity.enums.HttpMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SendRequestProxyRequest(
        @NotNull HttpMethod method,
        @NotBlank @Size(max = 2000) String url,
        List<SaveRequestHeadersRequest.RequestHeaderEntry> headers,
        SaveRequestBodyRequest body,
        SaveRequestAuthRequest auth,
        UUID environmentId,
        UUID collectionId,
        boolean saveToHistory,
        @Min(1000) @Max(300000) Integer timeoutMs,
        boolean followRedirects
) {}
