package com.codeops.courier.dto.request;

import com.codeops.courier.entity.enums.ScriptType;
import jakarta.validation.constraints.NotNull;

public record SaveRequestScriptRequest(
        @NotNull ScriptType scriptType,
        String content
) {}
