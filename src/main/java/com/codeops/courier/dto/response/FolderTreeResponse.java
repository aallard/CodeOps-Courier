package com.codeops.courier.dto.response;

import java.util.List;
import java.util.UUID;

public record FolderTreeResponse(
        UUID id,
        String name,
        int sortOrder,
        List<FolderTreeResponse> subFolders,
        List<RequestSummaryResponse> requests
) {}
