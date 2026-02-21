package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.CodeLanguage;

import java.util.UUID;

public record CodeSnippetTemplateResponse(
        UUID id,
        CodeLanguage language,
        String displayName,
        String templateContent,
        String fileExtension,
        String contentType
) {}
