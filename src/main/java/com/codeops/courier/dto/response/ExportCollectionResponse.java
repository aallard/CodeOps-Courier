package com.codeops.courier.dto.response;

public record ExportCollectionResponse(
        String format,
        String content,
        String filename
) {}
