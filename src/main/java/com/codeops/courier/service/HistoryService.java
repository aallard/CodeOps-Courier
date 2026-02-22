package com.codeops.courier.service;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.mapper.RequestHistoryMapper;
import com.codeops.courier.dto.response.PageResponse;
import com.codeops.courier.dto.response.RequestHistoryDetailResponse;
import com.codeops.courier.dto.response.RequestHistoryResponse;
import com.codeops.courier.entity.RequestHistory;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.repository.RequestHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing request history including paginated retrieval, search,
 * filtering, detail views, and cleanup operations with retention policy support.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class HistoryService {

    private final RequestHistoryRepository historyRepository;
    private final RequestHistoryMapper historyMapper;

    /**
     * Returns paginated request history for a team, ordered by most recent first.
     *
     * @param teamId the team ID
     * @param page   zero-based page number
     * @param size   page size (clamped to MAX_PAGE_SIZE)
     * @return paginated history responses (summary — no request/response bodies)
     */
    @Transactional(readOnly = true)
    public PageResponse<RequestHistoryResponse> getHistory(UUID teamId, int page, int size) {
        int clampedSize = Math.min(Math.max(size, 1), AppConstants.MAX_PAGE_SIZE);
        Page<RequestHistory> pagedResult = historyRepository.findByTeamIdOrderByCreatedAtDesc(
                teamId, PageRequest.of(page, clampedSize));
        return toPagedResponse(pagedResult);
    }

    /**
     * Returns paginated request history for a specific user within a team.
     *
     * @param teamId the team ID
     * @param userId the user ID to filter by
     * @param page   zero-based page number
     * @param size   page size (clamped to MAX_PAGE_SIZE)
     * @return paginated history responses for the user
     */
    @Transactional(readOnly = true)
    public PageResponse<RequestHistoryResponse> getUserHistory(UUID teamId, UUID userId, int page, int size) {
        int clampedSize = Math.min(Math.max(size, 1), AppConstants.MAX_PAGE_SIZE);
        Page<RequestHistory> pagedResult = historyRepository.findByTeamIdAndUserId(
                teamId, userId, PageRequest.of(page, clampedSize));
        return toPagedResponse(pagedResult);
    }

    /**
     * Returns full detail for a single history entry including request/response bodies.
     *
     * @param historyId the history entry ID
     * @param teamId    the team ID for access validation
     * @return the detail response with headers and bodies
     * @throws NotFoundException if the entry is not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public RequestHistoryDetailResponse getHistoryDetail(UUID historyId, UUID teamId) {
        RequestHistory history = findHistoryAndValidateTeam(historyId, teamId);
        return historyMapper.toDetailResponse(history);
    }

    /**
     * Searches history by URL fragment with case-insensitive matching.
     *
     * @param teamId      the team ID
     * @param urlFragment the URL substring to search for
     * @return matching history responses
     */
    @Transactional(readOnly = true)
    public List<RequestHistoryResponse> searchHistory(UUID teamId, String urlFragment) {
        return historyRepository.findByTeamIdAndRequestUrlContainingIgnoreCase(teamId, urlFragment).stream()
                .map(historyMapper::toResponse)
                .toList();
    }

    /**
     * Returns paginated history filtered by HTTP method.
     *
     * @param teamId the team ID
     * @param method the HTTP method to filter by
     * @param page   zero-based page number
     * @param size   page size (clamped to MAX_PAGE_SIZE)
     * @return paginated history responses matching the method
     */
    @Transactional(readOnly = true)
    public PageResponse<RequestHistoryResponse> getHistoryByMethod(UUID teamId, HttpMethod method,
                                                                    int page, int size) {
        int clampedSize = Math.min(Math.max(size, 1), AppConstants.MAX_PAGE_SIZE);
        Page<RequestHistory> pagedResult = historyRepository.findByTeamIdAndRequestMethod(
                teamId, method, PageRequest.of(page, clampedSize));
        return toPagedResponse(pagedResult);
    }

    /**
     * Deletes a single history entry.
     *
     * @param historyId the history entry ID
     * @param teamId    the team ID for access validation
     * @throws NotFoundException if the entry is not found or belongs to a different team
     */
    public void deleteHistoryEntry(UUID historyId, UUID teamId) {
        RequestHistory history = findHistoryAndValidateTeam(historyId, teamId);
        historyRepository.delete(history);
        log.info("Deleted history entry {}", historyId);
    }

    /**
     * Clears all history entries for a team.
     *
     * @param teamId the team ID
     */
    public void clearTeamHistory(UUID teamId) {
        historyRepository.deleteByTeamId(teamId);
        log.info("Cleared all history for team {}", teamId);
    }

    /**
     * Clears history entries older than the specified retention period.
     * Used for automatic cleanup and retention policy enforcement.
     *
     * @param teamId       the team ID
     * @param daysToRetain the number of days of history to retain
     * @return the number of deleted entries
     */
    public int clearOldHistory(UUID teamId, int daysToRetain) {
        Instant cutoff = Instant.now().minus(daysToRetain, ChronoUnit.DAYS);
        List<RequestHistory> toDelete = historyRepository.findByTeamIdAndCreatedAtBefore(teamId, cutoff);
        int count = toDelete.size();
        historyRepository.deleteAll(toDelete);
        log.info("Cleared {} old history entries for team {} (older than {} days)", count, teamId, daysToRetain);
        return count;
    }

    /**
     * Returns the total number of history entries for a team.
     *
     * @param teamId the team ID
     * @return the count of history entries
     */
    @Transactional(readOnly = true)
    public long getHistoryCount(UUID teamId) {
        return historyRepository.countByTeamId(teamId);
    }

    /**
     * Finds a history entry by ID and validates it belongs to the specified team.
     *
     * @param historyId the history entry ID
     * @param teamId    the expected team ID
     * @return the history entity
     * @throws NotFoundException if not found or wrong team
     */
    private RequestHistory findHistoryAndValidateTeam(UUID historyId, UUID teamId) {
        RequestHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new NotFoundException("History entry not found: " + historyId));
        if (!history.getTeamId().equals(teamId)) {
            throw new NotFoundException("History entry not found: " + historyId);
        }
        return history;
    }

    /**
     * Converts a Spring Page of RequestHistory entities to a PageResponse of DTOs.
     *
     * @param pagedResult the Spring Page to convert
     * @return the PageResponse with mapped content
     */
    private PageResponse<RequestHistoryResponse> toPagedResponse(Page<RequestHistory> pagedResult) {
        List<RequestHistoryResponse> content = pagedResult.getContent().stream()
                .map(historyMapper::toResponse)
                .toList();
        return new PageResponse<>(
                content,
                pagedResult.getNumber(),
                pagedResult.getSize(),
                pagedResult.getTotalElements(),
                pagedResult.getTotalPages(),
                pagedResult.isLast()
        );
    }
}
