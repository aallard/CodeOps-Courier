package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.CodeLanguage;

public record CodeSnippetResponse(
        CodeLanguage language,
        String displayName,
        String code,
        String fileExtension,
        String contentType
) {}
