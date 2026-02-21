package com.codeops.courier.entity;

import com.codeops.courier.entity.enums.HttpMethod;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "request_history",
        indexes = {
                @Index(name = "idx_request_history_team_id", columnList = "team_id"),
                @Index(name = "idx_request_history_user_id", columnList = "user_id"),
                @Index(name = "idx_request_history_request_method", columnList = "request_method"),
                @Index(name = "idx_request_history_created_at", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestHistory extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_method", nullable = false)
    private HttpMethod requestMethod;

    @Column(name = "request_url", nullable = false, length = 2000)
    private String requestUrl;

    @Column(name = "request_headers", columnDefinition = "TEXT")
    private String requestHeaders;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_headers", columnDefinition = "TEXT")
    private String responseHeaders;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "response_size_bytes")
    private Long responseSizeBytes;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @Column(name = "collection_id")
    private UUID collectionId;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "environment_id")
    private UUID environmentId;
}
