package com.codeops.courier.dto.request;

import java.util.UUID;

public record DuplicateRequestRequest(
        UUID targetFolderId
) {}
