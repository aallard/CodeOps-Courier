package com.codeops.courier.exception;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GlobalExceptionHandler covering all 15 exception types.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFound_returns404() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new NotFoundException("Item not found"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Item not found");
    }

    @Test
    void handleValidation_returns400() {
        ResponseEntity<ErrorResponse> response = handler.handleValidation(new ValidationException("Invalid name"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Invalid name");
    }

    @Test
    void handleAuthorization_returns403() {
        ResponseEntity<ErrorResponse> response = handler.handleAuthorization(new AuthorizationException("Forbidden"));
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().message()).isEqualTo("Forbidden");
    }

    @Test
    void handleEntityNotFound_returns404() {
        ResponseEntity<ErrorResponse> response = handler.handleEntityNotFound(new EntityNotFoundException("entity"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Resource not found");
    }

    @Test
    void handleIllegalArgument_returns400() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(new IllegalArgumentException("bad"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Invalid request");
    }

    @Test
    void handleAccessDenied_returns403() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(new AccessDeniedException("denied"));
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().message()).isEqualTo("Access denied");
    }

    @Test
    void handleMethodArgumentNotValid_returns400WithFieldErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "name", "must not be blank"));
        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).contains("name: must not be blank");
    }

    @Test
    void handleMessageNotReadable_returns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "bad json", new MockHttpInputMessage(new byte[0]));
        ResponseEntity<ErrorResponse> response = handler.handleMessageNotReadable(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Malformed request body");
    }

    @Test
    void handleNoResourceFound_returns404() throws NoResourceFoundException {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/unknown");
        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Resource not found");
    }

    @Test
    void handleMissingParameter_returns400() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("page", "int");
        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).contains("page");
    }

    @Test
    void handleTypeMismatch_returns400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "id", null, new NumberFormatException());
        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).contains("id");
    }

    @Test
    void handleMissingHeader_returns400() {
        MissingRequestHeaderException ex;
        try {
            throw new MissingRequestHeaderException("X-Team-ID", null);
        } catch (MissingRequestHeaderException e) {
            ex = e;
        }
        ResponseEntity<ErrorResponse> response = handler.handleMissingHeader(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).contains("X-Team-ID");
    }

    @Test
    void handleMethodNotSupported_returns405() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("PATCH");
        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().message()).contains("PATCH");
    }

    @Test
    void handleCourier_returns500() {
        ResponseEntity<ErrorResponse> response = handler.handleCourier(new CourierException("internal"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("An internal error occurred");
    }

    @Test
    void handleGeneral_returns500() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneral(new RuntimeException("oops"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("An internal error occurred");
    }
}
