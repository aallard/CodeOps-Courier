package com.codeops.courier.service;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.SaveRequestAuthRequest;
import com.codeops.courier.dto.request.SaveRequestBodyRequest;
import com.codeops.courier.dto.request.SaveRequestHeadersRequest;
import com.codeops.courier.dto.request.SendRequestProxyRequest;
import com.codeops.courier.dto.request.StartCollectionRunRequest;
import com.codeops.courier.dto.response.PageResponse;
import com.codeops.courier.dto.response.ProxyResponse;
import com.codeops.courier.dto.response.RunResultDetailResponse;
import com.codeops.courier.dto.response.RunResultResponse;
import com.codeops.courier.dto.mapper.RunResultMapper;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.RequestHeader;
import com.codeops.courier.entity.RequestScript;
import com.codeops.courier.entity.RunIteration;
import com.codeops.courier.entity.RunResult;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.entity.enums.RunStatus;
import com.codeops.courier.entity.enums.ScriptType;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.CollectionRepository;
import com.codeops.courier.repository.FolderRepository;
import com.codeops.courier.repository.RequestRepository;
import com.codeops.courier.repository.RequestScriptRepository;
import com.codeops.courier.repository.RunIterationRepository;
import com.codeops.courier.repository.RunResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates sequential execution of all requests in a collection across iterations.
 * Supports CSV/JSON data files for iteration variables, configurable delay between requests,
 * pre-request and post-response script execution, assertion capture, and results recording.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CollectionRunnerService {

    private final RunResultRepository runResultRepository;
    private final RunIterationRepository runIterationRepository;
    private final CollectionRepository collectionRepository;
    private final FolderRepository folderRepository;
    private final RequestRepository requestRepository;
    private final RequestScriptRepository requestScriptRepository;
    private final RequestProxyService requestProxyService;
    private final ScriptEngineService scriptEngineService;
    private final VariableService variableService;
    private final DataFileParser dataFileParser;
    private final RunResultMapper runResultMapper;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<UUID, AtomicBoolean> runningRuns = new ConcurrentHashMap<>();

    /**
     * Starts a collection run. Executes all requests in the collection sequentially,
     * across all iterations. For each request: runs pre-request script, executes HTTP
     * request, runs post-response script.
     *
     * <p>This method runs synchronously. For async execution, the controller should
     * wrap this in {@code @Async} or use {@code CompletableFuture}.</p>
     *
     * @param request run configuration (collection, environment, iterations, delay, data file)
     * @param teamId  team for auth and variable resolution
     * @param userId  user initiating the run
     * @return complete run result with all iterations
     * @throws NotFoundException if the collection does not exist or belongs to another team
     */
    public RunResultDetailResponse startRun(StartCollectionRunRequest request, UUID teamId, UUID userId) {
        // 1. Validate collection
        Collection collection = collectionRepository.findById(request.collectionId())
                .orElseThrow(() -> new NotFoundException("Collection not found: " + request.collectionId()));
        if (!collection.getTeamId().equals(teamId)) {
            throw new NotFoundException("Collection not found: " + request.collectionId());
        }

        // 2. Create RunResult with RUNNING status
        RunResult runResult = RunResult.builder()
                .teamId(teamId)
                .collectionId(request.collectionId())
                .environmentId(request.environmentId())
                .status(RunStatus.RUNNING)
                .startedAt(Instant.now())
                .iterationCount(Math.max(request.iterationCount(), 1))
                .delayBetweenRequestsMs(request.delayBetweenRequestsMs())
                .dataFilename(request.dataFilename())
                .startedByUserId(userId)
                .build();
        runResult = runResultRepository.save(runResult);
        log.info("Started collection run {} for collection {} ({} iterations)",
                runResult.getId(), request.collectionId(), request.iterationCount());

        // 3. Parse data file
        List<Map<String, String>> dataRows;
        if (request.dataContent() != null && !request.dataContent().isBlank()) {
            dataRows = dataFileParser.parse(request.dataContent(), request.dataFilename());
        } else {
            dataRows = List.of(Map.of());
        }

        // 4. Determine iteration count
        int iterations = Math.max(Math.max(request.iterationCount(), 1), dataRows.size());

        // 5. Collect all requests in execution order
        List<Request> allRequests = collectRequestsInOrder(request.collectionId());

        // 6. Setup cancellation
        AtomicBoolean cancelled = new AtomicBoolean(false);
        runningRuns.put(runResult.getId(), cancelled);

        // Counters
        int totalRequests = 0;
        int passedRequests = 0;
        int failedRequests = 0;
        int totalAssertions = 0;
        int passedAssertions = 0;
        int failedAssertions = 0;
        long totalDurationMs = 0;
        boolean anyFailed = false;

        try {
            for (int iter = 0; iter < iterations; iter++) {
                if (cancelled.get()) {
                    runResult.setStatus(RunStatus.CANCELLED);
                    break;
                }

                Map<String, String> iterationVars = getIterationVars(dataRows, iter);

                // Initialize variable scopes for this iteration
                Map<String, String> globalVars = new LinkedHashMap<>();
                Map<String, String> collectionVars = new LinkedHashMap<>();
                Map<String, String> envVars = new LinkedHashMap<>();
                Map<String, String> localVars = new LinkedHashMap<>(iterationVars);

                for (int reqIdx = 0; reqIdx < allRequests.size(); reqIdx++) {
                    if (cancelled.get()) {
                        runResult.setStatus(RunStatus.CANCELLED);
                        break;
                    }

                    Request req = allRequests.get(reqIdx);
                    totalRequests++;

                    // Build ScriptContext with current variables and request data
                    ScriptContext ctx = new ScriptContext(globalVars, collectionVars, envVars, localVars);
                    ctx.setRequestUrl(req.getUrl());
                    ctx.setRequestMethod(req.getMethod().name());
                    ctx.setRequestHeaders(buildHeaderMap(req));
                    ctx.setRequestBody(req.getBody() != null ? req.getBody().getRawContent() : null);

                    // Run pre-request scripts: collection → folders → request
                    runPreRequestScripts(ctx, collection, req);

                    // Check if script cancelled the request
                    if (ctx.isRequestCancelled()) {
                        RunIteration iteration = RunIteration.builder()
                                .iterationNumber(iter + 1)
                                .requestName(req.getName())
                                .requestMethod(req.getMethod())
                                .requestUrl(req.getUrl())
                                .passed(true)
                                .errorMessage("Skipped by pre-request script")
                                .runResult(runResult)
                                .build();
                        runIterationRepository.save(iteration);

                        // Persist variable state for next request
                        globalVars = new LinkedHashMap<>(ctx.getGlobalVariables());
                        collectionVars = new LinkedHashMap<>(ctx.getCollectionVariables());
                        envVars = new LinkedHashMap<>(ctx.getEnvironmentVariables());
                        localVars = new LinkedHashMap<>(ctx.getLocalVariables());
                        continue;
                    }

                    // Execute HTTP request
                    ProxyResponse response;
                    String resolvedUrl;
                    try {
                        // Merge all script variables for resolution
                        Map<String, String> scriptVars = mergeScriptVariables(ctx);

                        // Resolve URL with accumulated variables
                        resolvedUrl = variableService.resolveVariables(
                                ctx.getRequestUrl(), teamId, request.collectionId(),
                                request.environmentId(), scriptVars);

                        // Build and execute proxy request
                        response = executeHttpRequest(req, ctx, scriptVars, resolvedUrl,
                                teamId, request.collectionId(), request.environmentId());
                    } catch (Exception e) {
                        log.warn("Request execution failed for '{}': {}", req.getName(), e.getMessage());
                        RunIteration iteration = RunIteration.builder()
                                .iterationNumber(iter + 1)
                                .requestName(req.getName())
                                .requestMethod(req.getMethod())
                                .requestUrl(req.getUrl())
                                .passed(false)
                                .errorMessage("Execution error: " + e.getMessage())
                                .runResult(runResult)
                                .build();
                        runIterationRepository.save(iteration);
                        failedRequests++;
                        anyFailed = true;

                        globalVars = new LinkedHashMap<>(ctx.getGlobalVariables());
                        collectionVars = new LinkedHashMap<>(ctx.getCollectionVariables());
                        envVars = new LinkedHashMap<>(ctx.getEnvironmentVariables());
                        localVars = new LinkedHashMap<>(ctx.getLocalVariables());
                        continue;
                    }

                    // Build post-response ScriptContext
                    ScriptContext postCtx = new ScriptContext(
                            ctx.getGlobalVariables(), ctx.getCollectionVariables(),
                            ctx.getEnvironmentVariables(), ctx.getLocalVariables());
                    postCtx.setRequestUrl(ctx.getRequestUrl());
                    postCtx.setRequestMethod(ctx.getRequestMethod());
                    postCtx.setRequestHeaders(ctx.getRequestHeaders());
                    postCtx.setRequestBody(ctx.getRequestBody());
                    postCtx.setResponseStatus(response.statusCode());
                    postCtx.setResponseHeaders(response.responseHeaders());
                    postCtx.setResponseBody(response.responseBody());
                    postCtx.setResponseTimeMs(response.responseTimeMs());

                    // Run post-response scripts: request → folders → collection
                    runPostResponseScripts(postCtx, collection, req);

                    // Capture all assertions (pre-request + post-response)
                    List<ScriptContext.AssertionResult> allAssertionResults = new ArrayList<>();
                    allAssertionResults.addAll(ctx.getAssertions());
                    allAssertionResults.addAll(postCtx.getAssertions());

                    // Determine pass/fail
                    boolean httpOk = response.statusCode() > 0 && response.statusCode() < 400;
                    boolean allAssertionsPassed = allAssertionResults.stream()
                            .allMatch(ScriptContext.AssertionResult::passed);
                    boolean requestPassed = httpOk && allAssertionsPassed;

                    if (!requestPassed) {
                        anyFailed = true;
                        failedRequests++;
                    } else {
                        passedRequests++;
                    }

                    int assertionCount = allAssertionResults.size();
                    int assertionPassedCount = (int) allAssertionResults.stream()
                            .filter(ScriptContext.AssertionResult::passed).count();
                    totalAssertions += assertionCount;
                    passedAssertions += assertionPassedCount;
                    failedAssertions += (assertionCount - assertionPassedCount);
                    totalDurationMs += response.responseTimeMs();

                    // Record RunIteration
                    RunIteration iteration = RunIteration.builder()
                            .iterationNumber(iter + 1)
                            .requestName(req.getName())
                            .requestMethod(req.getMethod())
                            .requestUrl(resolvedUrl)
                            .responseStatus(response.statusCode())
                            .responseTimeMs(response.responseTimeMs())
                            .responseSizeBytes(response.responseSizeBytes())
                            .passed(requestPassed)
                            .assertionResults(serializeAssertions(allAssertionResults))
                            .errorMessage(!httpOk ? response.statusText() : null)
                            .runResult(runResult)
                            .build();
                    runIterationRepository.save(iteration);

                    // Persist variable state for next request
                    globalVars = new LinkedHashMap<>(postCtx.getGlobalVariables());
                    collectionVars = new LinkedHashMap<>(postCtx.getCollectionVariables());
                    envVars = new LinkedHashMap<>(postCtx.getEnvironmentVariables());
                    localVars = new LinkedHashMap<>(postCtx.getLocalVariables());

                    // Delay between requests
                    if (request.delayBetweenRequestsMs() > 0 && reqIdx < allRequests.size() - 1) {
                        sleepBetweenRequests(request.delayBetweenRequestsMs());
                    }
                }

                if (cancelled.get()) {
                    break;
                }
            }
        } finally {
            runningRuns.remove(runResult.getId());
        }

        // Update RunResult with final counters
        runResult.setTotalRequests(totalRequests);
        runResult.setPassedRequests(passedRequests);
        runResult.setFailedRequests(failedRequests);
        runResult.setTotalAssertions(totalAssertions);
        runResult.setPassedAssertions(passedAssertions);
        runResult.setFailedAssertions(failedAssertions);
        runResult.setTotalDurationMs(totalDurationMs);
        runResult.setIterationCount(iterations);
        runResult.setCompletedAt(Instant.now());
        if (runResult.getStatus() != RunStatus.CANCELLED) {
            runResult.setStatus(anyFailed ? RunStatus.FAILED : RunStatus.COMPLETED);
        }
        runResult = runResultRepository.save(runResult);

        log.info("Collection run {} completed — status={}, requests={}/{}, assertions={}/{}",
                runResult.getId(), runResult.getStatus(),
                passedRequests, totalRequests, passedAssertions, totalAssertions);

        // Build response
        List<RunIteration> savedIterations =
                runIterationRepository.findByRunResultIdOrderByIterationNumber(runResult.getId());
        return new RunResultDetailResponse(
                runResultMapper.toResponse(runResult),
                runResultMapper.toIterationResponseList(savedIterations)
        );
    }

    /**
     * Gets all requests in a collection in execution order.
     * Traverses the folder tree depth-first, respecting sortOrder at each level.
     * A folder's requests are returned before its subfolders' requests.
     *
     * @param collectionId the collection ID
     * @return flat list of all requests in execution order
     */
    List<Request> collectRequestsInOrder(UUID collectionId) {
        List<Folder> rootFolders =
                folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId);
        List<Request> allRequests = new ArrayList<>();
        for (Folder folder : rootFolders) {
            collectRequestsFromFolder(folder, allRequests);
        }
        return allRequests;
    }

    /**
     * Gets the variable map for a specific iteration from data rows.
     * Cycles through data rows if iterationIndex exceeds data length.
     *
     * @param dataRows       the parsed data file rows
     * @param iterationIndex zero-based iteration index
     * @return the variable map for this iteration
     */
    Map<String, String> getIterationVars(List<Map<String, String>> dataRows, int iterationIndex) {
        if (dataRows == null || dataRows.isEmpty()) {
            return Map.of();
        }
        return dataRows.get(iterationIndex % dataRows.size());
    }

    /**
     * Gets a run result by ID with team validation.
     *
     * @param runResultId the run result ID
     * @param teamId      the team ID for access validation
     * @return the run result response
     * @throws NotFoundException if not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public RunResultResponse getRunResult(UUID runResultId, UUID teamId) {
        RunResult runResult = findRunResult(runResultId, teamId);
        return runResultMapper.toResponse(runResult);
    }

    /**
     * Gets a run result with all iterations (full detail).
     *
     * @param runResultId the run result ID
     * @param teamId      the team ID for access validation
     * @return the run result detail response with all iterations
     * @throws NotFoundException if not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public RunResultDetailResponse getRunResultDetail(UUID runResultId, UUID teamId) {
        RunResult runResult = findRunResult(runResultId, teamId);
        List<RunIteration> iterations =
                runIterationRepository.findByRunResultIdOrderByIterationNumber(runResult.getId());
        return new RunResultDetailResponse(
                runResultMapper.toResponse(runResult),
                runResultMapper.toIterationResponseList(iterations)
        );
    }

    /**
     * Lists run results for a collection, ordered by creation date descending.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID for filtering
     * @return list of run result responses
     */
    @Transactional(readOnly = true)
    public List<RunResultResponse> getRunResults(UUID collectionId, UUID teamId) {
        return runResultRepository.findByCollectionIdOrderByCreatedAtDesc(collectionId).stream()
                .filter(r -> r.getTeamId().equals(teamId))
                .map(runResultMapper::toResponse)
                .toList();
    }

    /**
     * Lists run results for a team with pagination.
     *
     * @param teamId the team ID
     * @param page   zero-based page number
     * @param size   page size
     * @return paginated run result responses
     */
    @Transactional(readOnly = true)
    public PageResponse<RunResultResponse> getRunResultsPaged(UUID teamId, int page, int size) {
        size = Math.min(Math.max(size, 1), AppConstants.MAX_PAGE_SIZE);
        page = Math.max(page, 0);
        Page<RunResult> results = runResultRepository.findByTeamId(teamId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Page<RunResultResponse> mapped = results.map(runResultMapper::toResponse);
        return PageResponse.from(mapped);
    }

    /**
     * Cancels a running collection run by setting its status and signalling the run loop.
     *
     * @param runResultId the run result ID
     * @param teamId      the team ID for access validation
     * @param userId      the user requesting cancellation
     * @return the updated run result response
     * @throws NotFoundException   if not found or belongs to a different team
     * @throws ValidationException if the run is not in RUNNING status
     */
    public RunResultResponse cancelRun(UUID runResultId, UUID teamId, UUID userId) {
        RunResult runResult = findRunResult(runResultId, teamId);
        if (runResult.getStatus() != RunStatus.RUNNING) {
            throw new ValidationException("Can only cancel a running collection run");
        }

        AtomicBoolean flag = runningRuns.get(runResultId);
        if (flag != null) {
            flag.set(true);
        }

        runResult.setStatus(RunStatus.CANCELLED);
        runResult.setCompletedAt(Instant.now());
        runResult = runResultRepository.save(runResult);
        log.info("Cancelled collection run: {}", runResultId);
        return runResultMapper.toResponse(runResult);
    }

    /**
     * Deletes a run result and all its iterations (cascade).
     *
     * @param runResultId the run result ID
     * @param teamId      the team ID for access validation
     * @throws NotFoundException if not found or belongs to a different team
     */
    public void deleteRunResult(UUID runResultId, UUID teamId) {
        RunResult runResult = findRunResult(runResultId, teamId);
        runResultRepository.delete(runResult);
        log.info("Deleted run result: {}", runResultId);
    }

    // ─── Private Helpers ───

    /**
     * Recursively collects requests from a folder (depth-first, sorted).
     * Adds the folder's own requests first, then recurses into subfolders.
     *
     * @param folder     the folder to collect from
     * @param result     the accumulating list of requests
     */
    private void collectRequestsFromFolder(Folder folder, List<Request> result) {
        List<Request> folderRequests = requestRepository.findByFolderIdOrderBySortOrder(folder.getId());
        result.addAll(folderRequests);

        List<Folder> subFolders = folderRepository.findByParentFolderIdOrderBySortOrder(folder.getId());
        for (Folder sub : subFolders) {
            collectRequestsFromFolder(sub, result);
        }
    }

    /**
     * Builds the folder hierarchy from a request's folder up to the root.
     * Returns a list ordered from innermost (request's direct folder) to outermost (root folder).
     *
     * @param folder the starting folder
     * @return list of folders from innermost to outermost
     */
    private List<Folder> getFolderHierarchy(Folder folder) {
        List<Folder> chain = new ArrayList<>();
        Folder current = folder;
        while (current != null) {
            chain.add(current);
            current = current.getParentFolder();
        }
        return chain;
    }

    /**
     * Converts a request's stored headers into a simple key-value map for script context.
     * Only enabled headers are included.
     *
     * @param request the request entity
     * @return map of header keys to values
     */
    private Map<String, String> buildHeaderMap(Request request) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (request.getHeaders() != null) {
            for (RequestHeader h : request.getHeaders()) {
                if (h.isEnabled()) {
                    headers.put(h.getHeaderKey(), h.getHeaderValue() != null ? h.getHeaderValue() : "");
                }
            }
        }
        return headers;
    }

    /**
     * Runs pre-request scripts in order: collection → folder chain (outermost first) → request.
     * Stops if any script cancels the request.
     *
     * @param ctx        the mutable script context
     * @param collection the collection (may have a pre-request script)
     * @param request    the request being executed
     */
    private void runPreRequestScripts(ScriptContext ctx, Collection collection, Request request) {
        // 1. Collection pre-request script
        if (collection.getPreRequestScript() != null && !collection.getPreRequestScript().isBlank()) {
            scriptEngineService.executePreRequestScript(collection.getPreRequestScript(), ctx);
        }
        if (ctx.isRequestCancelled()) return;

        // 2. Folder chain — outermost first
        List<Folder> hierarchy = getFolderHierarchy(request.getFolder());
        List<Folder> outermostFirst = new ArrayList<>(hierarchy);
        Collections.reverse(outermostFirst);
        for (Folder folder : outermostFirst) {
            if (ctx.isRequestCancelled()) return;
            if (folder.getPreRequestScript() != null && !folder.getPreRequestScript().isBlank()) {
                scriptEngineService.executePreRequestScript(folder.getPreRequestScript(), ctx);
            }
        }
        if (ctx.isRequestCancelled()) return;

        // 3. Request pre-request script
        requestScriptRepository.findByRequestIdAndScriptType(request.getId(), ScriptType.PRE_REQUEST)
                .ifPresent(script -> {
                    if (script.getContent() != null && !script.getContent().isBlank()) {
                        scriptEngineService.executePreRequestScript(script.getContent(), ctx);
                    }
                });
    }

    /**
     * Runs post-response scripts in order: request → folder chain (innermost first) → collection.
     *
     * @param ctx        the mutable script context with response data
     * @param collection the collection (may have a post-response script)
     * @param request    the request that was executed
     */
    private void runPostResponseScripts(ScriptContext ctx, Collection collection, Request request) {
        // 1. Request post-response script
        requestScriptRepository.findByRequestIdAndScriptType(request.getId(), ScriptType.POST_RESPONSE)
                .ifPresent(script -> {
                    if (script.getContent() != null && !script.getContent().isBlank()) {
                        scriptEngineService.executePostResponseScript(script.getContent(), ctx);
                    }
                });

        // 2. Folder chain — innermost first
        List<Folder> hierarchy = getFolderHierarchy(request.getFolder());
        for (Folder folder : hierarchy) {
            if (folder.getPostResponseScript() != null && !folder.getPostResponseScript().isBlank()) {
                scriptEngineService.executePostResponseScript(folder.getPostResponseScript(), ctx);
            }
        }

        // 3. Collection post-response script
        if (collection.getPostResponseScript() != null && !collection.getPostResponseScript().isBlank()) {
            scriptEngineService.executePostResponseScript(collection.getPostResponseScript(), ctx);
        }
    }

    /**
     * Executes an HTTP request via RequestProxyService with script-resolved data.
     *
     * @param request       the stored request entity
     * @param ctx           the script context (with potentially modified request data)
     * @param scriptVars    merged variable map from all script scopes
     * @param resolvedUrl   the already-resolved URL
     * @param teamId        team ID for variable resolution
     * @param collectionId  collection ID for variable resolution
     * @param environmentId environment ID for variable resolution
     * @return the proxy response
     */
    private ProxyResponse executeHttpRequest(Request request, ScriptContext ctx,
                                              Map<String, String> scriptVars, String resolvedUrl,
                                              UUID teamId, UUID collectionId, UUID environmentId) {
        // Build header entries from script context
        List<SaveRequestHeadersRequest.RequestHeaderEntry> headerEntries = new ArrayList<>();
        if (ctx.getRequestHeaders() != null) {
            for (Map.Entry<String, String> entry : ctx.getRequestHeaders().entrySet()) {
                String resolvedKey = variableService.resolveVariables(
                        entry.getKey(), teamId, collectionId, environmentId, scriptVars);
                String resolvedValue = variableService.resolveVariables(
                        entry.getValue(), teamId, collectionId, environmentId, scriptVars);
                headerEntries.add(new SaveRequestHeadersRequest.RequestHeaderEntry(
                        resolvedKey, resolvedValue, null, true));
            }
        }

        // Build body from stored request with script-modified raw content
        SaveRequestBodyRequest bodyRequest = null;
        if (request.getBody() != null) {
            String resolvedBody = variableService.resolveVariables(
                    ctx.getRequestBody(), teamId, collectionId, environmentId, scriptVars);
            bodyRequest = new SaveRequestBodyRequest(
                    request.getBody().getBodyType(),
                    resolvedBody,
                    request.getBody().getFormData(),
                    request.getBody().getGraphqlQuery(),
                    request.getBody().getGraphqlVariables(),
                    request.getBody().getBinaryFileName());
        }

        // Resolve auth via inheritance chain
        SaveRequestAuthRequest authRequest = requestProxyService.resolveInheritedAuth(request);

        // Parse method from script context (may have been modified)
        HttpMethod method = HttpMethod.valueOf(ctx.getRequestMethod());

        SendRequestProxyRequest proxyRequest = new SendRequestProxyRequest(
                method, resolvedUrl, headerEntries, bodyRequest, authRequest,
                environmentId, collectionId, false, null, true);

        return requestProxyService.executeRequest(proxyRequest, teamId, UUID.randomUUID());
    }

    /**
     * Merges all variable scopes from a script context into a single map for resolution.
     * Priority: global (lowest) → collection → environment → local (highest).
     *
     * @param ctx the script context
     * @return merged variable map
     */
    private Map<String, String> mergeScriptVariables(ScriptContext ctx) {
        Map<String, String> merged = new LinkedHashMap<>();
        merged.putAll(ctx.getGlobalVariables());
        merged.putAll(ctx.getCollectionVariables());
        merged.putAll(ctx.getEnvironmentVariables());
        merged.putAll(ctx.getLocalVariables());
        return merged;
    }

    /**
     * Serializes assertion results to JSON for storage in RunIteration.
     *
     * @param assertions the assertion results
     * @return JSON string, or null if empty
     */
    private String serializeAssertions(List<ScriptContext.AssertionResult> assertions) {
        if (assertions == null || assertions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(assertions);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize assertion results: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Pauses execution between requests during a collection run.
     * Package-private for test verification.
     *
     * @param delayMs the delay in milliseconds
     */
    void sleepBetweenRequests(int delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Finds a run result by ID with team validation.
     *
     * @param runResultId the run result ID
     * @param teamId      the team ID for access validation
     * @return the RunResult entity
     * @throws NotFoundException if not found or belongs to a different team
     */
    private RunResult findRunResult(UUID runResultId, UUID teamId) {
        RunResult runResult = runResultRepository.findById(runResultId)
                .orElseThrow(() -> new NotFoundException("Run result not found: " + runResultId));
        if (!runResult.getTeamId().equals(teamId)) {
            throw new NotFoundException("Run result not found: " + runResultId);
        }
        return runResult;
    }
}
