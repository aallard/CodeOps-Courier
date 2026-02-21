package com.codeops.courier.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "request_params",
        indexes = {
                @Index(name = "idx_request_params_request_id", columnList = "request_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestParam extends BaseEntity {

    @Column(name = "param_key", nullable = false, length = 500)
    private String paramKey;

    @Column(name = "param_value", length = 5000)
    private String paramValue;

    @Column(length = 500)
    private String description;

    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;
}
