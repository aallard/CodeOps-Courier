package com.codeops.courier.repository;

import com.codeops.courier.entity.RequestHistory;
import com.codeops.courier.entity.enums.HttpMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RequestHistoryRepository extends JpaRepository<RequestHistory, UUID> {

    Page<RequestHistory> findByTeamIdOrderByCreatedAtDesc(UUID teamId, Pageable pageable);

    Page<RequestHistory> findByTeamIdAndUserId(UUID teamId, UUID userId, Pageable pageable);

    Page<RequestHistory> findByTeamIdAndRequestMethod(UUID teamId, HttpMethod method, Pageable pageable);

    List<RequestHistory> findByTeamIdAndRequestUrlContainingIgnoreCase(UUID teamId, String urlFragment);

    void deleteByTeamIdAndCreatedAtBefore(UUID teamId, Instant cutoff);
}
