package com.codeops.courier.exception;

/**
 * Thrown when a user lacks the required permissions for an operation.
 * Maps to HTTP 403 Forbidden.
 */
public class AuthorizationException extends CourierException {

    /**
     * Creates a new AuthorizationException with the specified message.
     *
     * @param message the detail message
     */
    public AuthorizationException(String message) {
        super(message);
    }
}
