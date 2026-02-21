package com.codeops.courier.exception;

/**
 * Thrown when a request fails business validation rules.
 * Maps to HTTP 400 Bad Request.
 */
public class ValidationException extends CourierException {

    /**
     * Creates a new ValidationException with the specified message.
     *
     * @param message the detail message
     */
    public ValidationException(String message) {
        super(message);
    }
}
