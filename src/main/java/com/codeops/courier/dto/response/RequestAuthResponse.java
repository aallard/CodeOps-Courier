package com.codeops.courier.dto.response;

import com.codeops.courier.entity.enums.AuthType;

import java.util.UUID;

public record RequestAuthResponse(
        UUID id,
        AuthType authType,
        String apiKeyHeader,
        String apiKeyValue,
        String apiKeyAddTo,
        String bearerToken,
        String basicUsername,
        String basicPassword,
        String oauth2GrantType,
        String oauth2AuthUrl,
        String oauth2TokenUrl,
        String oauth2ClientId,
        String oauth2ClientSecret,
        String oauth2Scope,
        String oauth2CallbackUrl,
        String oauth2AccessToken,
        String jwtSecret,
        String jwtPayload,
        String jwtAlgorithm
) {}
