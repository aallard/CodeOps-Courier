package com.codeops.courier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Base entity providing UUID primary key and audit timestamps for all Courier entities.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Sets createdAt and updatedAt to current time before initial persist.
     * Uses a single Instant.now() call to avoid nanosecond differences in tests.
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Updates updatedAt to current time before each update.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
