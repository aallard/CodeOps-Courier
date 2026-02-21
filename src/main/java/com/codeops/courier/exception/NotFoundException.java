package com.codeops.courier.exception;

/**
 * Thrown when a requested resource cannot be found.
 * Maps to HTTP 404 Not Found.
 */
public class NotFoundException extends CourierException {

    /**
     * Creates a new NotFoundException with the specified message.
     *
     * @param message the detail message
     */
    public NotFoundException(String message) {
        super(message);
    }
}
