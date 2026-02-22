package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.BatchSaveGlobalVariablesRequest;
import com.codeops.courier.dto.request.SaveGlobalVariableRequest;
import com.codeops.courier.dto.response.GlobalVariableResponse;
import com.codeops.courier.service.VariableService;
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
 * REST controller for managing team-scoped global variables.
 * Global variables are accessible across all environments and collections
 * within a team. All endpoints require authentication and the ADMIN role.
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/variables/global")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Global Variables", description = "Team-scoped global variable CRUD")
public class VariableController {

    private final VariableService variableService;

    /**
     * Returns all global variables for the team with secret values masked.
     *
     * @param teamId the team ID from the request header
     * @return list of global variable responses
     */
    @GetMapping
    public List<GlobalVariableResponse> getGlobalVariables(@RequestHeader("X-Team-ID") UUID teamId) {
        return variableService.getGlobalVariables(teamId);
    }

    /**
     * Saves a single global variable using upsert semantics.
     * If a variable with the same key exists, it is updated; otherwise a new variable is created.
     *
     * @param teamId  the team ID from the request header
     * @param request the save request with variable key, value, and flags
     * @return the saved global variable response
     */
    @PostMapping
    public GlobalVariableResponse saveGlobalVariable(@RequestHeader("X-Team-ID") UUID teamId,
                                                      @Valid @RequestBody SaveGlobalVariableRequest request) {
        log.info("Saving global variable '{}' for team {}", request.variableKey(), teamId);
        return variableService.saveGlobalVariable(teamId, request);
    }

    /**
     * Batch saves multiple global variables using additive semantics.
     * Existing variables with matching keys are updated; new keys create new variables.
     *
     * @param teamId  the team ID from the request header
     * @param request the batch save request containing multiple variable entries
     * @return the list of saved global variable responses
     */
    @PostMapping("/batch")
    public List<GlobalVariableResponse> batchSaveGlobalVariables(@RequestHeader("X-Team-ID") UUID teamId,
                                                                   @Valid @RequestBody BatchSaveGlobalVariablesRequest request) {
        log.info("Batch saving {} global variables for team {}", request.variables().size(), teamId);
        return variableService.batchSaveGlobalVariables(teamId, request);
    }

    /**
     * Deletes a global variable.
     *
     * @param variableId the global variable ID
     * @param teamId     the team ID from the request header
     */
    @DeleteMapping("/{variableId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGlobalVariable(@PathVariable UUID variableId,
                                      @RequestHeader("X-Team-ID") UUID teamId) {
        log.info("Deleting global variable {} for team {}", variableId, teamId);
        variableService.deleteGlobalVariable(variableId, teamId);
    }
}
