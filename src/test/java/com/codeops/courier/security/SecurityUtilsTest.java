package com.codeops.courier.security;

import com.codeops.courier.exception.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for SecurityUtils covering authenticated and unauthenticated access.
 */
class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserId_returnsUuidWhenAuthenticated() {
        UUID userId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "test@example.com",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(userId);
    }

    @Test
    void getCurrentUserId_throwsWhenUnauthenticated() {
        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("No authenticated user");
    }
}
