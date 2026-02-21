package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReorderFolderRequest(
        @NotNull List<UUID> folderIds
) {}
