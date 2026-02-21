package com.codeops.courier.dto.request;

import com.codeops.courier.entity.enums.AuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCollectionRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        AuthType authType,
        String authConfig
) {}
