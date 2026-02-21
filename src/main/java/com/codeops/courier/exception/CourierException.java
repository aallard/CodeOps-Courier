package com.codeops.courier.exception;

/**
 * Base exception for all CodeOps-Courier service exceptions.
 * Maps to HTTP 500 Internal Server Error when not caught by a more specific handler.
 */
public class CourierException extends RuntimeException {

    /**
     * Creates a new CourierException with the specified message.
     *
     * @param message the detail message
     */
    public CourierException(String message) {
        super(message);
    }

    /**
     * Creates a new CourierException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the root cause
     */
    public CourierException(String message, Throwable cause) {
        super(message, cause);
    }
}
