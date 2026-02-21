package com.codeops.courier.dto.response;

import java.util.UUID;

public record RequestHeaderResponse(
        UUID id,
        String headerKey,
        String headerValue,
        String description,
        boolean isEnabled
) {}
