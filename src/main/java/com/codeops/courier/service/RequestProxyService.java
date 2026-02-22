package com.codeops.courier.service;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.SaveRequestAuthRequest;
import com.codeops.courier.dto.request.SaveRequestBodyRequest;
import com.codeops.courier.dto.request.SaveRequestHeadersRequest;
import com.codeops.courier.dto.request.SendRequestProxyRequest;
import com.codeops.courier.dto.response.ProxyResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.RequestAuth;
import com.codeops.courier.entity.RequestHistory;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.repository.RequestAuthRepository;
import com.codeops.courier.repository.RequestHistoryRepository;
import com.codeops.courier.repository.RequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core HTTP execution engine for CodeOps-Courier.
 * Takes a request specification, resolves variables, applies authentication,
 * sends the HTTP request via java.net.http.HttpClient, captures the full
 * response with timing data, follows redirects manually for tracking,
 * and optionally saves execution history.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class RequestProxyService {

    private final HttpClient courierHttpClient;
    private final VariableService variableService;
    private final AuthResolverService authResolverService;
    private final RequestHistoryRepository requestHistoryRepository;
    private final RequestRepository requestRepository;
    private final RequestAuthRepository requestAuthRepository;
    private final ObjectMapper objectMapper;

    /**
     * Executes an HTTP request through the proxy.
     * Resolves variables in URL, headers, and body; applies auth; sends the request;
     * captures response with timing; and optionally saves to history.
     *
     * @param proxyRequest the request specification
     * @param teamId       team ID for variable resolution and history
     * @param userId       user ID for history recording
     * @return full proxy response with timing, headers, body, and redirect chain
     */
    public ProxyResponse executeRequest(SendRequestProxyRequest proxyRequest, UUID teamId, UUID userId) {
        UUID collectionId = proxyRequest.collectionId();
        UUID environmentId = proxyRequest.environmentId();

        // 1. Resolve variables in URL
        String resolvedUrl = variableService.resolveUrl(
                proxyRequest.url(), teamId, collectionId, environmentId, null);

        // 2. Build headers
        Map<String, String> headerMap = new LinkedHashMap<>();
        headerMap.put("User-Agent", AppConstants.USER_AGENT);
        if (proxyRequest.headers() != null) {
            for (SaveRequestHeadersRequest.RequestHeaderEntry entry : proxyRequest.headers()) {
                if (entry.isEnabled()) {
                    String key = variableService.resolveVariables(
                            entry.headerKey(), teamId, collectionId, environmentId, null);
                    String value = variableService.resolveVariables(
                            entry.headerValue(), teamId, collectionId, environmentId, null);
                    headerMap.put(key, value);
                }
            }
        }

        // 3. Resolve auth
        AuthResolverService.ResolvedAuth resolvedAuth = authResolverService.resolveAuth(
                proxyRequest.auth(), teamId, collectionId, environmentId, null);
        headerMap.putAll(resolvedAuth.headers());

        // 4. Add auth query params to URL
        if (!resolvedAuth.queryParams().isEmpty()) {
            resolvedUrl = appendQueryParams(resolvedUrl, resolvedAuth.queryParams());
        }

        // 5. Build request body
        HttpRequest.BodyPublisher bodyPublisher = buildBodyPublisher(
                proxyRequest.body(), teamId, collectionId, environmentId, null, headerMap);

        // 6. Build HttpRequest
        int timeoutMs = proxyRequest.timeoutMs() != null ? proxyRequest.timeoutMs() : AppConstants.DEFAULT_TIMEOUT_MS;
        timeoutMs = Math.max(AppConstants.MIN_TIMEOUT_MS, Math.min(timeoutMs, AppConstants.MAX_TIMEOUT_MS));

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(resolvedUrl))
                .timeout(Duration.ofMillis(timeoutMs));

        String methodName = proxyRequest.method().name();
        requestBuilder.method(methodName, bodyPublisher);

        for (Map.Entry<String, String> header : headerMap.entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }

        HttpRequest httpRequest = requestBuilder.build();

        // 7. Execute and capture timing
        long startTime = System.currentTimeMillis();
        ProxyResponse response;

        try {
            if (proxyRequest.followRedirects()) {
                response = executeWithRedirects(httpRequest, startTime);
            } else {
                HttpResponse<String> httpResponse = courierHttpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                long duration = System.currentTimeMillis() - startTime;
                response = buildProxyResponse(httpResponse, duration, List.of(), null);
            }
        } catch (HttpTimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("Request timed out after {}ms: {}", duration, resolvedUrl);
            response = buildErrorResponse(0, "Request timed out after " + duration + "ms", duration);
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("Request failed: {} — {}", resolvedUrl, e.getMessage());
            response = buildErrorResponse(0, "Connection error: " + e.getMessage(), duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.currentTimeMillis() - startTime;
            log.warn("Request interrupted: {}", resolvedUrl);
            response = buildErrorResponse(0, "Request interrupted", duration);
        }

        // 8. Save to history if requested
        UUID historyId = null;
        if (proxyRequest.saveToHistory()) {
            historyId = saveToHistory(proxyRequest, response, teamId, userId,
                    collectionId, null, environmentId, resolvedUrl);
        }

        return new ProxyResponse(
                response.statusCode(),
                response.statusText(),
                response.responseHeaders(),
                response.responseBody(),
                response.responseTimeMs(),
                response.responseSizeBytes(),
                response.contentType(),
                response.redirectChain(),
                historyId
        );
    }

    /**
     * Executes a stored request by loading it from the database.
     * Resolves auth inheritance by walking the folder/collection chain,
     * then delegates to {@link #executeRequest}.
     *
     * @param requestId     the stored request ID
     * @param teamId        team ID for access validation and variable resolution
     * @param userId        user ID for history recording
     * @param environmentId environment ID for variable resolution (nullable)
     * @return full proxy response with timing and response data
     * @throws NotFoundException if the request is not found
     */
    public ProxyResponse executeStoredRequest(UUID requestId, UUID teamId, UUID userId, UUID environmentId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found: " + requestId));

        Folder folder = request.getFolder();
        Collection collection = folder.getCollection();
        if (!collection.getTeamId().equals(teamId)) {
            throw new NotFoundException("Request not found: " + requestId);
        }

        // Build headers from stored request
        List<SaveRequestHeadersRequest.RequestHeaderEntry> headers = request.getHeaders().stream()
                .map(h -> new SaveRequestHeadersRequest.RequestHeaderEntry(
                        h.getHeaderKey(), h.getHeaderValue(), h.getDescription(), h.isEnabled()))
                .toList();

        // Resolve auth inheritance
        SaveRequestAuthRequest authRequest = resolveInheritedAuth(request);

        // Build body from stored request
        SaveRequestBodyRequest bodyRequest = null;
        if (request.getBody() != null) {
            bodyRequest = new SaveRequestBodyRequest(
                    request.getBody().getBodyType(),
                    request.getBody().getRawContent(),
                    request.getBody().getFormData(),
                    request.getBody().getGraphqlQuery(),
                    request.getBody().getGraphqlVariables(),
                    request.getBody().getBinaryFileName()
            );
        }

        SendRequestProxyRequest proxyRequest = new SendRequestProxyRequest(
                request.getMethod(),
                request.getUrl(),
                headers,
                bodyRequest,
                authRequest,
                environmentId,
                collection.getId(),
                true,
                null,
                true
        );

        ProxyResponse response = executeRequest(proxyRequest, teamId, userId);

        // Re-save with request and collection IDs attached
        if (response.historyId() != null) {
            requestHistoryRepository.findById(response.historyId()).ifPresent(history -> {
                history.setRequestId(requestId);
                history.setCollectionId(collection.getId());
                requestHistoryRepository.save(history);
            });
        }

        return response;
    }

    /**
     * Resolves auth by walking up the folder/collection hierarchy.
     * If the request's auth is INHERIT_FROM_PARENT, walks up: request → folder → parent folder → collection.
     *
     * @param request the request entity
     * @return the resolved auth request, or NO_AUTH if none found
     */
    SaveRequestAuthRequest resolveInheritedAuth(Request request) {
        // Check request's own auth
        RequestAuth reqAuth = requestAuthRepository.findByRequestId(request.getId()).orElse(null);
        if (reqAuth != null && reqAuth.getAuthType() != AuthType.INHERIT_FROM_PARENT) {
            return toAuthRequest(reqAuth);
        }

        // Walk folder chain
        Folder folder = request.getFolder();
        while (folder != null) {
            if (folder.getAuthType() != null && folder.getAuthType() != AuthType.INHERIT_FROM_PARENT) {
                return new SaveRequestAuthRequest(
                        folder.getAuthType(), null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null, null, null, null, null);
            }
            folder = folder.getParentFolder();
        }

        // Check collection
        Collection collection = request.getFolder().getCollection();
        if (collection.getAuthType() != null && collection.getAuthType() != AuthType.INHERIT_FROM_PARENT) {
            return new SaveRequestAuthRequest(
                    collection.getAuthType(), null, null, null, null,
                    null, null, null, null, null, null,
                    null, null, null, null, null, null, null);
        }

        // No auth found
        return new SaveRequestAuthRequest(
                AuthType.NO_AUTH, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private SaveRequestAuthRequest toAuthRequest(RequestAuth auth) {
        return new SaveRequestAuthRequest(
                auth.getAuthType(),
                auth.getApiKeyHeader(),
                auth.getApiKeyValue(),
                auth.getApiKeyAddTo(),
                auth.getBearerToken(),
                auth.getBasicUsername(),
                auth.getBasicPassword(),
                auth.getOauth2GrantType(),
                auth.getOauth2AuthUrl(),
                auth.getOauth2TokenUrl(),
                auth.getOauth2ClientId(),
                auth.getOauth2ClientSecret(),
                auth.getOauth2Scope(),
                auth.getOauth2CallbackUrl(),
                auth.getOauth2AccessToken(),
                auth.getJwtSecret(),
                auth.getJwtPayload(),
                auth.getJwtAlgorithm()
        );
    }

    private ProxyResponse executeWithRedirects(HttpRequest originalRequest, long startTime)
            throws IOException, InterruptedException {
        List<String> redirectChain = new ArrayList<>();
        HttpRequest currentRequest = originalRequest;
        HttpResponse<String> httpResponse = null;

        for (int i = 0; i < AppConstants.MAX_REDIRECT_COUNT; i++) {
            httpResponse = courierHttpClient.send(currentRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() >= 300 && httpResponse.statusCode() < 400) {
                String location = httpResponse.headers().firstValue("Location").orElse(null);
                if (location == null) {
                    break;
                }

                redirectChain.add(location);
                URI redirectUri = URI.create(location);
                if (!redirectUri.isAbsolute()) {
                    redirectUri = currentRequest.uri().resolve(redirectUri);
                }

                currentRequest = HttpRequest.newBuilder(redirectUri)
                        .method(currentRequest.method(),
                                currentRequest.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()))
                        .timeout(currentRequest.timeout().orElse(Duration.ofMillis(AppConstants.DEFAULT_TIMEOUT_MS)))
                        .build();
            } else {
                break;
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // If we exhausted all redirect attempts and last response was still a redirect
        if (redirectChain.size() >= AppConstants.MAX_REDIRECT_COUNT
                && httpResponse != null
                && httpResponse.statusCode() >= 300 && httpResponse.statusCode() < 400) {
            log.warn("Max redirects ({}) exceeded", AppConstants.MAX_REDIRECT_COUNT);
            return buildProxyResponse(httpResponse, duration, redirectChain, "Max redirects exceeded");
        }

        return buildProxyResponse(httpResponse, duration, redirectChain, null);
    }

    private ProxyResponse buildProxyResponse(HttpResponse<String> httpResponse, long duration,
                                             List<String> redirectChain, String errorMessage) {
        String body = httpResponse.body();
        long bodySize = body != null ? body.getBytes(StandardCharsets.UTF_8).length : 0;

        if (body != null && bodySize > AppConstants.MAX_RESPONSE_BODY_SIZE) {
            body = body.substring(0, AppConstants.MAX_RESPONSE_BODY_SIZE) + "\n... [truncated]";
        }

        Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
        httpResponse.headers().map().forEach((key, values) -> {
            if (key != null) {
                responseHeaders.put(key, new ArrayList<>(values));
            }
        });

        String contentType = httpResponse.headers().firstValue("Content-Type").orElse(null);
        String statusText = errorMessage != null ? errorMessage : getStatusText(httpResponse.statusCode());

        return new ProxyResponse(
                httpResponse.statusCode(),
                statusText,
                responseHeaders,
                body,
                duration,
                bodySize,
                contentType,
                redirectChain,
                null
        );
    }

    private ProxyResponse buildErrorResponse(int statusCode, String errorMessage, long duration) {
        return new ProxyResponse(
                statusCode,
                errorMessage,
                Map.of(),
                null,
                duration,
                0,
                null,
                List.of(),
                null
        );
    }

    private HttpRequest.BodyPublisher buildBodyPublisher(SaveRequestBodyRequest body, UUID teamId,
                                                         UUID collectionId, UUID environmentId,
                                                         Map<String, String> localVars,
                                                         Map<String, String> headerMap) {
        if (body == null || body.bodyType() == BodyType.NONE) {
            return HttpRequest.BodyPublishers.noBody();
        }

        switch (body.bodyType()) {
            case RAW_JSON -> {
                headerMap.putIfAbsent("Content-Type", "application/json");
                String content = variableService.resolveVariables(
                        body.rawContent(), teamId, collectionId, environmentId, localVars);
                return HttpRequest.BodyPublishers.ofString(content != null ? content : "");
            }
            case RAW_XML -> {
                headerMap.putIfAbsent("Content-Type", "application/xml");
                String content = variableService.resolveVariables(
                        body.rawContent(), teamId, collectionId, environmentId, localVars);
                return HttpRequest.BodyPublishers.ofString(content != null ? content : "");
            }
            case RAW_HTML -> {
                headerMap.putIfAbsent("Content-Type", "text/html");
                String content = variableService.resolveVariables(
                        body.rawContent(), teamId, collectionId, environmentId, localVars);
                return HttpRequest.BodyPublishers.ofString(content != null ? content : "");
            }
            case RAW_TEXT -> {
                headerMap.putIfAbsent("Content-Type", "text/plain");
                String content = variableService.resolveVariables(
                        body.rawContent(), teamId, collectionId, environmentId, localVars);
                return HttpRequest.BodyPublishers.ofString(content != null ? content : "");
            }
            case RAW_YAML -> {
                headerMap.putIfAbsent("Content-Type", "application/x-yaml");
                String content = variableService.resolveVariables(
                        body.rawContent(), teamId, collectionId, environmentId, localVars);
                return HttpRequest.BodyPublishers.ofString(content != null ? content : "");
            }
            case X_WWW_FORM_URLENCODED -> {
                headerMap.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
                String resolved = variableService.resolveVariables(
                        body.formData(), teamId, collectionId, environmentId, localVars);
                return HttpRequest.BodyPublishers.ofString(resolved != null ? resolved : "");
            }
            case FORM_DATA -> {
                String resolved = variableService.resolveVariables(
                        body.formData(), teamId, collectionId, environmentId, localVars);
                String boundary = "----CourierFormBoundary" + UUID.randomUUID().toString().replace("-", "");
                headerMap.put("Content-Type", "multipart/form-data; boundary=" + boundary);
                return HttpRequest.BodyPublishers.ofString(
                        buildMultipartBody(resolved, boundary));
            }
            case GRAPHQL -> {
                headerMap.putIfAbsent("Content-Type", "application/json");
                String query = variableService.resolveVariables(
                        body.graphqlQuery(), teamId, collectionId, environmentId, localVars);
                String variables = variableService.resolveVariables(
                        body.graphqlVariables(), teamId, collectionId, environmentId, localVars);
                String graphqlJson = buildGraphqlBody(query, variables);
                return HttpRequest.BodyPublishers.ofString(graphqlJson);
            }
            case BINARY -> {
                headerMap.putIfAbsent("Content-Type", "application/octet-stream");
                return HttpRequest.BodyPublishers.noBody();
            }
            default -> {
                return HttpRequest.BodyPublishers.noBody();
            }
        }
    }

    private String buildMultipartBody(String formData, String boundary) {
        if (formData == null || formData.isEmpty()) {
            return "--" + boundary + "--\r\n";
        }
        StringBuilder sb = new StringBuilder();
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            String key = keyValue[0];
            String value = keyValue.length > 1 ? keyValue[1] : "";
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"").append(key).append("\"\r\n\r\n");
            sb.append(value).append("\r\n");
        }
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString();
    }

    private String buildGraphqlBody(String query, String variables) {
        Map<String, Object> graphqlMap = new LinkedHashMap<>();
        graphqlMap.put("query", query != null ? query : "");
        if (variables != null && !variables.isEmpty()) {
            try {
                Object parsedVars = objectMapper.readValue(variables, Object.class);
                graphqlMap.put("variables", parsedVars);
            } catch (JsonProcessingException e) {
                graphqlMap.put("variables", variables);
            }
        }
        try {
            return objectMapper.writeValueAsString(graphqlMap);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize GraphQL body: {}", e.getMessage());
            return "{\"query\":\"" + (query != null ? query : "") + "\"}";
        }
    }

    private UUID saveToHistory(SendRequestProxyRequest proxyRequest, ProxyResponse response,
                               UUID teamId, UUID userId, UUID collectionId, UUID requestId,
                               UUID environmentId, String resolvedUrl) {
        String requestHeaders = serializeHeaders(proxyRequest.headers());
        String requestBody = proxyRequest.body() != null ? proxyRequest.body().rawContent() : null;

        String responseBody = response.responseBody();
        if (responseBody != null && responseBody.length() > AppConstants.HISTORY_BODY_TRUNCATE_SIZE) {
            responseBody = responseBody.substring(0, AppConstants.HISTORY_BODY_TRUNCATE_SIZE) + "\n... [truncated]";
        }

        String responseHeaders = null;
        if (response.responseHeaders() != null && !response.responseHeaders().isEmpty()) {
            try {
                responseHeaders = objectMapper.writeValueAsString(response.responseHeaders());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize response headers for history: {}", e.getMessage());
            }
        }

        RequestHistory history = new RequestHistory();
        history.setTeamId(teamId);
        history.setUserId(userId);
        history.setRequestMethod(proxyRequest.method());
        history.setRequestUrl(resolvedUrl);
        history.setRequestHeaders(requestHeaders);
        history.setRequestBody(requestBody);
        history.setResponseStatus(response.statusCode() > 0 ? response.statusCode() : null);
        history.setResponseHeaders(responseHeaders);
        history.setResponseBody(responseBody);
        history.setResponseSizeBytes(response.responseSizeBytes());
        history.setResponseTimeMs(response.responseTimeMs());
        history.setContentType(response.contentType());
        history.setCollectionId(collectionId);
        history.setRequestId(requestId);
        history.setEnvironmentId(environmentId);

        RequestHistory saved = requestHistoryRepository.save(history);
        log.info("Saved request history: {} {} → {} ({}ms)",
                proxyRequest.method(), resolvedUrl, response.statusCode(), response.responseTimeMs());
        return saved.getId();
    }

    private String serializeHeaders(List<SaveRequestHeadersRequest.RequestHeaderEntry> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize request headers: {}", e.getMessage());
            return null;
        }
    }

    private String appendQueryParams(String url, Map<String, String> queryParams) {
        String separator = url.contains("?") ? "&" : "?";
        String paramString = queryParams.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return url + separator + paramString;
    }

    /**
     * Converts an HTTP status code to its standard status text.
     *
     * @param statusCode the HTTP status code
     * @return the standard status text, or "Unknown" if not recognized
     */
    String getStatusText(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 413 -> "Payload Too Large";
            case 415 -> "Unsupported Media Type";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "Unknown";
        };
    }
}
