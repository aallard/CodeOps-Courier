package com.codeops.courier.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AppConstants verifying accessibility and non-null values.
 */
class AppConstantsTest {

    @Test
    void constants_areAccessibleAndNonNull() {
        assertThat(AppConstants.API_PREFIX).isEqualTo("/api/v1/courier");
        assertThat(AppConstants.SERVICE_NAME).isEqualTo("codeops-courier");
        assertThat(AppConstants.DEFAULT_PAGE_SIZE).isEqualTo(20);
        assertThat(AppConstants.MAX_PAGE_SIZE).isEqualTo(100);
        assertThat(AppConstants.RATE_LIMIT_REQUESTS).isEqualTo(100);
        assertThat(AppConstants.RATE_LIMIT_WINDOW_SECONDS).isEqualTo(60);
    }
}
