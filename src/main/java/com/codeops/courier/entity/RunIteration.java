package com.codeops.courier.entity;

import com.codeops.courier.entity.enums.HttpMethod;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "run_iterations",
        indexes = {
                @Index(name = "idx_run_iterations_run_result_id", columnList = "run_result_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunIteration extends BaseEntity {

    @Column(name = "iteration_number", nullable = false)
    private int iterationNumber;

    @Column(name = "request_name", nullable = false, length = 200)
    private String requestName;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_method", nullable = false)
    private HttpMethod requestMethod;

    @Column(name = "request_url", nullable = false, length = 2000)
    private String requestUrl;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "response_size_bytes")
    private Long responseSizeBytes;

    @Builder.Default
    @Column(nullable = false)
    private boolean passed = true;

    @Column(name = "assertion_results", columnDefinition = "TEXT")
    private String assertionResults;

    @Column(name = "error_message", length = 5000)
    private String errorMessage;

    @Column(name = "request_data", columnDefinition = "TEXT")
    private String requestData;

    @Column(name = "response_data", columnDefinition = "TEXT")
    private String responseData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_result_id", nullable = false)
    private RunResult runResult;
}
