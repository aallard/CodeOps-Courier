package com.codeops.courier.service;

import com.codeops.courier.dto.request.SaveRequestAuthRequest;
import com.codeops.courier.entity.enums.AuthType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuthResolverService covering all auth types,
 * variable resolution in auth values, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class AuthResolverServiceTest {

    @Mock
    private VariableService variableService;

    @InjectMocks
    private AuthResolverService authResolverService;

    private UUID teamId;
    private UUID collectionId;
    private UUID environmentId;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        collectionId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
    }

    @Test
    void resolveAuth_noAuth_returnsEmpty() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.NO_AUTH, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        assertThat(result.headers()).isEmpty();
        assertThat(result.queryParams()).isEmpty();
    }

    @Test
    void resolveAuth_bearerToken_addsHeader() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.BEARER_TOKEN, null, null, null, "my-token", null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        when(variableService.resolveVariables(eq("my-token"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("my-token");

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        assertThat(result.headers()).containsEntry("Authorization", "Bearer my-token");
        assertThat(result.queryParams()).isEmpty();
    }

    @Test
    void resolveAuth_basicAuth_encodesBase64() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.BASIC_AUTH, null, null, null, null, "user", "pass",
                null, null, null, null, null, null, null, null, null, null, null);

        when(variableService.resolveVariables(eq("user"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("user");
        when(variableService.resolveVariables(eq("pass"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("pass");

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        String expected = Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
        assertThat(result.headers()).containsEntry("Authorization", "Basic " + expected);
    }

    @Test
    void resolveAuth_apiKey_inHeader() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.API_KEY, "X-API-Key", "secret123", "header", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        when(variableService.resolveVariables(eq("X-API-Key"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("X-API-Key");
        when(variableService.resolveVariables(eq("secret123"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("secret123");

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        assertThat(result.headers()).containsEntry("X-API-Key", "secret123");
        assertThat(result.queryParams()).isEmpty();
    }

    @Test
    void resolveAuth_apiKey_inQuery() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.API_KEY, "api_key", "key456", "query", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        when(variableService.resolveVariables(eq("api_key"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("api_key");
        when(variableService.resolveVariables(eq("key456"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("key456");

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        assertThat(result.queryParams()).containsEntry("api_key", "key456");
        assertThat(result.headers()).isEmpty();
    }

    @Test
    void resolveAuth_oauth2_usesAccessToken() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.OAUTH2_CLIENT_CREDENTIALS, null, null, null, null, null, null,
                "client_credentials", null, null, null, null, null, null, "access-token-123",
                null, null, null);

        when(variableService.resolveVariables(eq("access-token-123"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("access-token-123");

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        assertThat(result.headers()).containsEntry("Authorization", "Bearer access-token-123");
    }

    @Test
    void resolveAuth_jwtBearer_addsHeader() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.JWT_BEARER, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                "secret", "jwt-payload-token", "HS256");

        when(variableService.resolveVariables(eq("jwt-payload-token"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("jwt-payload-token");

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        assertThat(result.headers()).containsEntry("Authorization", "Bearer jwt-payload-token");
    }

    @Test
    void resolveAuth_inheritFromParent_returnsEmpty() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.INHERIT_FROM_PARENT, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        assertThat(result.headers()).isEmpty();
        assertThat(result.queryParams()).isEmpty();
    }

    @Test
    void resolveAuth_resolvesVariablesInToken() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.BEARER_TOKEN, null, null, null, "{{authToken}}", null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        when(variableService.resolveVariables(eq("{{authToken}}"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("resolved-token-value");

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        assertThat(result.headers()).containsEntry("Authorization", "Bearer resolved-token-value");
    }

    @Test
    void resolveAuth_resolvesVariablesInUsername() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.BASIC_AUTH, null, null, null, null, "{{username}}", "{{password}}",
                null, null, null, null, null, null, null, null, null, null, null);

        when(variableService.resolveVariables(eq("{{username}}"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("admin");
        when(variableService.resolveVariables(eq("{{password}}"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("secret");

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        String expected = Base64.getEncoder().encodeToString("admin:secret".getBytes(StandardCharsets.UTF_8));
        assertThat(result.headers()).containsEntry("Authorization", "Basic " + expected);
    }

    @Test
    void resolveAuth_resolvesVariablesInApiKey() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.API_KEY, "{{keyName}}", "{{keyValue}}", "header", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        when(variableService.resolveVariables(eq("{{keyName}}"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("X-Custom-Key");
        when(variableService.resolveVariables(eq("{{keyValue}}"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("resolved-key-value");

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        assertThat(result.headers()).containsEntry("X-Custom-Key", "resolved-key-value");
    }

    @Test
    void resolveAuth_nullAuth_returnsEmpty() {
        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                null, teamId, collectionId, environmentId, null);

        assertThat(result.headers()).isEmpty();
        assertThat(result.queryParams()).isEmpty();
    }

    @Test
    void resolvedAuth_empty_hasNoEntries() {
        AuthResolverService.ResolvedAuth empty = AuthResolverService.ResolvedAuth.empty();

        assertThat(empty.headers()).isEmpty();
        assertThat(empty.queryParams()).isEmpty();
    }

    @Test
    void resolveAuth_basicAuth_correctEncoding() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.BASIC_AUTH, null, null, null, null, "user@domain.com", "p@ss:word!",
                null, null, null, null, null, null, null, null, null, null, null);

        when(variableService.resolveVariables(eq("user@domain.com"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("user@domain.com");
        when(variableService.resolveVariables(eq("p@ss:word!"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("p@ss:word!");

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        String expected = Base64.getEncoder().encodeToString("user@domain.com:p@ss:word!".getBytes(StandardCharsets.UTF_8));
        assertThat(result.headers().get("Authorization")).isEqualTo("Basic " + expected);
    }

    @Test
    void resolveAuth_apiKeyAddToDefault_isHeader() {
        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.API_KEY, "X-API-Key", "key123", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        when(variableService.resolveVariables(eq("X-API-Key"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("X-API-Key");
        when(variableService.resolveVariables(eq("key123"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("key123");

        AuthResolverService.ResolvedAuth result = authResolverService.resolveAuth(
                auth, teamId, collectionId, environmentId, null);

        assertThat(result.headers()).containsEntry("X-API-Key", "key123");
        assertThat(result.queryParams()).isEmpty();
    }
}
