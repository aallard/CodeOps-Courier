package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.SendRequestProxyRequest;
import com.codeops.courier.dto.response.ProxyResponse;
import com.codeops.courier.security.SecurityUtils;
import com.codeops.courier.service.RequestProxyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for the HTTP proxy service. Provides ad-hoc request execution
 * and stored request execution with optional environment variable resolution.
 * All endpoints require authentication and the ADMIN role.
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/proxy")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Proxy", description = "HTTP proxy for ad-hoc and stored request execution")
public class ProxyController {

    private final RequestProxyService requestProxyService;

    /**
     * Sends an ad-hoc HTTP request through the proxy service.
     *
     * @param teamId  the team ID from the request header
     * @param request the proxy request with method, URL, headers, body, and auth
     * @return the proxy response with status, headers, body, and timing
     */
    @PostMapping("/send")
    public ProxyResponse sendRequest(@RequestHeader("X-Team-ID") UUID teamId,
                                      @Valid @RequestBody SendRequestProxyRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Proxy send {} {} for team {}", request.method(), request.url(), teamId);
        return requestProxyService.executeRequest(request, teamId, userId);
    }

    /**
     * Executes a stored request through the proxy service, optionally resolving
     * variables from the specified environment.
     *
     * @param requestId     the stored request ID to execute
     * @param teamId        the team ID from the request header
     * @param environmentId optional environment ID for variable resolution
     * @return the proxy response with status, headers, body, and timing
     */
    @PostMapping("/send/{requestId}")
    public ProxyResponse executeStoredRequest(@PathVariable UUID requestId,
                                               @RequestHeader("X-Team-ID") UUID teamId,
                                               @RequestParam(required = false) UUID environmentId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Proxy execute stored request {} for team {} with environment {}", requestId, teamId, environmentId);
        return requestProxyService.executeStoredRequest(requestId, teamId, userId, environmentId);
    }
}
