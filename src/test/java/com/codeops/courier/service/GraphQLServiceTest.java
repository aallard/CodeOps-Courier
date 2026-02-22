package com.codeops.courier.service;

import com.codeops.courier.dto.request.ExecuteGraphQLRequest;
import com.codeops.courier.dto.request.IntrospectGraphQLRequest;
import com.codeops.courier.dto.request.SaveRequestAuthRequest;
import com.codeops.courier.dto.request.SaveRequestBodyRequest;
import com.codeops.courier.dto.request.SaveRequestHeadersRequest;
import com.codeops.courier.dto.request.SendRequestProxyRequest;
import com.codeops.courier.dto.response.GraphQLResponse;
import com.codeops.courier.dto.response.ProxyResponse;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GraphQLService covering query execution, introspection,
 * variable resolution, validation, and formatting.
 */
@ExtendWith(MockitoExtension.class)
class GraphQLServiceTest {

    @Mock
    private RequestProxyService requestProxyService;

    @Mock
    private VariableService variableService;

    @InjectMocks
    private GraphQLService graphQLService;

    private UUID teamId;
    private UUID userId;
    private UUID environmentId;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        teamId = UUID.randomUUID();
        userId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        objectMapper = new ObjectMapper();

        // Inject real ObjectMapper via reflection
        var field = GraphQLService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(graphQLService, objectMapper);
    }

    private ProxyResponse successResponse(String body) {
        return new ProxyResponse(200, "OK",
                Map.of("Content-Type", List.of("application/json")),
                body, 50, body != null ? body.length() : 0,
                "application/json", List.of(), UUID.randomUUID());
    }

    private ProxyResponse errorResponse() {
        return new ProxyResponse(500, "Internal Server Error",
                Map.of(), "{\"errors\":[{\"message\":\"Server error\"}]}",
                100, 0, "application/json", List.of(), null);
    }

    private void setupVariablePassthrough() {
        when(variableService.resolveVariables(any(String.class), any(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── executeQuery tests ───

    @Test
    void executeQuery_simpleQuery_success() throws Exception {
        setupVariablePassthrough();
        String responseBody = "{\"data\":{\"users\":[{\"name\":\"Alice\"}]}}";
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse(responseBody));

        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql",
                "{ users { name } }",
                null, null, null, null, environmentId);

        GraphQLResponse result = graphQLService.executeQuery(request, teamId, userId);

        assertThat(result.httpResponse().statusCode()).isEqualTo(200);
        assertThat(result.httpResponse().responseBody()).isEqualTo(responseBody);
        assertThat(result.schema()).isNull();
    }

    @Test
    void executeQuery_withVariables_success() throws Exception {
        setupVariablePassthrough();
        String responseBody = "{\"data\":{\"user\":{\"name\":\"Bob\"}}}";
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse(responseBody));

        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql",
                "query GetUser($id: ID!) { user(id: $id) { name } }",
                "{\"id\": \"123\"}",
                null, null, null, environmentId);

        GraphQLResponse result = graphQLService.executeQuery(request, teamId, userId);

        assertThat(result.httpResponse().statusCode()).isEqualTo(200);

        // Verify the payload includes parsed variables as JSON object
        ArgumentCaptor<SendRequestProxyRequest> captor = ArgumentCaptor.forClass(SendRequestProxyRequest.class);
        verify(requestProxyService).executeRequest(captor.capture(), eq(teamId), eq(userId));
        String rawContent = captor.getValue().body().rawContent();
        JsonNode payload = objectMapper.readTree(rawContent);
        assertThat(payload.has("variables")).isTrue();
        assertThat(payload.get("variables").get("id").asText()).isEqualTo("123");
    }

    @Test
    void executeQuery_withOperationName_success() throws Exception {
        setupVariablePassthrough();
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse("{\"data\":{}}"));

        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql",
                "query GetUsers { users { name } }",
                null, "GetUsers", null, null, environmentId);

        graphQLService.executeQuery(request, teamId, userId);

        ArgumentCaptor<SendRequestProxyRequest> captor = ArgumentCaptor.forClass(SendRequestProxyRequest.class);
        verify(requestProxyService).executeRequest(captor.capture(), eq(teamId), eq(userId));
        String rawContent = captor.getValue().body().rawContent();
        JsonNode payload = objectMapper.readTree(rawContent);
        assertThat(payload.get("operationName").asText()).isEqualTo("GetUsers");
    }

    @Test
    void executeQuery_resolvesTemplateVariablesInQuery() {
        when(variableService.resolveVariables(eq("{ {{queryField}} { name } }"), eq(teamId), any(), eq(environmentId), any()))
                .thenReturn("{ users { name } }");
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse("{\"data\":{}}"));

        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql",
                "{ {{queryField}} { name } }",
                null, null, null, null, environmentId);

        GraphQLResponse result = graphQLService.executeQuery(request, teamId, userId);

        assertThat(result.httpResponse().statusCode()).isEqualTo(200);
    }

    @Test
    void executeQuery_resolvesTemplateVariablesInVariablesJson() throws Exception {
        when(variableService.resolveVariables(eq("{ users { name } }"), eq(teamId), any(), eq(environmentId), any()))
                .thenReturn("{ users { name } }");
        when(variableService.resolveVariables(eq("{\"id\": \"{{userId}}\"}"), eq(teamId), any(), eq(environmentId), any()))
                .thenReturn("{\"id\": \"resolved-id\"}");
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse("{\"data\":{}}"));

        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql",
                "{ users { name } }",
                "{\"id\": \"{{userId}}\"}",
                null, null, null, environmentId);

        graphQLService.executeQuery(request, teamId, userId);

        ArgumentCaptor<SendRequestProxyRequest> captor = ArgumentCaptor.forClass(SendRequestProxyRequest.class);
        verify(requestProxyService).executeRequest(captor.capture(), eq(teamId), eq(userId));
        String rawContent = captor.getValue().body().rawContent();
        JsonNode payload = objectMapper.readTree(rawContent);
        assertThat(payload.get("variables").get("id").asText()).isEqualTo("resolved-id");
    }

    @Test
    void executeQuery_withAuth_passedThrough() {
        setupVariablePassthrough();
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse("{\"data\":{}}"));

        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.BEARER_TOKEN, null, null, null, "my-token", null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql",
                "{ users { name } }",
                null, null, null, auth, environmentId);

        graphQLService.executeQuery(request, teamId, userId);

        ArgumentCaptor<SendRequestProxyRequest> captor = ArgumentCaptor.forClass(SendRequestProxyRequest.class);
        verify(requestProxyService).executeRequest(captor.capture(), eq(teamId), eq(userId));
        assertThat(captor.getValue().auth()).isEqualTo(auth);
    }

    @Test
    void executeQuery_withCustomHeaders_merged() {
        setupVariablePassthrough();
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse("{\"data\":{}}"));

        List<SaveRequestHeadersRequest.RequestHeaderEntry> customHeaders = List.of(
                new SaveRequestHeadersRequest.RequestHeaderEntry("X-Custom", "value", null, true));

        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql",
                "{ users { name } }",
                null, null, customHeaders, null, environmentId);

        graphQLService.executeQuery(request, teamId, userId);

        ArgumentCaptor<SendRequestProxyRequest> captor = ArgumentCaptor.forClass(SendRequestProxyRequest.class);
        verify(requestProxyService).executeRequest(captor.capture(), eq(teamId), eq(userId));
        List<SaveRequestHeadersRequest.RequestHeaderEntry> sentHeaders = captor.getValue().headers();
        assertThat(sentHeaders).hasSize(2);
        assertThat(sentHeaders.get(0).headerKey()).isEqualTo("Content-Type");
        assertThat(sentHeaders.get(0).headerValue()).isEqualTo("application/json");
        assertThat(sentHeaders.get(1).headerKey()).isEqualTo("X-Custom");
    }

    @Test
    void executeQuery_nullVariables_sendsWithoutVariables() throws Exception {
        setupVariablePassthrough();
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse("{\"data\":{}}"));

        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql",
                "{ users { name } }",
                null, null, null, null, environmentId);

        graphQLService.executeQuery(request, teamId, userId);

        ArgumentCaptor<SendRequestProxyRequest> captor = ArgumentCaptor.forClass(SendRequestProxyRequest.class);
        verify(requestProxyService).executeRequest(captor.capture(), eq(teamId), eq(userId));
        String rawContent = captor.getValue().body().rawContent();
        JsonNode payload = objectMapper.readTree(rawContent);
        assertThat(payload.has("variables")).isFalse();
    }

    @Test
    void executeQuery_emptyQuery_throwsValidation() {
        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql",
                "   ",
                null, null, null, null, environmentId);

        assertThatThrownBy(() -> graphQLService.executeQuery(request, teamId, userId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void executeQuery_savesToHistory() {
        setupVariablePassthrough();
        UUID historyId = UUID.randomUUID();
        ProxyResponse response = new ProxyResponse(200, "OK",
                Map.of("Content-Type", List.of("application/json")),
                "{\"data\":{}}", 50, 12, "application/json", List.of(), historyId);
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(response);

        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql",
                "{ users { name } }",
                null, null, null, null, environmentId);

        GraphQLResponse result = graphQLService.executeQuery(request, teamId, userId);

        assertThat(result.httpResponse().historyId()).isEqualTo(historyId);

        ArgumentCaptor<SendRequestProxyRequest> captor = ArgumentCaptor.forClass(SendRequestProxyRequest.class);
        verify(requestProxyService).executeRequest(captor.capture(), eq(teamId), eq(userId));
        assertThat(captor.getValue().saveToHistory()).isTrue();
    }

    @Test
    void executeQuery_environmentId_passedForResolution() {
        setupVariablePassthrough();
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse("{\"data\":{}}"));

        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql",
                "{ users { name } }",
                null, null, null, null, environmentId);

        graphQLService.executeQuery(request, teamId, userId);

        ArgumentCaptor<SendRequestProxyRequest> captor = ArgumentCaptor.forClass(SendRequestProxyRequest.class);
        verify(requestProxyService).executeRequest(captor.capture(), eq(teamId), eq(userId));
        assertThat(captor.getValue().environmentId()).isEqualTo(environmentId);
    }

    @Test
    void executeQuery_buildsCorrectJsonPayload() throws Exception {
        setupVariablePassthrough();
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse("{\"data\":{}}"));

        ExecuteGraphQLRequest request = new ExecuteGraphQLRequest(
                "https://api.example.com/graphql",
                "query GetUser($id: ID!) { user(id: $id) { name email } }",
                "{\"id\": \"abc\"}",
                "GetUser",
                null, null, environmentId);

        graphQLService.executeQuery(request, teamId, userId);

        ArgumentCaptor<SendRequestProxyRequest> captor = ArgumentCaptor.forClass(SendRequestProxyRequest.class);
        verify(requestProxyService).executeRequest(captor.capture(), eq(teamId), eq(userId));

        SendRequestProxyRequest sent = captor.getValue();
        assertThat(sent.method()).isEqualTo(HttpMethod.POST);
        assertThat(sent.url()).isEqualTo("https://api.example.com/graphql");
        assertThat(sent.body().bodyType()).isEqualTo(BodyType.RAW_JSON);
        assertThat(sent.followRedirects()).isFalse();

        JsonNode payload = objectMapper.readTree(sent.body().rawContent());
        assertThat(payload.get("query").asText()).contains("GetUser");
        assertThat(payload.get("variables").get("id").asText()).isEqualTo("abc");
        assertThat(payload.get("operationName").asText()).isEqualTo("GetUser");
    }

    // ─── introspect tests ───

    @Test
    void introspect_success_returnsSchema() {
        setupVariablePassthrough();
        String schemaJson = "{\"queryType\":{\"name\":\"Query\"},\"types\":[]}";
        String responseBody = "{\"data\":{\"__schema\":" + schemaJson + "}}";
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse(responseBody));

        IntrospectGraphQLRequest request = new IntrospectGraphQLRequest(
                "https://api.example.com/graphql", null, null);

        GraphQLResponse result = graphQLService.introspect(request, teamId, userId);

        assertThat(result.httpResponse().statusCode()).isEqualTo(200);
        assertThat(result.schema()).isNotNull();
        JsonNode schemaNode;
        try {
            schemaNode = objectMapper.readTree(result.schema());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(schemaNode.has("queryType")).isTrue();
    }

    @Test
    void introspect_endpointError_returnsErrorResponse() {
        setupVariablePassthrough();
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(errorResponse());

        IntrospectGraphQLRequest request = new IntrospectGraphQLRequest(
                "https://api.example.com/graphql", null, null);

        GraphQLResponse result = graphQLService.introspect(request, teamId, userId);

        assertThat(result.httpResponse().statusCode()).isEqualTo(500);
        assertThat(result.schema()).isNull();
    }

    @Test
    void introspect_withAuth_passedThrough() {
        setupVariablePassthrough();
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse("{\"data\":{\"__schema\":{}}}"));

        SaveRequestAuthRequest auth = new SaveRequestAuthRequest(
                AuthType.BEARER_TOKEN, null, null, null, "introspect-token", null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        IntrospectGraphQLRequest request = new IntrospectGraphQLRequest(
                "https://api.example.com/graphql", null, auth);

        graphQLService.introspect(request, teamId, userId);

        ArgumentCaptor<SendRequestProxyRequest> captor = ArgumentCaptor.forClass(SendRequestProxyRequest.class);
        verify(requestProxyService).executeRequest(captor.capture(), eq(teamId), eq(userId));
        assertThat(captor.getValue().auth()).isEqualTo(auth);
    }

    @Test
    void introspect_withCustomHeaders_merged() {
        setupVariablePassthrough();
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse("{\"data\":{\"__schema\":{}}}"));

        List<SaveRequestHeadersRequest.RequestHeaderEntry> customHeaders = List.of(
                new SaveRequestHeadersRequest.RequestHeaderEntry("X-Api-Key", "key123", null, true));

        IntrospectGraphQLRequest request = new IntrospectGraphQLRequest(
                "https://api.example.com/graphql", customHeaders, null);

        graphQLService.introspect(request, teamId, userId);

        ArgumentCaptor<SendRequestProxyRequest> captor = ArgumentCaptor.forClass(SendRequestProxyRequest.class);
        verify(requestProxyService).executeRequest(captor.capture(), eq(teamId), eq(userId));
        List<SaveRequestHeadersRequest.RequestHeaderEntry> sentHeaders = captor.getValue().headers();
        assertThat(sentHeaders).hasSize(2);
        assertThat(sentHeaders.get(1).headerKey()).isEqualTo("X-Api-Key");
    }

    @Test
    void introspect_extractsSchemaFromResponse() {
        setupVariablePassthrough();
        String schema = "{\"queryType\":{\"name\":\"Query\"},\"mutationType\":{\"name\":\"Mutation\"}}";
        String responseBody = "{\"data\":{\"__schema\":" + schema + "}}";
        when(requestProxyService.executeRequest(any(SendRequestProxyRequest.class), eq(teamId), eq(userId)))
                .thenReturn(successResponse(responseBody));

        IntrospectGraphQLRequest request = new IntrospectGraphQLRequest(
                "https://api.example.com/graphql", null, null);

        GraphQLResponse result = graphQLService.introspect(request, teamId, userId);

        assertThat(result.schema()).isNotNull();
        try {
            JsonNode schemaNode = objectMapper.readTree(result.schema());
            assertThat(schemaNode.get("queryType").get("name").asText()).isEqualTo("Query");
            assertThat(schemaNode.get("mutationType").get("name").asText()).isEqualTo("Mutation");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ─── validateQuery tests ───

    @Test
    void validateQuery_validQuery_noErrors() {
        List<String> errors = graphQLService.validateQuery("query GetUsers { users { name email } }");

        assertThat(errors).isEmpty();
    }

    @Test
    void validateQuery_validMutation_noErrors() {
        List<String> errors = graphQLService.validateQuery("mutation CreateUser($input: CreateUserInput!) { createUser(input: $input) { id } }");

        assertThat(errors).isEmpty();
    }

    @Test
    void validateQuery_unbalancedBraces_returnsError() {
        List<String> errors = graphQLService.validateQuery("query { users { name }");

        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).containsIgnoringCase("unbalanced");
    }

    @Test
    void validateQuery_emptyString_returnsError() {
        List<String> errors = graphQLService.validateQuery("   ");

        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).containsIgnoringCase("empty");
    }

    @Test
    void validateQuery_nullString_returnsError() {
        List<String> errors = graphQLService.validateQuery(null);

        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).containsIgnoringCase("null");
    }

    // ─── formatQuery tests ───

    @Test
    void formatQuery_addsIndentation() {
        String result = graphQLService.formatQuery("{ users { name } }");

        assertThat(result).contains("  users");
        assertThat(result).contains("  ");
    }

    @Test
    void formatQuery_normalizesWhitespace() {
        String result = graphQLService.formatQuery("query   GetUsers  {   users  {  name   email  }  }");

        // Verify excessive inline whitespace between tokens is normalized
        assertThat(result).doesNotContain("query   ");
        assertThat(result).doesNotContain("name   ");
        assertThat(result).contains("query GetUsers");
    }

    @Test
    void formatQuery_handlesNestedFields() {
        String result = graphQLService.formatQuery("{ users { posts { title comments { text } } } }");

        assertThat(result).contains("users");
        assertThat(result).contains("posts");
        assertThat(result).contains("comments");
        // Verify nesting produces multi-line output
        assertThat(result.split("\n").length).isGreaterThan(3);
    }
}
