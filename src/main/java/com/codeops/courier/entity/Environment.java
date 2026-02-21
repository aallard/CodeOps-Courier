package com.codeops.courier.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "environments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "name"}),
        indexes = {
                @Index(name = "idx_environments_team_id", columnList = "team_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Environment extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = false;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Builder.Default
    @OneToMany(mappedBy = "environment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EnvironmentVariable> variables = new ArrayList<>();
}
