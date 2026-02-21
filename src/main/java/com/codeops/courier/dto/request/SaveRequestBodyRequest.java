package com.codeops.courier.dto.request;

import com.codeops.courier.entity.enums.BodyType;
import jakarta.validation.constraints.NotNull;

public record SaveRequestBodyRequest(
        @NotNull BodyType bodyType,
        String rawContent,
        String formData,
        String graphqlQuery,
        String graphqlVariables,
        String binaryFileName
) {}
