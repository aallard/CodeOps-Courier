package com.codeops.courier.entity;

import com.codeops.courier.entity.enums.SharePermission;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "collection_shares",
        uniqueConstraints = @UniqueConstraint(columnNames = {"collection_id", "shared_with_user_id"}),
        indexes = {
                @Index(name = "idx_collection_shares_collection_id", columnList = "collection_id"),
                @Index(name = "idx_collection_shares_shared_with", columnList = "shared_with_user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionShare extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SharePermission permission;

    @Column(name = "shared_with_user_id", nullable = false)
    private UUID sharedWithUserId;

    @Column(name = "shared_by_user_id", nullable = false)
    private UUID sharedByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;
}
