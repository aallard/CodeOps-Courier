package com.codeops.courier.security;

import com.codeops.courier.exception.AuthorizationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Utility class providing static helper methods for accessing the current Spring Security
 * authentication context.
 *
 * <p>Methods in this class read from {@link SecurityContextHolder} and expect the principal
 * to be a {@link UUID} (as set by {@link JwtAuthFilter}) and authorities to follow the
 * {@code ROLE_} prefix convention.</p>
 *
 * @see JwtAuthFilter
 * @see SecurityConfig
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Retrieves the UUID of the currently authenticated user from the Spring Security context.
     *
     * @return the authenticated user's UUID
     * @throws AuthorizationException if no authentication is present or the principal is not a UUID
     */
    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new AuthorizationException("No authenticated user");
        }
        if (!(auth.getPrincipal() instanceof UUID userId)) {
            throw new AuthorizationException("Invalid authentication principal");
        }
        return userId;
    }

    /**
     * Retrieves the email of the currently authenticated user from the credentials.
     *
     * @return the authenticated user's email
     * @throws AuthorizationException if no authentication is present
     */
    public static String getCurrentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getCredentials() == null) {
            throw new AuthorizationException("No authenticated user");
        }
        return auth.getCredentials().toString();
    }

    /**
     * Retrieves the roles of the currently authenticated user.
     *
     * @return the list of role names (without the {@code ROLE_} prefix)
     * @throws AuthorizationException if no authentication is present
     */
    public static List<String> getCurrentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AuthorizationException("No authenticated user");
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .toList();
    }

    /**
     * Checks whether the currently authenticated user has the specified role.
     *
     * @param role the role name to check (without the {@code ROLE_} prefix)
     * @return {@code true} if the current user has the specified role, {@code false} otherwise
     */
    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    /**
     * Checks whether the currently authenticated user has administrative privileges,
     * defined as having either the {@code ADMIN} or {@code OWNER} role.
     *
     * @return {@code true} if the current user has the ADMIN or OWNER role, {@code false} otherwise
     */
    public static boolean isAdmin() {
        return hasRole("ADMIN") || hasRole("OWNER");
    }
}
