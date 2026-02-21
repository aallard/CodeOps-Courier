package com.codeops.courier.repository;

import com.codeops.courier.entity.RunResult;
import com.codeops.courier.entity.enums.RunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RunResultRepository extends JpaRepository<RunResult, UUID> {

    List<RunResult> findByTeamIdOrderByCreatedAtDesc(UUID teamId);

    List<RunResult> findByCollectionIdOrderByCreatedAtDesc(UUID collectionId);

    Page<RunResult> findByTeamId(UUID teamId, Pageable pageable);

    List<RunResult> findByStatus(RunStatus status);
}
