package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateFolderRequest(
        @NotNull UUID collectionId,
        UUID parentFolderId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        Integer sortOrder
) {}
