package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.BodyType;

import java.util.UUID;

public record RequestBodyResponse(
        UUID id,
        BodyType bodyType,
        String rawContent,
        String formData,
        String graphqlQuery,
        String graphqlVariables,
        String binaryFileName
) {}
