package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloneEnvironmentRequest(
        @NotBlank @Size(max = 200) String newName
) {}
