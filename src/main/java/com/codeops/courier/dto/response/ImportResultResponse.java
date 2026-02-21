package com.codeops.courier.dto.response;

import java.util.List;
import java.util.UUID;

public record ImportResultResponse(
        UUID collectionId,
        String collectionName,
        int foldersImported,
        int requestsImported,
        int environmentsImported,
        List<String> warnings
) {}
