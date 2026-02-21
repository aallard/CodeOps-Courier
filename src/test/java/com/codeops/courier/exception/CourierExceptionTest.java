package com.codeops.courier.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for custom exception hierarchy message propagation.
 */
class CourierExceptionTest {

    @Test
    void courierException_propagatesMessage() {
        CourierException ex = new CourierException("test error");
        assertThat(ex.getMessage()).isEqualTo("test error");
    }

    @Test
    void notFoundException_propagatesMessage() {
        NotFoundException ex = new NotFoundException("not found");
        assertThat(ex.getMessage()).isEqualTo("not found");
        assertThat(ex).isInstanceOf(CourierException.class);
    }

    @Test
    void validationException_propagatesMessage() {
        ValidationException ex = new ValidationException("invalid");
        assertThat(ex.getMessage()).isEqualTo("invalid");
        assertThat(ex).isInstanceOf(CourierException.class);
    }

    @Test
    void authorizationException_propagatesMessage() {
        AuthorizationException ex = new AuthorizationException("forbidden");
        assertThat(ex.getMessage()).isEqualTo("forbidden");
        assertThat(ex).isInstanceOf(CourierException.class);
    }
}
