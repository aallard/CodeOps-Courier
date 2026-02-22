package com.codeops.courier.config;

/**
 * Application-wide constants for the CodeOps-Courier service.
 * Centralizes pagination defaults, rate limiting parameters, and service metadata.
 */
public final class AppConstants {

    private AppConstants() {}

    /** Base path prefix for all Courier API endpoints. */
    public static final String API_PREFIX = "/api/v1/courier";

    /** Default number of items per page for paginated responses. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Maximum allowed page size to prevent excessive data retrieval. */
    public static final int MAX_PAGE_SIZE = 100;

    /** Maximum number of API requests allowed per rate-limiting window. */
    public static final int RATE_LIMIT_REQUESTS = 100;

    /** Duration of the rate-limiting window in seconds. */
    public static final int RATE_LIMIT_WINDOW_SECONDS = 60;

    /** Service name used in health checks and structured logging. */
    public static final String SERVICE_NAME = "codeops-courier";

    /** Default HTTP request timeout in milliseconds (30 seconds). */
    public static final int DEFAULT_TIMEOUT_MS = 30000;

    /** Maximum allowed HTTP request timeout in milliseconds (5 minutes). */
    public static final int MAX_TIMEOUT_MS = 300000;

    /** Minimum allowed HTTP request timeout in milliseconds (1 second). */
    public static final int MIN_TIMEOUT_MS = 1000;

    /** Maximum number of HTTP redirects to follow before stopping. */
    public static final int MAX_REDIRECT_COUNT = 10;

    /** Maximum response body size to capture in bytes (10 MB). */
    public static final int MAX_RESPONSE_BODY_SIZE = 10 * 1024 * 1024;

    /** Maximum response body size to store in history in bytes (1 MB). */
    public static final int HISTORY_BODY_TRUNCATE_SIZE = 1024 * 1024;

    /** User-Agent header value for outgoing proxy requests. */
    public static final String USER_AGENT = "CodeOps-Courier/1.0";
}
