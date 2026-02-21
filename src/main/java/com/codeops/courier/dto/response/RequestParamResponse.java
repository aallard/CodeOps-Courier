package com.codeops.courier.dto.response;

import java.util.UUID;

public record RequestParamResponse(
        UUID id,
        String paramKey,
        String paramValue,
        String description,
        boolean isEnabled
) {}
