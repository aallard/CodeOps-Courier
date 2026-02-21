package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveEnvironmentVariablesRequest(
        @NotNull List<VariableEntry> variables
) {
    public record VariableEntry(
            @NotBlank @Size(max = 500) String variableKey,
            @Size(max = 5000) String variableValue,
            boolean isSecret,
            boolean isEnabled
    ) {}
}
