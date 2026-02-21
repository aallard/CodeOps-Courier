package com.codeops.courier.dto.request;

import com.codeops.courier.entity.enums.HttpMethod;
import jakarta.validation.constraints.Size;

public record UpdateRequestRequest(
        @Size(max = 200) String name,
        @Size(max = 2000) String description,
        HttpMethod method,
        @Size(max = 2000) String url,
        Integer sortOrder
) {}
