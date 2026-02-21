package com.codeops.courier.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for LoggingInterceptor verifying request logging behavior.
 */
class LoggingInterceptorTest {

    private LoggingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new LoggingInterceptor();
        MDC.put("correlationId", "test-correlation-id");
    }

    @Test
    void preHandle_recordsStartTimeAndLogs() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courier/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(request.getAttribute(LoggingInterceptor.START_TIME_ATTR)).isNotNull();
    }

    @Test
    void afterCompletion_logsDuration() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courier/test");
        request.setAttribute(LoggingInterceptor.START_TIME_ATTR, System.currentTimeMillis() - 50);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // Should not throw
        interceptor.afterCompletion(request, response, new Object(), null);
    }
}
