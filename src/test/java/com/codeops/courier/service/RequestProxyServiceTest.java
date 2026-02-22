package com.codeops.courier.service;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.SaveRequestAuthRequest;
import com.codeops.courier.dto.request.SaveRequestBodyRequest;
import com.codeops.courier.dto.request.SaveRequestHeadersRequest;
import com.codeops.courier.dto.request.SendRequestProxyRequest;
import com.codeops.courier.dto.response.ProxyResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.RequestAuth;
import com.codeops.courier.entity.RequestHeader;
import com.codeops.courier.entity.RequestHistory;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.repository.RequestAuthRepository;
import com.codeops.courier.repository.RequestHistoryRepository;
import com.codeops.courier.repository.RequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RequestProxyService covering HTTP execution,
 * variable resolution, auth handling, redirects, timeouts, and history saving.
 */
@ExtendWith(MockitoExtension.class)
class RequestProxyServiceTest {

    @Mock
    private HttpClient courierHttpClient;

    @Mock
    private VariableService variableService;

    @Mock
    private AuthResolverService authResolverService;

    @Mock
    private RequestHistoryRepository requestHistoryRepository;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private RequestAuthRepository requestAuthRepository;

    @InjectMocks
    private RequestProxyService requestProxyService;

    private UUID teamId;
    private UUID userId;
    private UUID collectionId;
    private UUID environmentId;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        teamId = UUID.randomUUID();
        userId = UUID.randomUUID();
        collectionId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        objectMapper = new ObjectMapper();

        // Inject real ObjectMapper via reflection since it's not mockable easily
        var field = RequestProxyService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(requestProxyService, objectMapper);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockHttpResponse(int statusCode, String body, Map<String, List<String>> headers) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        lenient().when(response.body()).thenReturn(body);

