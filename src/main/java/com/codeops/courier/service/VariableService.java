package com.codeops.courier.service;

import com.codeops.courier.dto.mapper.GlobalVariableMapper;
import com.codeops.courier.dto.request.BatchSaveGlobalVariablesRequest;
import com.codeops.courier.dto.request.SaveGlobalVariableRequest;
import com.codeops.courier.dto.response.EnvironmentVariableResponse;
import com.codeops.courier.dto.response.GlobalVariableResponse;
import com.codeops.courier.dto.response.RequestHeaderResponse;
import com.codeops.courier.entity.Environment;
import com.codeops.courier.entity.EnvironmentVariable;
import com.codeops.courier.entity.GlobalVariable;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.repository.EnvironmentVariableRepository;
import com.codeops.courier.repository.GlobalVariableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core variable resolution engine for Courier's runtime behavior.
 * Manages global variable CRUD and provides scoped variable resolution
 * with {{variableName}} placeholder substitution across the full
 * scope hierarchy: Global, Collection, Environment, Local.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class VariableService {

    private static final String SECRET_MASK = "••••••••";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final GlobalVariableRepository globalVariableRepository;
    private final EnvironmentVariableRepository environmentVariableRepository;
    private final GlobalVariableMapper globalVariableMapper;

    // ─── Variable Resolution Engine ───

    /**
     * Resolves all {{variableName}} placeholders in a string using the full scope hierarchy.
     * Resolution order (higher priority overrides lower):
     * <ol>
     *   <li>Global (team-wide, lowest priority)</li>
     *   <li>Collection (collection-scoped variables)</li>
     *   <li>Environment (active environment variables)</li>
     *   <li>Local (runtime-only, highest priority)</li>
     * </ol>
     * Unresolved placeholders are left as-is.
     *
     * @param input         the string containing {{variable}} placeholders
     * @param teamId        team ID for global variables
     * @param collectionId  collection ID for collection-scoped variables (nullable)
     * @param environmentId environment ID for env-scoped variables (nullable)
     * @param localVars     runtime local variables (nullable)
     * @return the string with all resolvable placeholders substituted
     */
    @Transactional(readOnly = true)
    public String resolveVariables(String input, UUID teamId, UUID collectionId, UUID environmentId, Map<String, String> localVars) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        Map<String, String> mergedVars = buildVariableMap(teamId, collectionId, environmentId, localVars);

        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = mergedVars.getOrDefault(varName, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Builds the merged variable map for a given scope context.
     * Later scopes override earlier scopes (Global < Collection < Environment < Local).
     * Only enabled variables are included. Secret variable values are included
     * (unmasked) for use in request execution.
     *
     * @param teamId        team ID for global variables
     * @param collectionId  collection ID for collection-scoped variables (nullable)
     * @param environmentId environment ID for env-scoped variables (nullable)
     * @param localVars     runtime local variables (nullable)
     * @return the merged variable map with resolved values
     */
    @Transactional(readOnly = true)
    public Map<String, String> buildVariableMap(UUID teamId, UUID collectionId, UUID environmentId, Map<String, String> localVars) {
        Map<String, String> merged = new LinkedHashMap<>();

        // 1. Global variables (lowest priority)
        List<GlobalVariable> globals = globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId);
        for (GlobalVariable gv : globals) {
            merged.put(gv.getVariableKey(), gv.getVariableValue());
        }

        // 2. Collection variables (override global)
        if (collectionId != null) {
            List<EnvironmentVariable> collectionVars = environmentVariableRepository.findByCollectionIdAndIsEnabledTrue(collectionId);
            for (EnvironmentVariable cv : collectionVars) {
                merged.put(cv.getVariableKey(), cv.getVariableValue());
            }
        }

        // 3. Environment variables (override collection)
        if (environmentId != null) {
            List<EnvironmentVariable> envVars = environmentVariableRepository.findByEnvironmentIdAndIsEnabledTrue(environmentId);
            for (EnvironmentVariable ev : envVars) {
                merged.put(ev.getVariableKey(), ev.getVariableValue());
            }
        }

        // 4. Local variables (highest priority)
        if (localVars != null) {
            merged.putAll(localVars);
        }

        return merged;
    }

    /**
     * Resolves variables in a URL — delegates to {@link #resolveVariables}.
     *
     * @param url           the URL containing {{variable}} placeholders
     * @param teamId        team ID for global variables
     * @param collectionId  collection ID for collection-scoped variables (nullable)
     * @param environmentId environment ID for env-scoped variables (nullable)
     * @param localVars     runtime local variables (nullable)
     * @return the URL with all resolvable placeholders substituted
     */
    @Transactional(readOnly = true)
    public String resolveUrl(String url, UUID teamId, UUID collectionId, UUID environmentId, Map<String, String> localVars) {
        return resolveVariables(url, teamId, collectionId, environmentId, localVars);
    }

    /**
     * Resolves variables in request headers.
     * Returns a new map with resolved key/value pairs for enabled headers only.
     *
     * @param headers       the request header responses
     * @param teamId        team ID for global variables
     * @param collectionId  collection ID for collection-scoped variables (nullable)
     * @param environmentId environment ID for env-scoped variables (nullable)
     * @param localVars     runtime local variables (nullable)
     * @return a map of resolved header keys to resolved header values
     */
    @Transactional(readOnly = true)
    public Map<String, String> resolveHeaders(List<RequestHeaderResponse> headers, UUID teamId, UUID collectionId, UUID environmentId, Map<String, String> localVars) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (headers == null) {
            return resolved;
        }
        for (RequestHeaderResponse header : headers) {
            if (header.isEnabled()) {
                String key = resolveVariables(header.headerKey(), teamId, collectionId, environmentId, localVars);
                String value = resolveVariables(header.headerValue(), teamId, collectionId, environmentId, localVars);
                resolved.put(key, value);
            }
        }
        return resolved;
    }

    /**
     * Resolves variables in a request body string — delegates to {@link #resolveVariables}.
     *
     * @param body          the body string containing {{variable}} placeholders
     * @param teamId        team ID for global variables
     * @param collectionId  collection ID for collection-scoped variables (nullable)
     * @param environmentId environment ID for env-scoped variables (nullable)
     * @param localVars     runtime local variables (nullable)
     * @return the body with all resolvable placeholders substituted
     */
    @Transactional(readOnly = true)
    public String resolveBody(String body, UUID teamId, UUID collectionId, UUID environmentId, Map<String, String> localVars) {
        return resolveVariables(body, teamId, collectionId, environmentId, localVars);
    }

    // ─── Global Variable CRUD ───

    /**
     * Gets all global variables for a team with secret values masked.
     *
     * @param teamId the team ID
     * @return the list of global variable responses with secrets masked
     */
    @Transactional(readOnly = true)
    public List<GlobalVariableResponse> getGlobalVariables(UUID teamId) {
        return globalVariableRepository.findByTeamId(teamId).stream()
                .map(globalVariableMapper::toResponse)
                .map(this::maskIfSecret)
                .toList();
    }

    /**
     * Saves a single global variable (upsert semantics).
     * If a variable with the same key exists for the team, it is updated.
     * Otherwise, a new variable is created.
     *
     * @param teamId  the team ID
     * @param request the save request with variable key, value, and flags
     * @return the saved global variable response with secret masked if applicable
     */
    public GlobalVariableResponse saveGlobalVariable(UUID teamId, SaveGlobalVariableRequest request) {
        GlobalVariable variable = globalVariableRepository.findByTeamIdAndVariableKey(teamId, request.variableKey())
                .orElseGet(() -> {
                    GlobalVariable newVar = new GlobalVariable();
                    newVar.setTeamId(teamId);
                    newVar.setVariableKey(request.variableKey());
                    return newVar;
                });

        variable.setVariableValue(request.variableValue());
        variable.setSecret(request.isSecret());
        variable.setEnabled(request.isEnabled());

        GlobalVariable saved = globalVariableRepository.save(variable);
        log.info("Saved global variable '{}' for team {}", saved.getVariableKey(), teamId);
        return maskIfSecret(globalVariableMapper.toResponse(saved));
    }

    /**
     * Batch saves global variables using additive semantics.
     * Does not delete existing variables — creates new or updates existing by key.
     *
     * @param teamId  the team ID
     * @param request the batch save request containing multiple variable entries
     * @return the list of saved global variable responses with secrets masked
     */
    public List<GlobalVariableResponse> batchSaveGlobalVariables(UUID teamId, BatchSaveGlobalVariablesRequest request) {
        return request.variables().stream()
                .map(var -> saveGlobalVariable(teamId, var))
                .toList();
    }

    /**
     * Deletes a global variable.
     *
     * @param variableId the global variable ID
     * @param teamId     the team ID for access validation
     * @throws NotFoundException if the variable is not found or belongs to a different team
     */
    public void deleteGlobalVariable(UUID variableId, UUID teamId) {
        GlobalVariable variable = globalVariableRepository.findById(variableId)
                .orElseThrow(() -> new NotFoundException("Global variable not found: " + variableId));
        if (!variable.getTeamId().equals(teamId)) {
            throw new NotFoundException("Global variable not found: " + variableId);
        }
        globalVariableRepository.delete(variable);
        log.info("Deleted global variable '{}' ({})", variable.getVariableKey(), variableId);
    }

    /**
     * Gets the unmasked value of a secret variable for use in request execution.
     * This method should only be called internally by RequestProxyService.
     * Checks both GlobalVariable and EnvironmentVariable repositories.
     *
     * @param variableId the variable ID (global or environment)
     * @param teamId     the team ID for access validation
     * @return the raw unmasked value
     * @throws NotFoundException if the variable is not found or does not belong to the team
     */
    @Transactional(readOnly = true)
    public String getSecretValue(UUID variableId, UUID teamId) {
        // Try global variable first
        var globalVar = globalVariableRepository.findById(variableId);
        if (globalVar.isPresent()) {
            if (!globalVar.get().getTeamId().equals(teamId)) {
                throw new NotFoundException("Variable not found: " + variableId);
            }
            return globalVar.get().getVariableValue();
        }

        // Try environment variable
        var envVar = environmentVariableRepository.findById(variableId);
        if (envVar.isPresent()) {
            Environment env = envVar.get().getEnvironment();
            if (env == null || !env.getTeamId().equals(teamId)) {
                throw new NotFoundException("Variable not found: " + variableId);
            }
            return envVar.get().getVariableValue();
        }

        throw new NotFoundException("Variable not found: " + variableId);
    }

    private GlobalVariableResponse maskIfSecret(GlobalVariableResponse response) {
        if (response.isSecret()) {
            return new GlobalVariableResponse(
                    response.id(),
                    response.teamId(),
                    response.variableKey(),
                    SECRET_MASK,
                    response.isSecret(),
                    response.isEnabled(),
                    response.createdAt(),
                    response.updatedAt()
            );
        }
        return response;
    }
}
