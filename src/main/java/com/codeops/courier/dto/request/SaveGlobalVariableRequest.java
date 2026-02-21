package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveGlobalVariableRequest(
        @NotBlank @Size(max = 500) String variableKey,
        @Size(max = 5000) String variableValue,
        boolean isSecret,
        boolean isEnabled
) {}
