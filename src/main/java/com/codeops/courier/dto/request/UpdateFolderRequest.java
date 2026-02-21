package com.codeops.courier.dto.request;

import com.codeops.courier.entity.enums.AuthType;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateFolderRequest(
        @Size(max = 200) String name,
        @Size(max = 2000) String description,
        Integer sortOrder,
        UUID parentFolderId,
        String preRequestScript,
        String postResponseScript,
        AuthType authType,
        String authConfig
) {}
