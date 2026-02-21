package com.codeops.courier.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CollectionSummaryResponse(
        UUID id,
        String name,
        String description,
        boolean isShared,
        int folderCount,
        int requestCount,
        Instant updatedAt
) {}
