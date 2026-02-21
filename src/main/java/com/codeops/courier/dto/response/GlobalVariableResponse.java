package com.codeops.courier.dto.response;

import java.time.Instant;
import java.util.UUID;

public record GlobalVariableResponse(
        UUID id,
        UUID teamId,
        String variableKey,
        String variableValue,
        boolean isSecret,
        boolean isEnabled,
        Instant createdAt,
        Instant updatedAt
) {}
