package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.SharePermission;

import java.time.Instant;
import java.util.UUID;

public record CollectionShareResponse(
        UUID id,
        UUID collectionId,
        UUID sharedWithUserId,
        UUID sharedByUserId,
        SharePermission permission,
        Instant createdAt
) {}
