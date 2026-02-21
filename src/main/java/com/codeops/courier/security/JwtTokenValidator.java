package com.codeops.courier.security;

import com.codeops.courier.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validates JWT tokens issued by CodeOps-Server.
 *
 * <p>This service only validates tokens — it never issues them. The shared HMAC secret
 * must match the signing secret used by CodeOps-Server's {@code JwtTokenProvider}.</p>
 *
 * <p>Extracts standard claims ({@code sub} for user ID, {@code email}) and optional
 * custom claims ({@code teamIds}, {@code teamRoles}) from validated tokens.</p>
 *
 * @see JwtAuthFilter
 * @see JwtProperties
 */
@Component
@RequiredArgsConstructor
public class JwtTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenValidator.class);

    private final JwtProperties jwtProperties;

    /**
     * Validates that the JWT secret is configured and meets the minimum length requirement
     * of 32 characters. Invoked automatically after dependency injection.
     *
     * @throws IllegalStateException if the secret is null, blank, or shorter than 32 characters
     */
    @PostConstruct
    public void validateSecret() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 characters. Set the JWT_SECRET environment variable.");
        }
    }

    /**
     * Validates a JWT token by verifying its HMAC signature and expiration.
     *
     * <p>All validation failures are logged at WARN level without exposing details to callers.</p>
     *
     * @param token the raw JWT string (without {@code "Bearer "} prefix)
     * @return {@code true} if the token is valid, {@code false} otherwise
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Extracts the user ID from the JWT token's subject claim.
     *
     * @param token the raw JWT string (without {@code "Bearer "} prefix)
     * @return the user's UUID parsed from the token subject
     */
    public UUID getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extracts the email address from the JWT token's {@code "email"} claim.
     *
     * @param token the raw JWT string (without {@code "Bearer "} prefix)
     * @return the email address stored in the token, or {@code null} if the claim is absent
     */
    public String getEmailFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * Extracts the list of role names from the JWT token's {@code "roles"} claim.
     *
     * @param token the raw JWT string (without {@code "Bearer "} prefix)
     * @return the list of role name strings, or an empty list if the claim is absent
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = parseClaims(token);
        List<String> roles = claims.get("roles", List.class);
        return roles != null ? roles : List.of();
    }

    /**
     * Extracts the list of team IDs from the JWT token's {@code "teamIds"} claim.
     *
     * @param token the raw JWT string (without {@code "Bearer "} prefix)
     * @return the list of team UUIDs, or an empty list if the claim is absent
     */
    @SuppressWarnings("unchecked")
    public List<UUID> getTeamIdsFromToken(String token) {
        Claims claims = parseClaims(token);
        List<String> teamIds = claims.get("teamIds", List.class);
        if (teamIds == null) {
            return List.of();
        }
        return teamIds.stream().map(UUID::fromString).collect(Collectors.toList());
    }

    /**
     * Extracts the team-to-role mapping from the JWT token's {@code "teamRoles"} claim.
     *
     * @param token the raw JWT string (without {@code "Bearer "} prefix)
     * @return a map of team UUID to role string, or an empty map if the claim is absent
     */
    @SuppressWarnings("unchecked")
    public Map<UUID, String> getTeamRolesFromToken(String token) {
        Claims claims = parseClaims(token);
        Map<String, String> teamRoles = claims.get("teamRoles", Map.class);
        if (teamRoles == null) {
            return Map.of();
        }
        return teamRoles.entrySet().stream()
                .collect(Collectors.toMap(e -> UUID.fromString(e.getKey()), Map.Entry::getValue));
    }

    /**
     * Parses and verifies a JWT token, returning the claims payload.
     *
     * @param token the raw JWT string (without {@code "Bearer "} prefix)
     * @return the parsed {@link Claims} from the token payload
     * @throws JwtException if the token is expired, malformed, or has an invalid signature
     */
    Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
    }
}
