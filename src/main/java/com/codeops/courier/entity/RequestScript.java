package com.codeops.courier.entity;

import com.codeops.courier.entity.enums.ScriptType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "request_scripts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "script_type"}),
        indexes = {
                @Index(name = "idx_request_scripts_request_id", columnList = "request_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestScript extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "script_type", nullable = false)
    private ScriptType scriptType;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;
}
