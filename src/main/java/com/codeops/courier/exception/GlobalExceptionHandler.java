package com.codeops.courier.exception;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Centralized exception handler for all REST controllers in the CodeOps-Courier API.
 *
 * <p>Catches application-specific exceptions ({@link NotFoundException}, {@link ValidationException},
 * {@link AuthorizationException}, {@link CourierException}), Spring/JPA exceptions
 * ({@link EntityNotFoundException}, {@link AccessDeniedException}, {@link MethodArgumentNotValidException}),
 * and general uncaught exceptions. Each handler returns a structured {@link ErrorResponse} with the
 * appropriate HTTP status code.</p>
 *
 * <p>Internal error details are never exposed to clients.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles CodeOps-specific {@link NotFoundException} by returning a 404 response.
     *
     * @param ex the thrown exception
     * @return a 404 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(404).body(new ErrorResponse(404, ex.getMessage()));
    }

    /**
     * Handles CodeOps-specific {@link ValidationException} by returning a 400 response.
     *
     * @param ex the thrown exception
     * @return a 400 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        return ResponseEntity.status(400).body(new ErrorResponse(400, ex.getMessage()));
    }

    /**
     * Handles CodeOps-specific {@link AuthorizationException} by returning a 403 response.
     *
     * @param ex the thrown exception
     * @return a 403 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorization(AuthorizationException ex) {
        log.warn("Authorization denied: {}", ex.getMessage());
        return ResponseEntity.status(403).body(new ErrorResponse(403, ex.getMessage()));
    }

    /**
     * Handles JPA {@link EntityNotFoundException} by returning a 404 response.
     *
     * @param ex the thrown exception
     * @return a 404 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(404).body(new ErrorResponse(404, "Resource not found"));
    }

    /**
     * Handles {@link IllegalArgumentException} by returning a 400 response.
     *
     * @param ex the thrown exception
     * @return a 400 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(400).body(new ErrorResponse(400, "Invalid request"));
    }

    /**
     * Handles Spring Security {@link AccessDeniedException} by returning a 403 response.
     *
     * @param ex the thrown exception
     * @return a 403 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(403).body(new ErrorResponse(403, "Access denied"));
    }

    /**
     * Handles Jakarta Bean Validation failures by returning a 400 response with field error details.
     *
     * @param ex the thrown exception
     * @return a 400 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", msg);
        return ResponseEntity.status(400).body(new ErrorResponse(400, msg));
    }

    /**
     * Handles malformed JSON or type-mismatch errors in request bodies.
     *
     * @param ex the thrown exception
     * @return a 400 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.status(400).body(new ErrorResponse(400, "Malformed request body"));
    }

    /**
     * Handles requests for unmapped paths.
     *
     * @param ex the thrown exception
     * @return a 404 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No resource found: {}", ex.getMessage());
        return ResponseEntity.status(404).body(new ErrorResponse(404, "Resource not found"));
    }

    /**
     * Handles missing required request parameters.
     *
     * @param ex the thrown exception
     * @return a 400 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing required request parameter: {}", ex.getParameterName());
        return ResponseEntity.status(400).body(new ErrorResponse(400,
                "Missing required parameter: '" + ex.getParameterName()
                        + "' of type " + ex.getParameterType()));
    }

    /**
     * Handles type mismatch in request parameters (e.g., passing "abc" for a UUID parameter).
     *
     * @param ex the thrown exception
     * @return a 400 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        return ResponseEntity.status(400).body(new ErrorResponse(400,
                "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue()));
    }

    /**
     * Handles missing required request headers.
     *
     * @param ex the thrown exception
     * @return a 400 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required request header: {}", ex.getHeaderName());
        return ResponseEntity.status(400).body(new ErrorResponse(400,
                "Missing required header: '" + ex.getHeaderName() + "'"));
    }

    /**
     * Handles unsupported HTTP method for an endpoint.
     *
     * @param ex the thrown exception
     * @return a 405 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMethod());
        return ResponseEntity.status(405).body(new ErrorResponse(405,
                "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint"));
    }

    /**
     * Handles the base {@link CourierException} by returning a 500 response with a generic message.
     *
     * @param ex the thrown exception
     * @return a 500 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(CourierException.class)
    public ResponseEntity<ErrorResponse> handleCourier(CourierException ex) {
        log.error("Application exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(500).body(new ErrorResponse(500, "An internal error occurred"));
    }

    /**
     * Catch-all handler for any unhandled exceptions. Returns a 500 response with a generic message.
     *
     * @param ex the unhandled exception
     * @return a 500 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(500).body(new ErrorResponse(500, "An internal error occurred"));
    }
}
