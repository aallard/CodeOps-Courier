package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ImportCollectionRequest(
        @NotBlank String format,
        @NotBlank String content
) {}
