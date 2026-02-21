package com.codeops.courier.dto.response;

import java.time.Instant;
import java.util.UUID;

public record EnvironmentResponse(
        UUID id,
        UUID teamId,
        String name,
        String description,
        boolean isActive,
        UUID createdBy,
        int variableCount,
        Instant createdAt,
        Instant updatedAt
) {}
