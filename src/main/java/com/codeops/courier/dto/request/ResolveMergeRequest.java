package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ResolveMergeRequest(
        @NotBlank String action
) {}
