package com.codeops.courier.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for JWT token validation, bound to the
 * {@code codeops.jwt} prefix in application properties.
 *
 * <p>The Courier service only validates tokens — it never issues them.
 * The {@code secret} must match the signing secret used by CodeOps-Server.</p>
 *
 * @see com.codeops.courier.security.JwtTokenValidator
 */
@ConfigurationProperties(prefix = "codeops.jwt")
@Getter
@Setter
public class JwtProperties {
    private String secret;
}
