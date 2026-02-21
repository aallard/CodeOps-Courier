package com.codeops.courier.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forks",
        indexes = {
                @Index(name = "idx_forks_source_collection_id", columnList = "source_collection_id"),
                @Index(name = "idx_forks_forked_by_user_id", columnList = "forked_by_user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fork extends BaseEntity {

    @Column(name = "forked_by_user_id", nullable = false)
    private UUID forkedByUserId;

    @Column(name = "forked_at", nullable = false)
    private Instant forkedAt;

    @Column(length = 200)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_collection_id", nullable = false)
    private Collection sourceCollection;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forked_collection_id", nullable = false, unique = true)
    private Collection forkedCollection;
}
