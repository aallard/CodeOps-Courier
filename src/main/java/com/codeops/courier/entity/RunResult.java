package com.codeops.courier.entity;

import com.codeops.courier.entity.enums.RunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "run_results",
        indexes = {
                @Index(name = "idx_run_results_team_id", columnList = "team_id"),
                @Index(name = "idx_run_results_collection_id", columnList = "collection_id"),
                @Index(name = "idx_run_results_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunResult extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Column(name = "environment_id")
    private UUID environmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Builder.Default
    @Column(name = "total_requests", nullable = false)
    private int totalRequests = 0;

    @Builder.Default
    @Column(name = "passed_requests", nullable = false)
    private int passedRequests = 0;

    @Builder.Default
    @Column(name = "failed_requests", nullable = false)
    private int failedRequests = 0;

    @Builder.Default
    @Column(name = "total_assertions", nullable = false)
    private int totalAssertions = 0;

    @Builder.Default
    @Column(name = "passed_assertions", nullable = false)
    private int passedAssertions = 0;

    @Builder.Default
    @Column(name = "failed_assertions", nullable = false)
    private int failedAssertions = 0;

    @Builder.Default
    @Column(name = "total_duration_ms", nullable = false)
    private long totalDurationMs = 0;

    @Builder.Default
    @Column(name = "iteration_count", nullable = false)
    private int iterationCount = 1;

    @Builder.Default
    @Column(name = "delay_between_requests_ms", nullable = false)
    private int delayBetweenRequestsMs = 0;

    @Column(name = "data_filename", length = 500)
    private String dataFilename;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "started_by_user_id", nullable = false)
    private UUID startedByUserId;

    @Builder.Default
    @OneToMany(mappedBy = "runResult", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RunIteration> iterations = new ArrayList<>();
}
