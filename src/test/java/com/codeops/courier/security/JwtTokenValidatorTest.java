package com.codeops.courier.security;

import com.codeops.courier.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for JwtTokenValidator covering token validation, claim extraction, and startup checks.
 */
class JwtTokenValidatorTest {

    private static final String SECRET = "test-secret-key-minimum-32-characters-long-for-hs256-testing";

    private JwtTokenValidator validator;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(SECRET);
        validator = new JwtTokenValidator(jwtProperties);
        validator.validateSecret();
    }

    @Test
    void validateToken_returnsTrueForValidToken() {
        String token = buildToken(UUID.randomUUID(), "test@example.com", List.of("ADMIN"),
                Date.from(Instant.now().plusSeconds(3600)));
        assertThat(validator.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() {
        String token = buildToken(UUID.randomUUID(), "test@example.com", List.of("ADMIN"),
                Date.from(Instant.now().minusSeconds(3600)));
        assertThat(validator.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForInvalidSignature() {
        JwtProperties wrongProps = new JwtProperties();
        wrongProps.setSecret("wrong-secret-key-minimum-32-characters-long-for-testing");
        SecretKey wrongKey = Keys.hmacShaKeyFor(wrongProps.getSecret().getBytes());

        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(wrongKey)
                .compact();

        assertThat(validator.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForMalformedToken() {
        assertThat(validator.validateToken("not.a.valid.jwt")).isFalse();
    }

    @Test
    void validateSecret_throwsForShortSecret() {
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret("short");
        JwtTokenValidator shortValidator = new JwtTokenValidator(shortProps);

        assertThatThrownBy(shortValidator::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 characters");
    }

    @Test
    void getUserIdFromToken_extractsCorrectUserId() {
        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, "test@example.com", List.of("ADMIN"),
                Date.from(Instant.now().plusSeconds(3600)));

        assertThat(validator.getUserIdFromToken(token)).isEqualTo(userId);
    }

    @Test
    void getEmailFromToken_extractsCorrectEmail() {
        String token = buildToken(UUID.randomUUID(), "user@example.com", List.of("ADMIN"),
                Date.from(Instant.now().plusSeconds(3600)));

        assertThat(validator.getEmailFromToken(token)).isEqualTo("user@example.com");
    }

    @Test
    void getRolesFromToken_extractsRoles() {
        String token = buildToken(UUID.randomUUID(), "test@example.com", List.of("ADMIN", "MEMBER"),
                Date.from(Instant.now().plusSeconds(3600)));

        assertThat(validator.getRolesFromToken(token)).containsExactly("ADMIN", "MEMBER");
    }

    private String buildToken(UUID userId, String email, List<String> roles, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
}
