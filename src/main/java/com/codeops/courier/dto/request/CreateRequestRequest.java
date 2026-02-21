package com.codeops.courier.dto.request;

import com.codeops.courier.entity.enums.HttpMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateRequestRequest(
        @NotNull UUID folderId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull HttpMethod method,
        @NotBlank @Size(max = 2000) String url,
        Integer sortOrder
) {}
