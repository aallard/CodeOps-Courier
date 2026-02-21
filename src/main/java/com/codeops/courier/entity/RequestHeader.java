package com.codeops.courier.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "request_headers",
        indexes = {
                @Index(name = "idx_request_headers_request_id", columnList = "request_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestHeader extends BaseEntity {

    @Column(name = "header_key", nullable = false, length = 500)
    private String headerKey;

    @Column(name = "header_value", length = 5000)
    private String headerValue;

    @Column(length = 500)
    private String description;

    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;
}
