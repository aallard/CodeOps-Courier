package com.codeops.courier.dto.request;

import com.codeops.courier.entity.enums.SharePermission;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ShareCollectionRequest(
        @NotNull UUID sharedWithUserId,
        @NotNull SharePermission permission
) {}
