package com.codeops.courier.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merge_requests",
        indexes = {
                @Index(name = "idx_merge_requests_source_fork_id", columnList = "source_fork_id"),
                @Index(name = "idx_merge_requests_target_collection_id", columnList = "target_collection_id"),
                @Index(name = "idx_merge_requests_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MergeRequest extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 5000)
    private String description;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "conflict_details", columnDefinition = "TEXT")
    private String conflictDetails;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_fork_id", nullable = false)
    private Fork sourceFork;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_collection_id", nullable = false)
    private Collection targetCollection;
}
