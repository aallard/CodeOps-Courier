package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.ExecuteGraphQLRequest;
import com.codeops.courier.dto.request.IntrospectGraphQLRequest;
import com.codeops.courier.dto.response.GraphQLResponse;
import com.codeops.courier.security.SecurityUtils;
import com.codeops.courier.service.GraphQLService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for GraphQL operations including query execution,
 * schema introspection, query validation, and formatting.
 * All endpoints require authentication and the ADMIN role.
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/graphql")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "GraphQL", description = "GraphQL query execution, introspection, validation, and formatting")
public class GraphQLController {

    private final GraphQLService graphQLService;

    /**
     * Executes a GraphQL query against the specified endpoint.
     *
     * @param teamId  the team ID from the request header
     * @param request the GraphQL execution request with URL, query, variables, and auth
     * @return the GraphQL response containing the HTTP response and parsed data
     */
    @PostMapping("/execute")
    public GraphQLResponse executeQuery(@RequestHeader("X-Team-ID") UUID teamId,
                                         @Valid @RequestBody ExecuteGraphQLRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Executing GraphQL query against {} for team {}", request.url(), teamId);
        return graphQLService.executeQuery(request, teamId, userId);
    }

    /**
     * Introspects a GraphQL endpoint to retrieve its schema.
     *
     * @param teamId  the team ID from the request header
     * @param request the introspection request with URL and optional auth
     * @return the GraphQL response containing the schema
     */
    @PostMapping("/introspect")
    public GraphQLResponse introspect(@RequestHeader("X-Team-ID") UUID teamId,
                                       @Valid @RequestBody IntrospectGraphQLRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Introspecting GraphQL endpoint {} for team {}", request.url(), teamId);
        return graphQLService.introspect(request, teamId, userId);
    }

    /**
     * Validates a GraphQL query for syntax errors without executing it.
     *
     * @param body a map containing a {@code query} key with the GraphQL query string
     * @return a list of validation error messages, empty if the query is valid
     */
    @PostMapping("/validate")
    public List<String> validateQuery(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        return graphQLService.validateQuery(query);
    }

    /**
     * Formats and prettifies a GraphQL query string.
     *
     * @param body a map containing a {@code query} key with the GraphQL query string
     * @return a map containing the formatted {@code query} string
     */
    @PostMapping("/format")
    public Map<String, String> formatQuery(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        String formatted = graphQLService.formatQuery(query);
        return Map.of("query", formatted);
    }
}
