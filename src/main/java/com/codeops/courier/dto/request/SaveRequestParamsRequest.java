package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveRequestParamsRequest(
        @NotNull List<RequestParamEntry> params
) {
    public record RequestParamEntry(
            @NotBlank @Size(max = 500) String paramKey,
            @Size(max = 5000) String paramValue,
            @Size(max = 500) String description,
            boolean isEnabled
    ) {}
}
