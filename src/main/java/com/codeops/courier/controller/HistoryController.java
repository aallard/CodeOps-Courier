package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.response.PageResponse;
import com.codeops.courier.dto.response.RequestHistoryDetailResponse;
import com.codeops.courier.dto.response.RequestHistoryResponse;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.service.HistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing request execution history.
 * Provides paginated retrieval, filtering by user or HTTP method, search,
 * detail views, and cleanup operations. All endpoints require authentication
 * and the ADMIN role.
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/history")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "History", description = "Request execution history, search, and cleanup")
public class HistoryController {

    private final HistoryService historyService;

    /**
     * Returns paginated request history for the team, ordered by most recent first.
     *
     * @param teamId the team ID from the request header
     * @param page   zero-based page number (default 0)
     * @param size   page size (default 20)
     * @return paginated history responses
     */
    @GetMapping
    public PageResponse<RequestHistoryResponse> getHistory(@RequestHeader("X-Team-ID") UUID teamId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return historyService.getHistory(teamId, page, size);
    }

    /**
     * Returns paginated request history for a specific user within the team.
     *
     * @param teamId the team ID from the request header
     * @param userId the user ID to filter by
     * @param page   zero-based page number (default 0)
     * @param size   page size (default 20)
     * @return paginated history responses for the user
     */
    @GetMapping("/user/{userId}")
    public PageResponse<RequestHistoryResponse> getUserHistory(@RequestHeader("X-Team-ID") UUID teamId,
                                                                @PathVariable UUID userId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return historyService.getUserHistory(teamId, userId, page, size);
    }

    /**
     * Returns paginated history filtered by HTTP method.
     *
     * @param teamId the team ID from the request header
     * @param method the HTTP method to filter by
     * @param page   zero-based page number (default 0)
     * @param size   page size (default 20)
     * @return paginated history responses matching the method
     */
    @GetMapping("/method/{method}")
    public PageResponse<RequestHistoryResponse> getHistoryByMethod(@RequestHeader("X-Team-ID") UUID teamId,
                                                                     @PathVariable HttpMethod method,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return historyService.getHistoryByMethod(teamId, method, page, size);
    }

    /**
     * Searches history by URL fragment with case-insensitive matching.
     *
     * @param teamId the team ID from the request header
     * @param query  the URL substring to search for
     * @return matching history responses
     */
    @GetMapping("/search")
    public List<RequestHistoryResponse> searchHistory(@RequestHeader("X-Team-ID") UUID teamId,
                                                       @RequestParam String query) {
        return historyService.searchHistory(teamId, query);
    }

    /**
     * Returns full detail for a single history entry including request/response bodies.
     *
     * @param historyId the history entry ID
     * @param teamId    the team ID from the request header
     * @return the detail response with headers and bodies
     */
    @GetMapping("/{historyId}")
    public RequestHistoryDetailResponse getHistoryDetail(@PathVariable UUID historyId,
                                                          @RequestHeader("X-Team-ID") UUID teamId) {
        return historyService.getHistoryDetail(historyId, teamId);
    }

    /**
     * Deletes a single history entry.
     *
     * @param historyId the history entry ID
     * @param teamId    the team ID from the request header
     */
    @DeleteMapping("/{historyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHistory(@PathVariable UUID historyId,
                               @RequestHeader("X-Team-ID") UUID teamId) {
        log.info("Deleting history entry {} for team {}", historyId, teamId);
        historyService.deleteHistoryEntry(historyId, teamId);
    }

    /**
     * Clears history for the team. If {@code daysToRetain} is provided, only entries
     * older than that many days are removed. If omitted, all history is cleared.
     *
     * @param teamId       the team ID from the request header
     * @param daysToRetain optional number of days of history to retain
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(@RequestHeader("X-Team-ID") UUID teamId,
                              @RequestParam(required = false) Integer daysToRetain) {
        if (daysToRetain != null) {
            log.info("Clearing history older than {} days for team {}", daysToRetain, teamId);
            historyService.clearOldHistory(teamId, daysToRetain);
        } else {
            log.info("Clearing all history for team {}", teamId);
            historyService.clearTeamHistory(teamId);
        }
    }
}