        HttpHeaders httpHeaders = HttpHeaders.of(headers, (a, b) -> true);
        when(response.headers()).thenReturn(httpHeaders);
        return response;
    }

    private void setupUrlPassthrough() {
        when(variableService.resolveUrl(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void setupFullPassthrough() {
        setupUrlPassthrough();
        when(variableService.resolveVariables(any(String.class), any(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private SendRequestProxyRequest simpleGetRequest(String url, boolean saveToHistory) {
        return new SendRequestProxyRequest(
                HttpMethod.GET, url, null, null, null,
                environmentId, collectionId, saveToHistory, null, false);
    }

    // ─── executeRequest tests ───

    @Test
    void executeRequest_getSuccess() throws Exception {
        setupUrlPassthrough();
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        HttpResponse<String> httpResponse = mockHttpResponse(200, "{\"status\":\"ok\"}",
                Map.of("Content-Type", List.of("application/json")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        ProxyResponse result = requestProxyService.executeRequest(
                simpleGetRequest("https://api.example.com/health", false), teamId, userId);

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.statusText()).isEqualTo("OK");
        assertThat(result.responseBody()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(result.contentType()).isEqualTo("application/json");
    }

    @Test
    void executeRequest_postWithJsonBody() throws Exception {
        setupFullPassthrough();
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        SaveRequestBodyRequest body = new SaveRequestBodyRequest(
                BodyType.RAW_JSON, "{\"name\":\"test\"}", null, null, null, null);

        SendRequestProxyRequest proxyReq = new SendRequestProxyRequest(
                HttpMethod.POST, "https://api.example.com/users", null, body, null,
                environmentId, collectionId, false, null, false);

        HttpResponse<String> httpResponse = mockHttpResponse(201, "{\"id\":\"123\"}",
                Map.of("Content-Type", List.of("application/json")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        ProxyResponse result = requestProxyService.executeRequest(proxyReq, teamId, userId);

        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(result.statusText()).isEqualTo("Created");
    }

    @Test
    void executeRequest_postWithFormData() throws Exception {
        setupFullPassthrough();
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        SaveRequestBodyRequest body = new SaveRequestBodyRequest(
                BodyType.X_WWW_FORM_URLENCODED, null, "key1=val1&key2=val2", null, null, null);

        SendRequestProxyRequest proxyReq = new SendRequestProxyRequest(
                HttpMethod.POST, "https://api.example.com/form", null, body, null,
                environmentId, collectionId, false, null, false);

        HttpResponse<String> httpResponse = mockHttpResponse(200, "OK",
                Map.of("Content-Type", List.of("text/plain")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        ProxyResponse result = requestProxyService.executeRequest(proxyReq, teamId, userId);

        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    void executeRequest_resolvesVariablesInUrl() throws Exception {
        when(variableService.resolveUrl(eq("{{baseUrl}}/users"), eq(teamId), eq(collectionId), eq(environmentId), any()))
                .thenReturn("https://api.example.com/users");
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        HttpResponse<String> httpResponse = mockHttpResponse(200, "[]",
                Map.of("Content-Type", List.of("application/json")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        ProxyResponse result = requestProxyService.executeRequest(
                simpleGetRequest("{{baseUrl}}/users", false), teamId, userId);

        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    void executeRequest_resolvesVariablesInHeaders() throws Exception {
        setupFullPassthrough();
        when(variableService.resolveVariables(eq("{{token}}"), any(), any(), any(), any()))
                .thenReturn("resolved-token");
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        List<SaveRequestHeadersRequest.RequestHeaderEntry> headers = List.of(
                new SaveRequestHeadersRequest.RequestHeaderEntry("Authorization", "{{token}}", null, true));

        SendRequestProxyRequest proxyReq = new SendRequestProxyRequest(
                HttpMethod.GET, "https://api.example.com/data", headers, null, null,
                environmentId, collectionId, false, null, false);

        HttpResponse<String> httpResponse = mockHttpResponse(200, "data",
                Map.of("Content-Type", List.of("text/plain")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        ProxyResponse result = requestProxyService.executeRequest(proxyReq, teamId, userId);

        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    void executeRequest_resolvesVariablesInBody() throws Exception {
        setupUrlPassthrough();
        when(variableService.resolveVariables(eq("{\"user\":\"{{userId}}\"}"), any(), any(), any(), any()))
                .thenReturn("{\"user\":\"12345\"}");
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        SaveRequestBodyRequest body = new SaveRequestBodyRequest(
                BodyType.RAW_JSON, "{\"user\":\"{{userId}}\"}", null, null, null, null);

        SendRequestProxyRequest proxyReq = new SendRequestProxyRequest(
                HttpMethod.POST, "https://api.example.com/users", null, body, null,
                environmentId, collectionId, false, null, false);

        HttpResponse<String> httpResponse = mockHttpResponse(201, "{\"id\":1}",
                Map.of("Content-Type", List.of("application/json")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        ProxyResponse result = requestProxyService.executeRequest(proxyReq, teamId, userId);

        assertThat(result.statusCode()).isEqualTo(201);
    }

    @Test
    void executeRequest_bearerTokenAuth() throws Exception {
        setupUrlPassthrough();
        AuthResolverService.ResolvedAuth resolvedAuth = new AuthResolverService.ResolvedAuth(
                Map.of("Authorization", "Bearer my-token"), Map.of());
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any())).thenReturn(resolvedAuth);

        HttpResponse<String> httpResponse = mockHttpResponse(200, "ok",
                Map.of("Content-Type", List.of("text/plain")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.BEARER_TOKEN, null, null, null, "my-token", null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        SendRequestProxyRequest proxyReq = new SendRequestProxyRequest(
                HttpMethod.GET, "https://api.example.com/protected", null, null, auth,
                environmentId, collectionId, false, null, false);

        ProxyResponse result = requestProxyService.executeRequest(proxyReq, teamId, userId);

        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    void executeRequest_basicAuth() throws Exception {
        setupUrlPassthrough();
        AuthResolverService.ResolvedAuth resolvedAuth = new AuthResolverService.ResolvedAuth(
                Map.of("Authorization", "Basic dXNlcjpwYXNz"), Map.of());
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any())).thenReturn(resolvedAuth);

        HttpResponse<String> httpResponse = mockHttpResponse(200, "ok",
                Map.of("Content-Type", List.of("text/plain")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        ProxyResponse result = requestProxyService.executeRequest(
                simpleGetRequest("https://api.example.com/basic", false), teamId, userId);

        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    void executeRequest_apiKeyInHeader() throws Exception {
        setupUrlPassthrough();
        AuthResolverService.ResolvedAuth resolvedAuth = new AuthResolverService.ResolvedAuth(
                Map.of("X-API-Key", "key123"), Map.of());
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any())).thenReturn(resolvedAuth);

        HttpResponse<String> httpResponse = mockHttpResponse(200, "ok",
                Map.of("Content-Type", List.of("text/plain")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        ProxyResponse result = requestProxyService.executeRequest(
                simpleGetRequest("https://api.example.com/apikey", false), teamId, userId);

        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    void executeRequest_apiKeyInQuery() throws Exception {
        setupUrlPassthrough();
        AuthResolverService.ResolvedAuth resolvedAuth = new AuthResolverService.ResolvedAuth(
                Map.of(), Map.of("api_key", "key456"));
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any())).thenReturn(resolvedAuth);

        HttpResponse<String> httpResponse = mockHttpResponse(200, "ok",
                Map.of("Content-Type", List.of("text/plain")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        ProxyResponse result = requestProxyService.executeRequest(
                simpleGetRequest("https://api.example.com/data", false), teamId, userId);

        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    void executeRequest_followsRedirects() throws Exception {
        setupUrlPassthrough();
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        HttpResponse<String> redirectResponse = mockHttpResponse(302, "",
                Map.of("Location", List.of("https://api.example.com/new-location")));
        HttpResponse<String> finalResponse = mockHttpResponse(200, "final",
                Map.of("Content-Type", List.of("text/plain")));

        doReturn(redirectResponse).doReturn(finalResponse)
                .when(courierHttpClient).send(any(HttpRequest.class), any());

        SendRequestProxyRequest proxyReq = new SendRequestProxyRequest(
                HttpMethod.GET, "https://api.example.com/old", null, null, null,
                environmentId, collectionId, false, null, true);

        ProxyResponse result = requestProxyService.executeRequest(proxyReq, teamId, userId);

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.redirectChain()).containsExactly("https://api.example.com/new-location");
    }

    @Test
    void executeRequest_maxRedirectsExceeded() throws Exception {
        setupUrlPassthrough();
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        HttpResponse<String> redirectResponse = mockHttpResponse(302, "",
                Map.of("Location", List.of("https://api.example.com/loop")));

        doReturn(redirectResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        SendRequestProxyRequest proxyReq = new SendRequestProxyRequest(
                HttpMethod.GET, "https://api.example.com/loop", null, null, null,
                environmentId, collectionId, false, null, true);

        ProxyResponse result = requestProxyService.executeRequest(proxyReq, teamId, userId);

        assertThat(result.statusText()).isEqualTo("Max redirects exceeded");
        assertThat(result.redirectChain()).hasSize(AppConstants.MAX_REDIRECT_COUNT);
    }

    @Test
    void executeRequest_timeout() throws Exception {
        setupUrlPassthrough();
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        doThrow(new HttpTimeoutException("Read timed out"))
                .when(courierHttpClient).send(any(HttpRequest.class), any());

        ProxyResponse result = requestProxyService.executeRequest(
                simpleGetRequest("https://api.example.com/slow", false), teamId, userId);

        assertThat(result.statusCode()).isZero();
        assertThat(result.statusText()).contains("timed out");
    }

    @Test
    void executeRequest_savesToHistory() throws Exception {
        setupUrlPassthrough();
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        HttpResponse<String> httpResponse = mockHttpResponse(200, "response-body",
                Map.of("Content-Type", List.of("application/json")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        UUID historyId = UUID.randomUUID();
        when(requestHistoryRepository.save(any(RequestHistory.class))).thenAnswer(inv -> {
            RequestHistory h = inv.getArgument(0);
            h.setId(historyId);
            return h;
        });

        ProxyResponse result = requestProxyService.executeRequest(
                simpleGetRequest("https://api.example.com/data", true), teamId, userId);

        assertThat(result.historyId()).isEqualTo(historyId);
        verify(requestHistoryRepository).save(any(RequestHistory.class));
    }

    @Test
    void executeRequest_doesNotSaveWhenFlagFalse() throws Exception {
        setupUrlPassthrough();
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        HttpResponse<String> httpResponse = mockHttpResponse(200, "ok",
                Map.of("Content-Type", List.of("text/plain")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        ProxyResponse result = requestProxyService.executeRequest(
                simpleGetRequest("https://api.example.com/data", false), teamId, userId);

        assertThat(result.historyId()).isNull();
        verify(requestHistoryRepository, never()).save(any(RequestHistory.class));
    }

    @Test
    void executeRequest_truncatesLargeResponseBody() throws Exception {
        setupUrlPassthrough();
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        UUID historyId = UUID.randomUUID();
        when(requestHistoryRepository.save(any(RequestHistory.class))).thenAnswer(inv -> {
            RequestHistory h = inv.getArgument(0);
            h.setId(historyId);
            return h;
        });

        // Build large body > HISTORY_BODY_TRUNCATE_SIZE
        String largeBody = "x".repeat(AppConstants.HISTORY_BODY_TRUNCATE_SIZE + 1000);
        HttpResponse<String> httpResponse = mockHttpResponse(200, largeBody,
                Map.of("Content-Type", List.of("text/plain")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        requestProxyService.executeRequest(
                simpleGetRequest("https://api.example.com/large", true), teamId, userId);

        ArgumentCaptor<RequestHistory> captor = ArgumentCaptor.forClass(RequestHistory.class);
        verify(requestHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getResponseBody()).contains("[truncated]");
        assertThat(captor.getValue().getResponseBody().length())
                .isLessThan(AppConstants.HISTORY_BODY_TRUNCATE_SIZE + 50);
    }

    // ─── executeStoredRequest tests ───

    @Test
    void executeStoredRequest_success() throws Exception {
        setupUrlPassthrough();
        when(authResolverService.resolveAuth(any(), any(), any(), any(), any()))
                .thenReturn(AuthResolverService.ResolvedAuth.empty());

        Request request = buildStoredRequest();
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(requestAuthRepository.findByRequestId(request.getId())).thenReturn(Optional.empty());

        HttpResponse<String> httpResponse = mockHttpResponse(200, "ok",
                Map.of("Content-Type", List.of("text/plain")));
        doReturn(httpResponse).when(courierHttpClient).send(any(HttpRequest.class), any());

        UUID historyId = UUID.randomUUID();
        when(requestHistoryRepository.save(any(RequestHistory.class))).thenAnswer(inv -> {
            RequestHistory h = inv.getArgument(0);
            h.setId(historyId);
            return h;
        });
        when(requestHistoryRepository.findById(historyId)).thenAnswer(inv -> {
            RequestHistory h = new RequestHistory();
            h.setId(historyId);
            return Optional.of(h);
        });

        ProxyResponse result = requestProxyService.executeStoredRequest(
                request.getId(), teamId, userId, environmentId);

        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    void executeStoredRequest_inheritsAuthFromFolder() {
        Request request = buildStoredRequest();
        Folder folder = request.getFolder();
        folder.setAuthType(AuthType.BEARER_TOKEN);

        when(requestAuthRepository.findByRequestId(request.getId())).thenReturn(Optional.empty());

        SaveRequestAuthRequest resolved = requestProxyService.resolveInheritedAuth(request);

        assertThat(resolved.authType()).isEqualTo(AuthType.BEARER_TOKEN);
    }

    @Test
    void executeStoredRequest_inheritsAuthFromCollection() {
        Request request = buildStoredRequest();
        Collection collection = request.getFolder().getCollection();
        collection.setAuthType(AuthType.BASIC_AUTH);

        when(requestAuthRepository.findByRequestId(request.getId())).thenReturn(Optional.empty());

        SaveRequestAuthRequest resolved = requestProxyService.resolveInheritedAuth(request);

        assertThat(resolved.authType()).isEqualTo(AuthType.BASIC_AUTH);
    }

    @Test
    void executeStoredRequest_requestNotFound_throws() {
        UUID requestId = UUID.randomUUID();
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestProxyService.executeStoredRequest(requestId, teamId, userId, environmentId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(requestId.toString());
    }

    // ─── resolveInheritedAuth tests ───

    @Test
    void resolveInheritedAuth_fromRequest() {
        Request request = buildStoredRequest();
        RequestAuth reqAuth = new RequestAuth();
        reqAuth.setAuthType(AuthType.BEARER_TOKEN);
        reqAuth.setBearerToken("request-token");

        when(requestAuthRepository.findByRequestId(request.getId())).thenReturn(Optional.of(reqAuth));

        SaveRequestAuthRequest resolved = requestProxyService.resolveInheritedAuth(request);

        assertThat(resolved.authType()).isEqualTo(AuthType.BEARER_TOKEN);
        assertThat(resolved.bearerToken()).isEqualTo("request-token");
    }

    @Test
    void resolveInheritedAuth_fromFolder() {
        Request request = buildStoredRequest();
        request.getFolder().setAuthType(AuthType.API_KEY);

        when(requestAuthRepository.findByRequestId(request.getId())).thenReturn(Optional.empty());

        SaveRequestAuthRequest resolved = requestProxyService.resolveInheritedAuth(request);

        assertThat(resolved.authType()).isEqualTo(AuthType.API_KEY);
    }

    @Test
    void resolveInheritedAuth_fromParentFolder() {
        Request request = buildStoredRequest();
        Folder parentFolder = new Folder();
        parentFolder.setId(UUID.randomUUID());
        parentFolder.setAuthType(AuthType.OAUTH2_CLIENT_CREDENTIALS);
        parentFolder.setCollection(request.getFolder().getCollection());
        request.getFolder().setParentFolder(parentFolder);

        when(requestAuthRepository.findByRequestId(request.getId())).thenReturn(Optional.empty());

        SaveRequestAuthRequest resolved = requestProxyService.resolveInheritedAuth(request);

        assertThat(resolved.authType()).isEqualTo(AuthType.OAUTH2_CLIENT_CREDENTIALS);
    }

    @Test
    void resolveInheritedAuth_fromCollection() {
        Request request = buildStoredRequest();
        request.getFolder().getCollection().setAuthType(AuthType.JWT_BEARER);

        when(requestAuthRepository.findByRequestId(request.getId())).thenReturn(Optional.empty());

        SaveRequestAuthRequest resolved = requestProxyService.resolveInheritedAuth(request);

        assertThat(resolved.authType()).isEqualTo(AuthType.JWT_BEARER);
    }

    @Test
    void resolveInheritedAuth_noAuth_fallback() {
        Request request = buildStoredRequest();

        when(requestAuthRepository.findByRequestId(request.getId())).thenReturn(Optional.empty());

        SaveRequestAuthRequest resolved = requestProxyService.resolveInheritedAuth(request);

        assertThat(resolved.authType()).isEqualTo(AuthType.NO_AUTH);
    }

    // ─── getStatusText ───

    @Test
    void getStatusText_commonCodes() {
        assertThat(requestProxyService.getStatusText(200)).isEqualTo("OK");
        assertThat(requestProxyService.getStatusText(201)).isEqualTo("Created");
        assertThat(requestProxyService.getStatusText(204)).isEqualTo("No Content");
        assertThat(requestProxyService.getStatusText(301)).isEqualTo("Moved Permanently");
        assertThat(requestProxyService.getStatusText(302)).isEqualTo("Found");
        assertThat(requestProxyService.getStatusText(400)).isEqualTo("Bad Request");
        assertThat(requestProxyService.getStatusText(401)).isEqualTo("Unauthorized");
        assertThat(requestProxyService.getStatusText(403)).isEqualTo("Forbidden");
        assertThat(requestProxyService.getStatusText(404)).isEqualTo("Not Found");
        assertThat(requestProxyService.getStatusText(500)).isEqualTo("Internal Server Error");
        assertThat(requestProxyService.getStatusText(999)).isEqualTo("Unknown");
    }

    // ─── Helpers ───

    private Request buildStoredRequest() {
        UUID requestId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();

        Collection collection = new Collection();
        collection.setId(UUID.randomUUID());
        collection.setTeamId(teamId);
        collection.setName("Test Collection");
        collection.setCreatedBy(userId);
        collection.setCreatedAt(Instant.now());
        collection.setUpdatedAt(Instant.now());

        Folder folder = new Folder();
        folder.setId(folderId);
        folder.setName("Test Folder");
        folder.setCollection(collection);
        folder.setCreatedAt(Instant.now());
        folder.setUpdatedAt(Instant.now());

        Request request = new Request();
        request.setId(requestId);
        request.setName("Test Request");
        request.setMethod(HttpMethod.GET);
        request.setUrl("https://api.example.com/test");
        request.setSortOrder(0);
        request.setFolder(folder);
        request.setHeaders(new ArrayList<>());
        request.setParams(new ArrayList<>());
        request.setScripts(new ArrayList<>());
        request.setCreatedAt(Instant.now());
        request.setUpdatedAt(Instant.now());

        return request;
    }
}
