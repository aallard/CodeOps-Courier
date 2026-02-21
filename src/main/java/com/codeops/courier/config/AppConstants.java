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
}
