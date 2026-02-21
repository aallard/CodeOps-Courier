package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReorderRequestRequest(
        @NotNull List<UUID> requestIds
) {}
