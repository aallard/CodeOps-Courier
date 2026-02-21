package com.codeops.courier.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for JwtAuthFilter covering token extraction, validation, and SecurityContext population.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtTokenValidator jwtTokenValidator;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void validToken_setsSecurityContext() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenValidator.validateToken(token)).thenReturn(true);
        when(jwtTokenValidator.getUserIdFromToken(token)).thenReturn(userId);
        when(jwtTokenValidator.getEmailFromToken(token)).thenReturn("test@example.com");
        when(jwtTokenValidator.getRolesFromToken(token)).thenReturn(List.of("ADMIN"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingToken_skipsAuthentication() throws Exception {
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidToken_skipsAuthentication() throws Exception {
        request.addHeader("Authorization", "Bearer invalid.token");
        when(jwtTokenValidator.validateToken("invalid.token")).thenReturn(false);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void expiredToken_skipsAuthentication() throws Exception {
        request.addHeader("Authorization", "Bearer expired.token");
        when(jwtTokenValidator.validateToken("expired.token")).thenReturn(false);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nonBearerHeader_skipsAuthentication() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
