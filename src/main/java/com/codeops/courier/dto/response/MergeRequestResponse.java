package com.codeops.courier.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MergeRequestResponse(
        UUID id,
        UUID sourceForkId,
        UUID targetCollectionId,
        String targetCollectionName,
        String title,
        String description,
        String status,
        UUID requestedByUserId,
        UUID reviewedByUserId,
        Instant mergedAt,
        String conflictDetails,
        Instant createdAt,
        Instant updatedAt
) {}
