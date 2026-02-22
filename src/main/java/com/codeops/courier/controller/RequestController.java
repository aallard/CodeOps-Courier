package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.*;
import com.codeops.courier.dto.response.*;
import com.codeops.courier.security.SecurityUtils;
import com.codeops.courier.service.RequestProxyService;
import com.codeops.courier.service.RequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing API requests and their component sub-resources
 * (headers, params, body, auth, scripts). Also provides request execution
 * through the proxy service. All endpoints require authentication and the ADMIN role.
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/requests")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Requests", description = "Request CRUD, components, duplication, move, reorder, and execution")
public class RequestController {

    private final RequestService requestService;
    private final RequestProxyService requestProxyService;

    // ─── Request CRUD ───

    /**
     * Creates a new request within a folder.
     *
     * @param teamId  the team ID from the request header
     * @param request the request creation DTO
     * @return the created request response with all components
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse createRequest(@RequestHeader("X-Team-ID") UUID teamId,
                                         @Valid @RequestBody CreateRequestRequest request) {
        log.info("Creating request '{}' in folder {} for team {}", request.name(), request.folderId(), teamId);
        return requestService.createRequest(teamId, request);
    }

    /**
     * Returns the full detail of a request including all components.
     *
     * @param requestId the request ID
     * @param teamId    the team ID from the request header
     * @return the request response with headers, params, body, auth, and scripts
     */
    @GetMapping("/{requestId}")
    public RequestResponse getRequest(@PathVariable UUID requestId,
                                      @RequestHeader("X-Team-ID") UUID teamId) {
        return requestService.getRequest(requestId, teamId);
    }

    /**
     * Updates a request's metadata (name, description, method, URL, sort order).
     *
     * @param requestId the request ID
     * @param teamId    the team ID from the request header
     * @param request   the update DTO
     * @return the updated request response
     */
    @PutMapping("/{requestId}")
    public RequestResponse updateRequest(@PathVariable UUID requestId,
                                         @RequestHeader("X-Team-ID") UUID teamId,
                                         @Valid @RequestBody UpdateRequestRequest request) {
        log.info("Updating request {} for team {}", requestId, teamId);
        return requestService.updateRequest(requestId, teamId, request);
    }

    /**
     * Deletes a request and all its components.
     *
     * @param requestId the request ID
     * @param teamId    the team ID from the request header
     */
    @DeleteMapping("/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRequest(@PathVariable UUID requestId,
                              @RequestHeader("X-Team-ID") UUID teamId) {
        log.info("Deleting request {} for team {}", requestId, teamId);
        requestService.deleteRequest(requestId, teamId);
    }

    /**
     * Duplicates a request, optionally into a different folder.
     *
     * @param requestId the source request ID
     * @param teamId    the team ID from the request header
     * @param request   optional duplicate request specifying target folder (may be null)
     * @return the duplicated request response
     */
    @PostMapping("/{requestId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse duplicateRequest(@PathVariable UUID requestId,
                                            @RequestHeader("X-Team-ID") UUID teamId,
                                            @RequestBody(required = false) DuplicateRequestRequest request) {
        log.info("Duplicating request {} for team {}", requestId, teamId);
        return requestService.duplicateRequest(requestId, teamId, request);
    }

    /**
     * Moves a request to a different folder.
     *
     * @param requestId      the request ID
     * @param teamId         the team ID from the request header
     * @param targetFolderId the target folder ID
     * @return the updated request response
     */
    @PutMapping("/{requestId}/move")
    public RequestResponse moveRequest(@PathVariable UUID requestId,
                                       @RequestHeader("X-Team-ID") UUID teamId,
                                       @RequestParam UUID targetFolderId) {
        log.info("Moving request {} to folder {} for team {}", requestId, targetFolderId, teamId);
        return requestService.moveRequest(requestId, teamId, targetFolderId);
    }

