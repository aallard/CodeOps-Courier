package com.codeops.courier.dto.request;

import com.codeops.courier.entity.enums.CodeLanguage;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GenerateCodeRequest(
        @NotNull UUID requestId,
        @NotNull CodeLanguage language,
        UUID environmentId
) {}
