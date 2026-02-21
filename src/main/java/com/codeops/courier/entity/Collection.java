package com.codeops.courier.entity;

import com.codeops.courier.entity.enums.AuthType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "collections",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "name"}),
        indexes = {
                @Index(name = "idx_collections_team_id", columnList = "team_id"),
                @Index(name = "idx_collections_created_by", columnList = "created_by")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Collection extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "pre_request_script", columnDefinition = "TEXT")
    private String preRequestScript;

    @Column(name = "post_response_script", columnDefinition = "TEXT")
    private String postResponseScript;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type")
    private AuthType authType;

    @Column(name = "auth_config", columnDefinition = "TEXT")
    private String authConfig;

    @Builder.Default
    @Column(name = "is_shared", nullable = false)
    private boolean isShared = false;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Builder.Default
    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Folder> folders = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EnvironmentVariable> variables = new ArrayList<>();
}
