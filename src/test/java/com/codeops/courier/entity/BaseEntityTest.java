package com.codeops.courier.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for BaseEntity lifecycle callbacks (PrePersist, PreUpdate).
 */
class BaseEntityTest {

    private static class TestEntity extends BaseEntity {}

    @Test
    void onCreate_setsCreatedAtAndUpdatedAt() {
        TestEntity entity = new TestEntity();
        entity.onCreate();

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getCreatedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void onCreate_setsCreatedAtAndUpdatedAtToSameValue() {
        TestEntity entity = new TestEntity();
        entity.onCreate();

        assertThat(entity.getCreatedAt()).isEqualTo(entity.getUpdatedAt());
    }

    @Test
    void onUpdate_changesUpdatedAt() throws InterruptedException {
        TestEntity entity = new TestEntity();
        entity.onCreate();
        Instant originalUpdatedAt = entity.getUpdatedAt();

        Thread.sleep(10);
        entity.onUpdate();

        assertThat(entity.getUpdatedAt()).isAfter(originalUpdatedAt);
        assertThat(entity.getCreatedAt()).isNotEqualTo(entity.getUpdatedAt());
    }
}
