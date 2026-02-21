package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.AuthType;

import java.time.Instant;
import java.util.UUID;

public record FolderResponse(
        UUID id,
        UUID collectionId,
        UUID parentFolderId,
        String name,
        String description,
        int sortOrder,
        String preRequestScript,
        String postResponseScript,
        AuthType authType,
        String authConfig,
        int subFolderCount,
        int requestCount,
        Instant createdAt,
        Instant updatedAt
) {}