    /**
     * Reorders requests within a folder by providing the desired order of request IDs.
     *
     * @param teamId  the team ID from the request header
     * @param request the reorder request with ordered request IDs
     * @return the reordered request summaries
     */
    @PutMapping("/reorder")
    public List<RequestSummaryResponse> reorderRequests(@RequestHeader("X-Team-ID") UUID teamId,
                                                        @Valid @RequestBody ReorderRequestRequest request) {
        log.info("Reordering {} requests for team {}", request.requestIds().size(), teamId);
        return requestService.reorderRequests(teamId, request);
    }

    // ─── Request Components ───

    /**
     * Replaces all headers on a request.
     *
     * @param requestId the request ID
     * @param teamId    the team ID from the request header
     * @param request   the headers save request
     * @return the saved header responses
     */
    @PutMapping("/{requestId}/headers")
    public List<RequestHeaderResponse> saveHeaders(@PathVariable UUID requestId,
                                                   @RequestHeader("X-Team-ID") UUID teamId,
                                                   @Valid @RequestBody SaveRequestHeadersRequest request) {
        return requestService.saveHeaders(requestId, teamId, request);
    }

    /**
     * Replaces all query parameters on a request.
     *
     * @param requestId the request ID
     * @param teamId    the team ID from the request header
     * @param request   the params save request
     * @return the saved param responses
     */
    @PutMapping("/{requestId}/params")
    public List<RequestParamResponse> saveParams(@PathVariable UUID requestId,
                                                 @RequestHeader("X-Team-ID") UUID teamId,
                                                 @Valid @RequestBody SaveRequestParamsRequest request) {
        return requestService.saveParams(requestId, teamId, request);
    }

    /**
     * Saves or replaces the request body.
     *
     * @param requestId the request ID
     * @param teamId    the team ID from the request header
     * @param request   the body save request
     * @return the saved body response
     */
    @PutMapping("/{requestId}/body")
    public RequestBodyResponse saveBody(@PathVariable UUID requestId,
                                        @RequestHeader("X-Team-ID") UUID teamId,
                                        @Valid @RequestBody SaveRequestBodyRequest request) {
        return requestService.saveBody(requestId, teamId, request);
    }

    /**
     * Saves or replaces the request authentication configuration.
     *
     * @param requestId the request ID
     * @param teamId    the team ID from the request header
     * @param request   the auth save request
     * @return the saved auth response
     */
    @PutMapping("/{requestId}/auth")
    public RequestAuthResponse saveAuth(@PathVariable UUID requestId,
                                        @RequestHeader("X-Team-ID") UUID teamId,
                                        @Valid @RequestBody SaveRequestAuthRequest request) {
        return requestService.saveAuth(requestId, teamId, request);
    }

    /**
     * Saves or replaces a request script (pre-request or post-response).
     *
     * @param requestId the request ID
     * @param teamId    the team ID from the request header
     * @param request   the script save request
     * @return the saved script response
     */
    @PutMapping("/{requestId}/scripts")
    public RequestScriptResponse saveScript(@PathVariable UUID requestId,
                                            @RequestHeader("X-Team-ID") UUID teamId,
                                            @Valid @RequestBody SaveRequestScriptRequest request) {
        return requestService.saveScript(requestId, teamId, request);
    }

    // ─── Request Execution ───

    /**
     * Executes a stored request through the proxy service, optionally resolving
     * variables from the specified environment.
     *
     * @param requestId     the request ID to execute
     * @param teamId        the team ID from the request header
     * @param environmentId optional environment ID for variable resolution
     * @return the proxy response with status, headers, body, and timing
     */
    @PostMapping("/{requestId}/send")
    public ProxyResponse sendRequest(@PathVariable UUID requestId,
                                     @RequestHeader("X-Team-ID") UUID teamId,
                                     @RequestParam(required = false) UUID environmentId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Sending request {} for team {} with environment {}", requestId, teamId, environmentId);
        return requestProxyService.executeStoredRequest(requestId, teamId, userId, environmentId);
    }
}
