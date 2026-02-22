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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private RequestHistoryRepository historyRepository;

    @Mock
    private RequestHistoryMapper historyMapper;

    @InjectMocks
    private HistoryService service;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID HISTORY_ID = UUID.randomUUID();

    // ─── getHistory Tests ───

    @Test
    void getHistory_paged_success() {
        RequestHistory entity = buildHistory();
        Page<RequestHistory> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1);
        when(historyRepository.findByTeamIdOrderByCreatedAtDesc(eq(TEAM_ID), any(PageRequest.class)))
                .thenReturn(page);
        when(historyMapper.toResponse(entity)).thenReturn(buildResponse(entity));

        PageResponse<RequestHistoryResponse> result = service.getHistory(TEAM_ID, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.isLast()).isTrue();
    }

    @Test
    void getHistory_emptyResult() {
        Page<RequestHistory> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(historyRepository.findByTeamIdOrderByCreatedAtDesc(eq(TEAM_ID), any(PageRequest.class)))
                .thenReturn(emptyPage);

        PageResponse<RequestHistoryResponse> result = service.getHistory(TEAM_ID, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
    }

    @Test
    void getHistory_respectsMaxPageSize() {
        Page<RequestHistory> emptyPage = new PageImpl<>(Collections.emptyList(),
                PageRequest.of(0, AppConstants.MAX_PAGE_SIZE), 0);
        when(historyRepository.findByTeamIdOrderByCreatedAtDesc(eq(TEAM_ID),
                eq(PageRequest.of(0, AppConstants.MAX_PAGE_SIZE))))
                .thenReturn(emptyPage);

        service.getHistory(TEAM_ID, 0, 500);

        verify(historyRepository).findByTeamIdOrderByCreatedAtDesc(
                eq(TEAM_ID), eq(PageRequest.of(0, AppConstants.MAX_PAGE_SIZE)));
    }

    // ─── getUserHistory Tests ───

    @Test
    void getUserHistory_success() {
        RequestHistory entity = buildHistory();
        Page<RequestHistory> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1);
        when(historyRepository.findByTeamIdAndUserId(eq(TEAM_ID), eq(USER_ID), any(PageRequest.class)))
                .thenReturn(page);
        when(historyMapper.toResponse(entity)).thenReturn(buildResponse(entity));

        PageResponse<RequestHistoryResponse> result = service.getUserHistory(TEAM_ID, USER_ID, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).userId()).isEqualTo(USER_ID);
    }

    // ─── getHistoryDetail Tests ───

    @Test
    void getHistoryDetail_success() {
        RequestHistory entity = buildHistory();
        when(historyRepository.findById(HISTORY_ID)).thenReturn(Optional.of(entity));
        RequestHistoryDetailResponse detail = buildDetailResponse(entity);
        when(historyMapper.toDetailResponse(entity)).thenReturn(detail);

        RequestHistoryDetailResponse result = service.getHistoryDetail(HISTORY_ID, TEAM_ID);

        assertThat(result.id()).isEqualTo(HISTORY_ID);
        assertThat(result.requestHeaders()).isEqualTo("{\"Accept\":\"application/json\"}");
        assertThat(result.requestBody()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void getHistoryDetail_notFound_throws() {
        when(historyRepository.findById(HISTORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistoryDetail(HISTORY_ID, TEAM_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("History entry not found");
    }

    @Test
    void getHistoryDetail_wrongTeam_throws() {
        RequestHistory entity = buildHistory();
        entity.setTeamId(UUID.randomUUID());
        when(historyRepository.findById(HISTORY_ID)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.getHistoryDetail(HISTORY_ID, TEAM_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── searchHistory Tests ───

    @Test
    void searchHistory_findsPartialMatch() {
        RequestHistory entity = buildHistory();
        when(historyRepository.findByTeamIdAndRequestUrlContainingIgnoreCase(TEAM_ID, "example"))
                .thenReturn(List.of(entity));
        when(historyMapper.toResponse(entity)).thenReturn(buildResponse(entity));

        List<RequestHistoryResponse> result = service.searchHistory(TEAM_ID, "example");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).requestUrl()).contains("example");
    }

    @Test
    void searchHistory_noResults() {
        when(historyRepository.findByTeamIdAndRequestUrlContainingIgnoreCase(TEAM_ID, "nonexistent"))
                .thenReturn(Collections.emptyList());

        List<RequestHistoryResponse> result = service.searchHistory(TEAM_ID, "nonexistent");

        assertThat(result).isEmpty();
    }

    // ─── getHistoryByMethod Tests ───

    @Test
    void getHistoryByMethod_success() {
        RequestHistory entity = buildHistory();
        Page<RequestHistory> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1);
        when(historyRepository.findByTeamIdAndRequestMethod(eq(TEAM_ID), eq(HttpMethod.GET), any(PageRequest.class)))
                .thenReturn(page);
        when(historyMapper.toResponse(entity)).thenReturn(buildResponse(entity));

        PageResponse<RequestHistoryResponse> result = service.getHistoryByMethod(TEAM_ID, HttpMethod.GET, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).requestMethod()).isEqualTo(HttpMethod.GET);
    }

    // ─── deleteHistoryEntry Tests ───

    @Test
    void deleteHistoryEntry_success() {
        RequestHistory entity = buildHistory();
        when(historyRepository.findById(HISTORY_ID)).thenReturn(Optional.of(entity));

        service.deleteHistoryEntry(HISTORY_ID, TEAM_ID);

        verify(historyRepository).delete(entity);
    }

    @Test
    void deleteHistoryEntry_notFound_throws() {
        when(historyRepository.findById(HISTORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteHistoryEntry(HISTORY_ID, TEAM_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("History entry not found");
    }

    // ─── clearTeamHistory Tests ───

    @Test
    void clearTeamHistory_success() {
        service.clearTeamHistory(TEAM_ID);

        verify(historyRepository).deleteByTeamId(TEAM_ID);
    }

    // ─── clearOldHistory Tests ───

    @Test
    void clearOldHistory_deletesExpired() {
        RequestHistory old1 = buildHistory();
        RequestHistory old2 = buildHistory();
        old2.setId(UUID.randomUUID());
        when(historyRepository.findByTeamIdAndCreatedAtBefore(eq(TEAM_ID), any(Instant.class)))
                .thenReturn(List.of(old1, old2));

        int count = service.clearOldHistory(TEAM_ID, 30);

        assertThat(count).isEqualTo(2);
        verify(historyRepository).deleteAll(List.of(old1, old2));
    }

    // ─── getHistoryCount Tests ───

    @Test
    void getHistoryCount_success() {
        when(historyRepository.countByTeamId(TEAM_ID)).thenReturn(42L);

        long count = service.getHistoryCount(TEAM_ID);

        assertThat(count).isEqualTo(42L);
    }

    // ─── Helper Methods ───

    private RequestHistory buildHistory() {
        RequestHistory history = new RequestHistory();
        history.setId(HISTORY_ID);
        history.setTeamId(TEAM_ID);
        history.setUserId(USER_ID);
        history.setRequestMethod(HttpMethod.GET);
        history.setRequestUrl("https://api.example.com/users");
        history.setRequestHeaders("{\"Accept\":\"application/json\"}");
        history.setRequestBody("{\"key\":\"value\"}");
        history.setResponseStatus(200);
        history.setResponseHeaders("{\"Content-Type\":\"application/json\"}");
        history.setResponseBody("{\"result\":\"ok\"}");
        history.setResponseSizeBytes(1024L);
        history.setResponseTimeMs(150L);
        history.setContentType("application/json");
        history.setCreatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        return history;
    }

    private RequestHistoryResponse buildResponse(RequestHistory entity) {
        return new RequestHistoryResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getRequestMethod(),
                entity.getRequestUrl(),
                entity.getResponseStatus(),
                entity.getResponseTimeMs(),
                entity.getResponseSizeBytes(),
                entity.getContentType(),
                entity.getCollectionId(),
                entity.getRequestId(),
                entity.getEnvironmentId(),
                entity.getCreatedAt()
        );
    }

    private RequestHistoryDetailResponse buildDetailResponse(RequestHistory entity) {
        return new RequestHistoryDetailResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getRequestMethod(),
                entity.getRequestUrl(),
                entity.getRequestHeaders(),
                entity.getRequestBody(),
                entity.getResponseStatus(),
                entity.getResponseHeaders(),
                entity.getResponseBody(),
                entity.getResponseSizeBytes(),
                entity.getResponseTimeMs(),
                entity.getContentType(),
                entity.getCollectionId(),
                entity.getRequestId(),
                entity.getEnvironmentId(),
                entity.getCreatedAt()
        );
    }
}
