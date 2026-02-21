package com.codeops.courier.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ForkResponse(
        UUID id,
        UUID sourceCollectionId,
        String sourceCollectionName,
        UUID forkedCollectionId,
        UUID forkedByUserId,
        String label,
        Instant forkedAt,
        Instant createdAt
) {}
