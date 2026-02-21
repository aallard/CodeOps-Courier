package com.codeops.courier.dto.response;

import java.util.UUID;

public record EnvironmentVariableResponse(
        UUID id,
        String variableKey,
        String variableValue,
        boolean isSecret,
        boolean isEnabled,
        String scope
) {}
