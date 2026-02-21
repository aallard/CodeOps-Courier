package com.codeops.courier.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Tests for RequestCorrelationFilter covering UUID generation and propagation.
 */
@ExtendWith(MockitoExtension.class)
class RequestCorrelationFilterTest {

    @Mock
    private FilterChain filterChain;

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void generatesUuidWhenNoHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(inv -> {
            assertThat(MDC.get("correlationId")).isNotNull();
            assertThat(MDC.get("correlationId")).matches(
                    "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("X-Correlation-ID")).isNotNull();
    }

    @Test
    void propagatesExistingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "existing-correlation-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(inv -> {
            assertThat(MDC.get("correlationId")).isEqualTo("existing-correlation-id");
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("existing-correlation-id");
    }

    @Test
    void addsMdcContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courier/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(inv -> {
            assertThat(MDC.get("requestPath")).isEqualTo("/api/v1/courier/health");
            assertThat(MDC.get("requestMethod")).isEqualTo("GET");
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);
    }
}
