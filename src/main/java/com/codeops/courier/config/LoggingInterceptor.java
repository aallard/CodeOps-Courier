package com.codeops.courier.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC interceptor that logs every HTTP request and response.
 *
 * <p>On {@code preHandle}, records the start time as a request attribute and
 * enriches MDC with the authenticated user's ID (if available). On
 * {@code afterCompletion}, logs the response status and elapsed duration.</p>
 *
 * <p>Log levels by response status:</p>
 * <ul>
 *   <li>5xx — ERROR</li>
 *   <li>4xx — WARN</li>
 *   <li>All others — INFO</li>
 * </ul>
 */
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    /** Request attribute key for the request start timestamp. */
    static final String START_TIME_ATTR = "codeops.requestStartTime";

    /**
     * Logs the incoming request and enriches MDC with user and team context.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response
     * @param handler  the handler being invoked
     * @return {@code true} to continue processing the request
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            MDC.put("userId", auth.getPrincipal().toString());
        }

        String teamId = request.getHeader("X-Team-ID");
        if (teamId != null && !teamId.isBlank()) {
            MDC.put("teamId", teamId);
        }

        String correlationId = MDC.get(RequestCorrelationFilter.MDC_CORRELATION_ID);
        log.info("-> {} {} (correlationId={})", request.getMethod(), request.getRequestURI(), correlationId);

        return true;
    }

    /**
     * Logs the response status and duration after request completion.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response
     * @param handler  the handler that was invoked
     * @param ex       any exception thrown during processing, or {@code null}
     */
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;
        int status = response.getStatus();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if (status >= 500) {
            log.error("<- {} {} status={} duration={}ms", method, uri, status, duration);
        } else if (status >= 400) {
            log.warn("<- {} {} status={} duration={}ms", method, uri, status, duration);
        } else {
            log.info("<- {} {} status={} duration={}ms", method, uri, status, duration);
        }
    }
}
