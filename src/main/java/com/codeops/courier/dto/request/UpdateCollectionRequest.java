package com.codeops.courier.dto.request;

import com.codeops.courier.entity.enums.AuthType;
import jakarta.validation.constraints.Size;

public record UpdateCollectionRequest(
        @Size(max = 200) String name,
        @Size(max = 2000) String description,
        String preRequestScript,
        String postResponseScript,
        AuthType authType,
        String authConfig
) {}
