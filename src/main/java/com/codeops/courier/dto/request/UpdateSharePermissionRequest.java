package com.codeops.courier.dto.request;

import com.codeops.courier.entity.enums.SharePermission;
import jakarta.validation.constraints.NotNull;

public record UpdateSharePermissionRequest(
        @NotNull SharePermission permission
) {}
