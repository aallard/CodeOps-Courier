package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.StartCollectionRunRequest;
import com.codeops.courier.dto.response.PageResponse;
import com.codeops.courier.dto.response.RunResultDetailResponse;
import com.codeops.courier.dto.response.RunResultResponse;
import com.codeops.courier.security.SecurityUtils;
import com.codeops.courier.service.CollectionRunnerService;
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
 * REST controller for the collection runner. Provides endpoints to start
 * collection runs, retrieve run results with pagination, view run detail
 * with iterations, cancel running runs, and delete run results.
 * All endpoints require authentication and the ADMIN role.
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/runner")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Runner", description = "Collection runner for batch request execution")
public class RunnerController {

    private final CollectionRunnerService runnerService;

    /**
     * Starts a collection run with the specified configuration.
     *
     * @param teamId  the team ID from the request header
     * @param request the run configuration with collection, environment, iterations, and delay
     * @return the run result detail with all iteration results
     */
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public RunResultDetailResponse startRun(@RequestHeader("X-Team-ID") UUID teamId,
                                             @Valid @RequestBody StartCollectionRunRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Starting collection run for collection {} with {} iterations for team {}",
                request.collectionId(), request.iterationCount(), teamId);
        return runnerService.startRun(request, teamId, userId);
    }

    /**
     * Returns paginated run results for the team.
     *
     * @param teamId the team ID from the request header
     * @param page   zero-based page number (default 0)
     * @param size   page size (default 20)
     * @return paginated run result summaries
     */
    @GetMapping("/results")
    public PageResponse<RunResultResponse> getRunResults(@RequestHeader("X-Team-ID") UUID teamId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return runnerService.getRunResultsPaged(teamId, page, size);
    }

    /**
     * Returns all run results for a specific collection.
     *
     * @param collectionId the collection ID
     * @param teamId       the team ID from the request header
     * @return list of run result summaries for the collection
     */
    @GetMapping("/results/collection/{collectionId}")
    public List<RunResultResponse> getRunResultsByCollection(@PathVariable UUID collectionId,
                                                              @RequestHeader("X-Team-ID") UUID teamId) {
        return runnerService.getRunResults(collectionId, teamId);
    }

    /**
     * Returns a single run result summary.
     *
     * @param runResultId the run result ID
     * @param teamId      the team ID from the request header
     * @return the run result summary
     */
    @GetMapping("/results/{runResultId}")
    public RunResultResponse getRunResult(@PathVariable UUID runResultId,
                                           @RequestHeader("X-Team-ID") UUID teamId) {
        return runnerService.getRunResult(runResultId, teamId);
    }

    /**
     * Returns a run result with all iteration details.
     *
     * @param runResultId the run result ID
     * @param teamId      the team ID from the request header
     * @return the run result with iteration results
     */
    @GetMapping("/results/{runResultId}/detail")
    public RunResultDetailResponse getRunResultDetail(@PathVariable UUID runResultId,
                                                       @RequestHeader("X-Team-ID") UUID teamId) {
        return runnerService.getRunResultDetail(runResultId, teamId);
    }

    /**
     * Cancels a running collection run.
     *
     * @param runResultId the run result ID to cancel
     * @param teamId      the team ID from the request header
     * @return the updated run result summary
     */
    @PostMapping("/results/{runResultId}/cancel")
    public RunResultResponse cancelRun(@PathVariable UUID runResultId,
                                        @RequestHeader("X-Team-ID") UUID teamId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Cancelling run {} for team {}", runResultId, teamId);
        return runnerService.cancelRun(runResultId, teamId, userId);
    }

    /**
     * Deletes a run result and all its iterations.
     *
     * @param runResultId the run result ID
     * @param teamId      the team ID from the request header
     */
    @DeleteMapping("/results/{runResultId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRunResult(@PathVariable UUID runResultId,
                                 @RequestHeader("X-Team-ID") UUID teamId) {
        log.info("Deleting run result {} for team {}", runResultId, teamId);
        runnerService.deleteRunResult(runResultId, teamId);
    }
}
