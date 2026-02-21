package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.AuthType;

import java.time.Instant;
import java.util.UUID;

public record CollectionResponse(
        UUID id,
        UUID teamId,
        String name,
        String description,
        String preRequestScript,
        String postResponseScript,
        AuthType authType,
        String authConfig,
        boolean isShared,
        UUID createdBy,
        int folderCount,
        int requestCount,
        Instant createdAt,
        Instant updatedAt
) {}
