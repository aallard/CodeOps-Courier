package com.codeops.courier.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Servlet filter that authenticates incoming HTTP requests by extracting and validating
 * JWT tokens from the {@code Authorization} header.
 *
 * <p>When a valid Bearer token is present, this filter extracts the user ID, email, and
 * roles from the token claims and populates the Spring {@link SecurityContextHolder} with a
 * {@link UsernamePasswordAuthenticationToken}. The principal is set to the user's {@link UUID},
 * and roles are mapped to {@link SimpleGrantedAuthority} instances with a {@code ROLE_} prefix.</p>
 *
 * <p>Requests without an {@code Authorization} header or with an invalid token are allowed
 * to proceed through the filter chain unauthenticated, leaving authorization decisions to
 * downstream security configuration.</p>
 *
 * @see JwtTokenValidator
 * @see SecurityConfig
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtTokenValidator jwtTokenValidator;

    /**
     * Extracts the JWT token from the {@code Authorization} header, validates it, and sets
     * the Spring Security authentication context if valid.
     *
     * @param request     the incoming HTTP request
     * @param response    the HTTP response
     * @param filterChain the filter chain to pass the request/response to
     * @throws ServletException if a servlet error occurs during filtering
     * @throws IOException      if an I/O error occurs during filtering
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        log.debug("Token extraction attempted for {}", request.getRequestURI());

        if (jwtTokenValidator.validateToken(token)) {
            UUID userId = jwtTokenValidator.getUserIdFromToken(token);
            String email = jwtTokenValidator.getEmailFromToken(token);
            List<String> roles = jwtTokenValidator.getRolesFromToken(token);

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, email, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            MDC.put("userId", userId.toString());
            log.debug("SecurityContext set for userId={} roles={}", userId, roles);
        } else {
            log.warn("Invalid JWT token from IP: {} for path: {}",
                    request.getRemoteAddr(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
