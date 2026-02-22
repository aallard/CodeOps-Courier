package com.codeops.courier.service;

import com.codeops.courier.dto.request.ExecuteGraphQLRequest;
import com.codeops.courier.dto.request.IntrospectGraphQLRequest;
import com.codeops.courier.dto.request.SaveRequestBodyRequest;
import com.codeops.courier.dto.request.SaveRequestHeadersRequest;
import com.codeops.courier.dto.request.SendRequestProxyRequest;
import com.codeops.courier.dto.response.GraphQLResponse;
import com.codeops.courier.dto.response.ProxyResponse;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GraphQL service for schema introspection, query/mutation/subscription execution,
 * and variable injection for GraphQL endpoints. Wraps {@link RequestProxyService}
 * for GraphQL-specific concerns — all GraphQL operations are HTTP POST requests
 * with a JSON body containing query, variables, and operationName.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GraphQLService {

    private final RequestProxyService requestProxyService;
    private final VariableService variableService;
    private final ObjectMapper objectMapper;

    /**
     * Standard GraphQL introspection query that retrieves the full schema
     * including types, fields, arguments, directives, enums, and interfaces.
     */
    static final String INTROSPECTION_QUERY = """
            query IntrospectionQuery {
              __schema {
                queryType { name }
                mutationType { name }
                subscriptionType { name }
                types {
                  ...FullType
                }
                directives {
                  name
                  description
                  locations
                  args {
                    ...InputValue
                  }
                }
              }
            }

            fragment FullType on __Type {
              kind
              name
              description
              fields(includeDeprecated: true) {
                name
                description
                args {
                  ...InputValue
                }
                type {
                  ...TypeRef
                }
                isDeprecated
                deprecationReason
              }
              inputFields {
                ...InputValue
              }
              interfaces {
                ...TypeRef
              }
              enumValues(includeDeprecated: true) {
                name
                description
                isDeprecated
                deprecationReason
              }
              possibleTypes {
                ...TypeRef
              }
            }

            fragment InputValue on __InputValue {
              name
              description
              type { ...TypeRef }
              defaultValue
            }

            fragment TypeRef on __Type {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                  ofType {
                    kind
                    name
                    ofType {
                      kind
                      name
                      ofType {
                        kind
                        name
                        ofType {
                          kind
                          name
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

    /**
     * Executes a GraphQL query/mutation against a GraphQL endpoint.
     * Resolves {{variables}} in the query and variables JSON,
     * then sends as HTTP POST with Content-Type: application/json.
     *
     * @param request the GraphQL execution request containing url, query, variables, headers, and auth
     * @param teamId  team ID for variable resolution and history recording
     * @param userId  user ID for history recording
     * @return GraphQL response wrapping the HTTP response with null schema
     * @throws ValidationException if the query is null or blank
     */
    public GraphQLResponse executeQuery(ExecuteGraphQLRequest request, UUID teamId, UUID userId) {
        if (request.query() == null || request.query().isBlank()) {
            throw new ValidationException("GraphQL query must not be empty");
        }

        UUID environmentId = request.environmentId();

        // 1. Resolve {{variables}} in the query string
        String resolvedQuery = variableService.resolveVariables(
                request.query(), teamId, null, environmentId, null);

        // 2. Resolve {{variables}} in the variables JSON string
        String resolvedVariables = null;
        if (request.variables() != null && !request.variables().isBlank()) {
            resolvedVariables = variableService.resolveVariables(
                    request.variables(), teamId, null, environmentId, null);
        }

        // 3. Build GraphQL JSON payload
        String graphqlPayload = buildGraphQLPayload(resolvedQuery, resolvedVariables, request.operationName());

        // 4. Build body
        SaveRequestBodyRequest body = new SaveRequestBodyRequest(
                BodyType.RAW_JSON, graphqlPayload, null, null, null, null);

        // 5. Build headers: Content-Type: application/json + user-provided headers
        List<SaveRequestHeadersRequest.RequestHeaderEntry> mergedHeaders = new ArrayList<>();
        mergedHeaders.add(new SaveRequestHeadersRequest.RequestHeaderEntry(
                "Content-Type", "application/json", null, true));
        if (request.headers() != null) {
            mergedHeaders.addAll(request.headers());
        }

        // 6. Build SendRequestProxyRequest
        SendRequestProxyRequest proxyRequest = new SendRequestProxyRequest(
                HttpMethod.POST,
                request.url(),
                mergedHeaders,
                body,
                request.auth(),
                environmentId,
                null,
                true,
                null,
                false
        );

        // 7. Execute via requestProxyService
        ProxyResponse httpResponse = requestProxyService.executeRequest(proxyRequest, teamId, userId);

        log.info("GraphQL query executed against {} — status {}", request.url(), httpResponse.statusCode());

        // 8. Return GraphQLResponse with null schema
        return new GraphQLResponse(httpResponse, null);
    }

    /**
     * Introspects a GraphQL endpoint to retrieve its schema.
     * Sends the standard introspection query and extracts the schema
     * from the response body's {@code data.__schema} field.
     *
     * @param request the introspection request containing url, headers, and auth
     * @param teamId  team ID for variable resolution and history recording
     * @param userId  user ID for history recording
     * @return GraphQL response with the schema JSON in the schema field
     */
    public GraphQLResponse introspect(IntrospectGraphQLRequest request, UUID teamId, UUID userId) {
        ExecuteGraphQLRequest execRequest = new ExecuteGraphQLRequest(
                request.url(),
                INTROSPECTION_QUERY,
                null,
                "IntrospectionQuery",
                request.headers(),
                request.auth(),
                null
        );

        GraphQLResponse queryResponse = executeQuery(execRequest, teamId, userId);
        ProxyResponse httpResponse = queryResponse.httpResponse();

        // Extract schema from response body
        String schema = extractSchema(httpResponse.responseBody());

        log.info("GraphQL introspection completed for {} — schema {}",
                request.url(), schema != null ? "extracted" : "not found");

        return new GraphQLResponse(httpResponse, schema);
    }

    /**
     * Validates a GraphQL query string with basic syntax checks.
     * Not a full parser — checks for balanced braces, valid keywords,
     * and non-empty content.
     *
     * @param query the GraphQL query string to validate
     * @return list of error messages; empty if valid
     */
    public List<String> validateQuery(String query) {
        List<String> errors = new ArrayList<>();

        if (query == null) {
            errors.add("Query must not be null");
            return errors;
        }

        if (query.isBlank()) {
            errors.add("Query must not be empty");
            return errors;
        }

        String trimmed = query.trim();

        // Check balanced braces
        int braceCount = 0;
        for (char c : trimmed.toCharArray()) {
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
            }
            if (braceCount < 0) {
                errors.add("Unbalanced braces: unexpected closing brace");
                return errors;
            }
        }
        if (braceCount != 0) {
            errors.add("Unbalanced braces: " + braceCount + " unclosed opening brace(s)");
        }

        // Check starts with valid keyword or shorthand query
        if (!trimmed.startsWith("{")
                && !trimmed.startsWith("query")
                && !trimmed.startsWith("mutation")
                && !trimmed.startsWith("subscription")
                && !trimmed.startsWith("fragment")) {
            errors.add("Query must start with 'query', 'mutation', 'subscription', 'fragment', or '{'");
        }

        return errors;
    }

    /**
     * Formats and prettifies a GraphQL query string with proper indentation.
     * Normalizes whitespace and adds indentation based on brace depth.
     *
     * @param query the GraphQL query string to format
     * @return the formatted query string, or the original if null/empty
     */
    public String formatQuery(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }

        // Normalize whitespace: collapse multiple spaces/newlines into single spaces
        String normalized = query.replaceAll("\\s+", " ").trim();

        StringBuilder formatted = new StringBuilder();
        int indent = 0;
        boolean inString = false;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);

            if (c == '"' && (i == 0 || normalized.charAt(i - 1) != '\\')) {
                inString = !inString;
                formatted.append(c);
                continue;
            }

            if (inString) {
                formatted.append(c);
                continue;
            }

            if (c == '{') {
                if (formatted.length() > 0 && formatted.charAt(formatted.length() - 1) != ' '
                        && formatted.charAt(formatted.length() - 1) != '\n') {
                    formatted.append(' ');
                }
                formatted.append("{\n");
                indent++;
                appendIndent(formatted, indent);
            } else if (c == '}') {
                formatted.append('\n');
                indent--;
                appendIndent(formatted, indent);
                formatted.append('}');
            } else if (c == ' ') {
                // Skip spaces after newlines (we add our own indentation)
                if (formatted.length() > 0 && formatted.charAt(formatted.length() - 1) != '\n'
                        && formatted.charAt(formatted.length() - 1) != ' ') {
                    formatted.append(' ');
                }
            } else {
                formatted.append(c);
            }
        }

        return formatted.toString().trim();
    }

    /**
     * Builds the GraphQL JSON payload from resolved query, variables, and operation name.
     *
     * @param query         the resolved GraphQL query string
     * @param variables     the resolved variables JSON string (nullable)
     * @param operationName the operation name (nullable)
     * @return the serialized JSON payload string
     */
    private String buildGraphQLPayload(String query, String variables, String operationName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);

        if (variables != null && !variables.isBlank()) {
            try {
                Object parsedVars = objectMapper.readValue(variables, Object.class);
                payload.put("variables", parsedVars);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse GraphQL variables as JSON, sending as string: {}", e.getMessage());
                payload.put("variables", variables);
            }
        }

        if (operationName != null && !operationName.isBlank()) {
            payload.put("operationName", operationName);
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize GraphQL payload: {}", e.getMessage());
            throw new ValidationException("Failed to build GraphQL request payload");
        }
    }

    /**
     * Extracts the {@code __schema} JSON from a GraphQL introspection response body.
     *
     * @param responseBody the raw HTTP response body string
     * @return the schema JSON string, or null if extraction fails
     */
    private String extractSchema(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataNode = root.path("data");
            JsonNode schemaNode = dataNode.path("__schema");
            if (schemaNode.isMissingNode() || schemaNode.isNull()) {
                log.warn("No __schema found in introspection response");
                return null;
            }
            return objectMapper.writeValueAsString(schemaNode);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse introspection response body: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Appends indentation spaces to the string builder.
     *
     * @param sb     the string builder
     * @param level  the indentation level (2 spaces per level)
     */
    private void appendIndent(StringBuilder sb, int level) {
        sb.append("  ".repeat(Math.max(0, level)));
    }
}
