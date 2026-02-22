package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.CloneEnvironmentRequest;
import com.codeops.courier.dto.request.CreateEnvironmentRequest;
import com.codeops.courier.dto.request.SaveEnvironmentVariablesRequest;
import com.codeops.courier.dto.request.UpdateEnvironmentRequest;
import com.codeops.courier.dto.response.EnvironmentResponse;
import com.codeops.courier.dto.response.EnvironmentVariableResponse;
import com.codeops.courier.security.SecurityUtils;
import com.codeops.courier.service.EnvironmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing environments and their variables.
 * Provides CRUD operations, activation toggling, cloning, and variable management.
 * All endpoints require authentication and the ADMIN role.
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/environments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Environments", description = "Environment CRUD, activation, cloning, and variable management")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    /**
     * Creates a new environment for the team.
     *
     * @param teamId  the team ID from the request header
     * @param request the environment creation request
     * @return the created environment response
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse createEnvironment(@RequestHeader("X-Team-ID") UUID teamId,
                                                  @Valid @RequestBody CreateEnvironmentRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Creating environment '{}' for team {}", request.name(), teamId);
        return environmentService.createEnvironment(teamId, userId, request);
    }

    /**
     * Returns all environments for the team.
     *
     * @param teamId the team ID from the request header
     * @return list of environment responses
     */
    @GetMapping
    public List<EnvironmentResponse> getEnvironments(@RequestHeader("X-Team-ID") UUID teamId) {
        return environmentService.getEnvironments(teamId);
    }

    /**
     * Returns the active environment for the team.
     *
     * @param teamId the team ID from the request header
     * @return the active environment response
     */
    @GetMapping("/active")
    public EnvironmentResponse getActiveEnvironment(@RequestHeader("X-Team-ID") UUID teamId) {
        return environmentService.getActiveEnvironment(teamId);
    }

    /**
     * Returns the detail of a specific environment.
     *
     * @param environmentId the environment ID
     * @param teamId        the team ID from the request header
     * @return the environment response
     */
    @GetMapping("/{environmentId}")
    public EnvironmentResponse getEnvironment(@PathVariable UUID environmentId,
                                               @RequestHeader("X-Team-ID") UUID teamId) {
        return environmentService.getEnvironment(environmentId, teamId);
    }

    /**
     * Updates an environment's metadata.
     *
     * @param environmentId the environment ID
     * @param teamId        the team ID from the request header
     * @param request       the update request
     * @return the updated environment response
     */
    @PutMapping("/{environmentId}")
    public EnvironmentResponse updateEnvironment(@PathVariable UUID environmentId,
                                                  @RequestHeader("X-Team-ID") UUID teamId,
                                                  @Valid @RequestBody UpdateEnvironmentRequest request) {
        log.info("Updating environment {} for team {}", environmentId, teamId);
        return environmentService.updateEnvironment(environmentId, teamId, request);
    }

    /**
     * Sets an environment as the active environment for the team.
     *
     * @param environmentId the environment ID to activate
     * @param teamId        the team ID from the request header
     * @return the activated environment response
     */
    @PutMapping("/{environmentId}/activate")
    public EnvironmentResponse activateEnvironment(@PathVariable UUID environmentId,
                                                    @RequestHeader("X-Team-ID") UUID teamId) {
        log.info("Activating environment {} for team {}", environmentId, teamId);
        return environmentService.setActiveEnvironment(environmentId, teamId);
    }

    /**
     * Deletes an environment and all its variables.
     *
     * @param environmentId the environment ID
     * @param teamId        the team ID from the request header
     */
    @DeleteMapping("/{environmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEnvironment(@PathVariable UUID environmentId,
                                   @RequestHeader("X-Team-ID") UUID teamId) {
        log.info("Deleting environment {} for team {}", environmentId, teamId);
        environmentService.deleteEnvironment(environmentId, teamId);
    }

    /**
     * Clones an environment with all its variables.
     *
     * @param environmentId the source environment ID
     * @param teamId        the team ID from the request header
     * @param request       the clone request with the new name
     * @return the cloned environment response
     */
    @PostMapping("/{environmentId}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse cloneEnvironment(@PathVariable UUID environmentId,
                                                 @RequestHeader("X-Team-ID") UUID teamId,
                                                 @Valid @RequestBody CloneEnvironmentRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Cloning environment {} as '{}' for team {}", environmentId, request.newName(), teamId);
        return environmentService.cloneEnvironment(environmentId, teamId, userId, request);
    }

    /**
     * Returns all variables for an environment with secret values masked.
     *
     * @param environmentId the environment ID
     * @param teamId        the team ID from the request header
     * @return list of environment variable responses
     */
    @GetMapping("/{environmentId}/variables")
    public List<EnvironmentVariableResponse> getEnvironmentVariables(@PathVariable UUID environmentId,
                                                                      @RequestHeader("X-Team-ID") UUID teamId) {
        return environmentService.getEnvironmentVariables(environmentId, teamId);
    }

    /**
     * Replaces all variables for an environment.
     *
     * @param environmentId the environment ID
     * @param teamId        the team ID from the request header
     * @param request       the save request containing variable entries
     * @return the saved variable responses
     */
    @PutMapping("/{environmentId}/variables")
    public List<EnvironmentVariableResponse> saveEnvironmentVariables(@PathVariable UUID environmentId,
                                                                       @RequestHeader("X-Team-ID") UUID teamId,
                                                                       @Valid @RequestBody SaveEnvironmentVariablesRequest request) {
        log.info("Saving {} variables for environment {} in team {}", request.variables().size(), environmentId, teamId);
        return environmentService.saveEnvironmentVariables(environmentId, teamId, request);
    }
}
