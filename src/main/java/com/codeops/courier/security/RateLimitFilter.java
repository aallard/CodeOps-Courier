package com.codeops.courier.security;

import com.codeops.courier.config.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servlet filter that enforces per-IP rate limiting on Courier API endpoints
 * ({@code /api/v1/courier/**}) to prevent abuse.
 *
 * <p>Uses an in-memory sliding window strategy backed by a {@link ConcurrentHashMap}.
 * Each client IP is allowed a maximum of {@value AppConstants#RATE_LIMIT_REQUESTS} requests
 * per 60-second window. Requests exceeding the limit receive a
 * {@code 429 Too Many Requests} JSON response.</p>
 *
 * <p>Client IP is resolved from the {@code X-Forwarded-For} header (first entry) when
 * present, falling back to {@link HttpServletRequest#getRemoteAddr()} for direct connections.</p>
 *
 * @see SecurityConfig
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final long WINDOW_MS = AppConstants.RATE_LIMIT_WINDOW_SECONDS * 1000L;

    private final ConcurrentHashMap<String, RateWindow> buckets = new ConcurrentHashMap<>();

    /**
     * Applies rate limiting to courier endpoint requests and passes all other
     * requests through without restriction.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response
     * @param chain    the filter chain to pass the request/response to
     * @throws ServletException if a servlet error occurs during filtering
     * @throws IOException      if an I/O error occurs during filtering
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith(AppConstants.API_PREFIX + "/")) {
            String key = getClientIp(request);
            RateWindow window = buckets.compute(key, (k, existing) -> {
                long now = System.currentTimeMillis();
                if (existing == null || now - existing.windowStart > WINDOW_MS) {
                    return new RateWindow(now, new AtomicInteger(1));
                }
                existing.count.incrementAndGet();
                return existing;
            });
            if (window.count.get() > AppConstants.RATE_LIMIT_REQUESTS) {
                log.warn("Rate limit exceeded for IP={} endpoint={} count={}",
                        key, request.getRequestURI(), window.count.get());
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":429,\"message\":\"Rate limit exceeded. Try again later.\"}");
                return;
            }
            log.debug("Rate limit check passed for IP={} count={}", key, window.count.get());
        }
        chain.doFilter(request, response);
    }

    /**
     * Resolves the client IP address from headers or the remote address.
     *
     * @param request the HTTP request
     * @return the client IP address
     */
    String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    static class RateWindow {
        final long windowStart;
        final AtomicInteger count;

        RateWindow(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
