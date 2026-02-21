package com.codeops.courier.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "environment_variables",
        indexes = {
                @Index(name = "idx_env_variables_environment_id", columnList = "environment_id"),
                @Index(name = "idx_env_variables_collection_id", columnList = "collection_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnvironmentVariable extends BaseEntity {

    @Column(name = "variable_key", nullable = false, length = 500)
    private String variableKey;

    @Column(name = "variable_value", length = 5000)
    private String variableValue;

    @Builder.Default
    @Column(name = "is_secret", nullable = false)
    private boolean isSecret = false;

    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    @Column(nullable = false, length = 20)
    private String scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private Environment environment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private Collection collection;
}
