package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveRequestHeadersRequest(
        @NotNull List<RequestHeaderEntry> headers
) {
    public record RequestHeaderEntry(
            @NotBlank @Size(max = 500) String headerKey,
            @Size(max = 5000) String headerValue,
            @Size(max = 500) String description,
            boolean isEnabled
    ) {}
}
