package com.codeops.courier.service;

import com.codeops.courier.dto.request.SaveRequestAuthRequest;
import com.codeops.courier.entity.enums.AuthType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves authentication configuration into concrete HTTP headers and query parameters.
 * Handles all {@link AuthType} values and integrates with {@link VariableService}
 * for {{variable}} placeholder resolution in auth values.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthResolverService {

    private final VariableService variableService;

    /**
     * Resolves auth configuration into headers and query parameters to add to the outgoing request.
     * All string values are passed through variable resolution before use.
     *
     * @param auth          the auth configuration (nullable — returns empty if null)
     * @param teamId        team ID for variable resolution
     * @param collectionId  collection ID for variable resolution (nullable)
     * @param environmentId environment ID for variable resolution (nullable)
     * @param localVars     runtime variables for resolution (nullable)
     * @return resolved auth artifacts with headers and query params
     */
    public ResolvedAuth resolveAuth(SaveRequestAuthRequest auth, UUID teamId, UUID collectionId,
                                    UUID environmentId, Map<String, String> localVars) {
        if (auth == null || auth.authType() == AuthType.NO_AUTH || auth.authType() == AuthType.INHERIT_FROM_PARENT) {
            return ResolvedAuth.empty();
        }

        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> queryParams = new LinkedHashMap<>();

        switch (auth.authType()) {
            case BEARER_TOKEN -> {
                String token = resolve(auth.bearerToken(), teamId, collectionId, environmentId, localVars);
                headers.put("Authorization", "Bearer " + token);
            }
            case BASIC_AUTH -> {
                String username = resolve(auth.basicUsername(), teamId, collectionId, environmentId, localVars);
                String password = resolve(auth.basicPassword(), teamId, collectionId, environmentId, localVars);
                String encoded = Base64.getEncoder().encodeToString(
                        (username + ":" + password).getBytes(StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + encoded);
            }
            case API_KEY -> {
                String keyHeader = resolve(auth.apiKeyHeader(), teamId, collectionId, environmentId, localVars);
                String keyValue = resolve(auth.apiKeyValue(), teamId, collectionId, environmentId, localVars);
                String addTo = auth.apiKeyAddTo();
                if ("query".equalsIgnoreCase(addTo)) {
                    queryParams.put(keyHeader, keyValue);
                } else {
                    // Default to header
                    headers.put(keyHeader, keyValue);
                }
            }
            case OAUTH2_AUTHORIZATION_CODE, OAUTH2_CLIENT_CREDENTIALS, OAUTH2_IMPLICIT, OAUTH2_PASSWORD -> {
                String accessToken = resolve(auth.oauth2AccessToken(), teamId, collectionId, environmentId, localVars);
                if (accessToken != null && !accessToken.isEmpty()) {
                    headers.put("Authorization", "Bearer " + accessToken);
                }
            }
            case JWT_BEARER -> {
                String jwtToken = resolve(auth.jwtPayload(), teamId, collectionId, environmentId, localVars);
                if (jwtToken != null && !jwtToken.isEmpty()) {
                    headers.put("Authorization", "Bearer " + jwtToken);
                }
            }
            default -> log.warn("Unhandled auth type: {}", auth.authType());
        }

        return new ResolvedAuth(headers, queryParams);
    }

    private String resolve(String value, UUID teamId, UUID collectionId, UUID environmentId, Map<String, String> localVars) {
        if (value == null) {
            return "";
        }
        return variableService.resolveVariables(value, teamId, collectionId, environmentId, localVars);
    }

    /**
     * Resolved auth artifacts containing headers and query parameters
     * to add to an outgoing HTTP request.
     *
     * @param headers    HTTP headers to add (e.g., Authorization)
     * @param queryParams query parameters to add (e.g., API key in query)
     */
    public record ResolvedAuth(
            Map<String, String> headers,
            Map<String, String> queryParams
    ) {
        /**
         * Returns an empty resolved auth with no headers or query params.
         *
         * @return empty resolved auth
         */
        public static ResolvedAuth empty() {
            return new ResolvedAuth(Map.of(), Map.of());
        }
    }
}
