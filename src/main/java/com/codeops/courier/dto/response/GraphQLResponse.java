package com.codeops.courier.dto.response;

public record GraphQLResponse(
        ProxyResponse httpResponse,
        String schema
) {}
