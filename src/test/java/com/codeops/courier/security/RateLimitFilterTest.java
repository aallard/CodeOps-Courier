package com.codeops.courier.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Tests for RateLimitFilter covering rate limiting behavior.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private RateLimitFilter rateLimitFilter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter();
    }

    @Test
    void underLimit_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courier/test");
        request.setRequestURI("/api/v1/courier/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void overLimit_returns429() throws Exception {
        for (int i = 0; i < 101; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courier/test");
            request.setRequestURI("/api/v1/courier/test");
            request.setRemoteAddr("192.168.1.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            if (i >= 100) {
                assertThat(response.getStatus()).isEqualTo(429);
            }
        }
    }

    @Test
    void differentIps_trackedSeparately() throws Exception {
        // Exhaust limit for IP 1
        for (int i = 0; i < 101; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courier/test");
            request.setRequestURI("/api/v1/courier/test");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        // IP 2 should still work
        MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/api/v1/courier/test");
        request2.setRequestURI("/api/v1/courier/test");
        request2.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        rateLimitFilter.doFilterInternal(request2, response2, filterChain);

        assertThat(response2.getStatus()).isNotEqualTo(429);
    }
}
