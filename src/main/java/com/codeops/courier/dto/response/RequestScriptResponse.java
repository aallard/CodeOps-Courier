package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.ScriptType;

import java.util.UUID;

public record RequestScriptResponse(
        UUID id,
        ScriptType scriptType,
        String content
) {}
