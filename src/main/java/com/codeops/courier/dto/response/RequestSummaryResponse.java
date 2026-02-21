package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.HttpMethod;

import java.util.UUID;

public record RequestSummaryResponse(
        UUID id,
        String name,
        HttpMethod method,
        String url,
        int sortOrder
) {}
