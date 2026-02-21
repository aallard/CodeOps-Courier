package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEnvironmentRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description
) {}
