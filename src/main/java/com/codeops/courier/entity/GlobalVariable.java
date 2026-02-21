package com.codeops.courier.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "global_variables",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "variable_key"}),
        indexes = {
                @Index(name = "idx_global_variables_team_id", columnList = "team_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalVariable extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

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
}
