package com.codeops.courier.service;

import com.codeops.courier.dto.mapper.EnvironmentMapper;
import com.codeops.courier.dto.mapper.EnvironmentVariableMapper;
import com.codeops.courier.dto.request.CloneEnvironmentRequest;
import com.codeops.courier.dto.request.CreateEnvironmentRequest;
import com.codeops.courier.dto.request.SaveEnvironmentVariablesRequest;
import com.codeops.courier.dto.request.UpdateEnvironmentRequest;
import com.codeops.courier.dto.response.EnvironmentResponse;
import com.codeops.courier.dto.response.EnvironmentVariableResponse;
import com.codeops.courier.entity.Environment;
import com.codeops.courier.entity.EnvironmentVariable;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.EnvironmentRepository;
import com.codeops.courier.repository.EnvironmentVariableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing environments including CRUD operations,
 * cloning, activation toggling, and environment variable management
 * with secret masking for display responses.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class EnvironmentService {

    private static final String SECRET_MASK = "••••••••";

    private final EnvironmentRepository environmentRepository;
    private final EnvironmentVariableRepository environmentVariableRepository;
    private final EnvironmentMapper environmentMapper;
    private final EnvironmentVariableMapper environmentVariableMapper;

    /**
     * Creates a new environment for the given team.
     * Validates name uniqueness within the team.
     * If this is the first environment for the team, it is automatically set as active.
     *
     * @param teamId  the team owning the environment
     * @param userId  the user creating the environment
     * @param request the creation request
     * @return the created environment response with variableCount=0
     * @throws ValidationException if an environment with the same name already exists in the team
     */
    public EnvironmentResponse createEnvironment(UUID teamId, UUID userId, CreateEnvironmentRequest request) {
        if (environmentRepository.existsByTeamIdAndName(teamId, request.name())) {
            throw new ValidationException("Environment with name '" + request.name() + "' already exists in this team");
        }

        Environment entity = environmentMapper.toEntity(request);
        entity.setTeamId(teamId);
        entity.setCreatedBy(userId);

        if (environmentRepository.countByTeamId(teamId) == 0) {
            entity.setActive(true);
        }

        Environment saved = environmentRepository.save(entity);
        log.info("Created environment '{}' for team {}", saved.getName(), teamId);
        return toResponse(saved, 0);
    }

    /**
     * Gets an environment by ID, validating team ownership.
     *
     * @param environmentId the environment ID
     * @param teamId        the team ID for access validation
     * @return the environment response with computed variable count
     * @throws NotFoundException if the environment is not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public EnvironmentResponse getEnvironment(UUID environmentId, UUID teamId) {
        Environment entity = findEnvironmentAndValidateTeam(environmentId, teamId);
        int variableCount = (int) environmentVariableRepository.findByEnvironmentId(environmentId).size();
        return toResponse(entity, variableCount);
    }

    /**
     * Lists all environments for a team with computed variable counts.
     *
     * @param teamId the team ID
     * @return the list of environment responses
     */
    @Transactional(readOnly = true)
    public List<EnvironmentResponse> getEnvironments(UUID teamId) {
        return environmentRepository.findByTeamId(teamId).stream()
                .map(env -> toResponse(env, environmentVariableRepository.findByEnvironmentId(env.getId()).size()))
                .toList();
    }

    /**
     * Gets the active environment for a team.
     *
     * @param teamId the team ID
     * @return the active environment response
     * @throws NotFoundException if no active environment exists for the team
     */
    @Transactional(readOnly = true)
    public EnvironmentResponse getActiveEnvironment(UUID teamId) {
        Environment active = environmentRepository.findByTeamIdAndIsActiveTrue(teamId)
                .orElseThrow(() -> new NotFoundException("No active environment found for team: " + teamId));
        int variableCount = (int) environmentVariableRepository.findByEnvironmentId(active.getId()).size();
        return toResponse(active, variableCount);
    }

    /**
     * Sets an environment as active for the team, deactivating the current active environment.
     *
     * @param environmentId the environment ID to activate
     * @param teamId        the team ID for access validation
     * @return the activated environment response
     * @throws NotFoundException if the environment is not found or belongs to a different team
     */
    public EnvironmentResponse setActiveEnvironment(UUID environmentId, UUID teamId) {
        Environment target = findEnvironmentAndValidateTeam(environmentId, teamId);

        environmentRepository.findByTeamIdAndIsActiveTrue(teamId).ifPresent(current -> {
            current.setActive(false);
            environmentRepository.save(current);
        });

        target.setActive(true);
        Environment saved = environmentRepository.save(target);
        log.info("Set environment '{}' as active for team {}", saved.getName(), teamId);
        int variableCount = (int) environmentVariableRepository.findByEnvironmentId(saved.getId()).size();
        return toResponse(saved, variableCount);
    }

    /**
     * Updates an environment with partial update semantics — only non-null fields are applied.
     * Validates name uniqueness if renaming.
     *
     * @param environmentId the environment ID
     * @param teamId        the team ID for access validation
     * @param request       the update request with optional fields
     * @return the updated environment response
     * @throws NotFoundException   if the environment is not found
     * @throws ValidationException if renaming would create a duplicate name
     */
    public EnvironmentResponse updateEnvironment(UUID environmentId, UUID teamId, UpdateEnvironmentRequest request) {
        Environment entity = findEnvironmentAndValidateTeam(environmentId, teamId);

        if (request.name() != null) {
            if (!request.name().equals(entity.getName()) && environmentRepository.existsByTeamIdAndName(teamId, request.name())) {
                throw new ValidationException("Environment with name '" + request.name() + "' already exists in this team");
            }
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }

        Environment saved = environmentRepository.save(entity);
        log.info("Updated environment '{}'", saved.getName());
        int variableCount = (int) environmentVariableRepository.findByEnvironmentId(saved.getId()).size();
        return toResponse(saved, variableCount);
    }

    /**
     * Deletes an environment and all its variables via cascade.
     * If the deleted environment was active, another environment is automatically activated.
     *
     * @param environmentId the environment ID
     * @param teamId        the team ID for access validation
     * @throws NotFoundException if the environment is not found or belongs to a different team
     */
    public void deleteEnvironment(UUID environmentId, UUID teamId) {
        Environment entity = findEnvironmentAndValidateTeam(environmentId, teamId);
        boolean wasActive = entity.isActive();
        environmentRepository.delete(entity);
        log.info("Deleted environment '{}' ({})", entity.getName(), environmentId);

        if (wasActive) {
            environmentRepository.findByTeamId(teamId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setActive(true);
                        environmentRepository.save(next);
                        log.info("Auto-activated environment '{}' after deletion", next.getName());
                    });
        }
    }

    /**
     * Clones an environment with a deep copy of all variables.
     * The clone is always created as inactive.
     *
     * @param environmentId the source environment ID
     * @param teamId        the team ID for access validation
     * @param userId        the user performing the clone
     * @param request       the clone request containing the new name
     * @return the cloned environment response
     * @throws NotFoundException   if the source environment is not found
     * @throws ValidationException if the new name already exists in the team
     */
    public EnvironmentResponse cloneEnvironment(UUID environmentId, UUID teamId, UUID userId, CloneEnvironmentRequest request) {
        Environment source = findEnvironmentAndValidateTeam(environmentId, teamId);

        if (environmentRepository.existsByTeamIdAndName(teamId, request.newName())) {
            throw new ValidationException("Environment with name '" + request.newName() + "' already exists in this team");
        }

        Environment clone = new Environment();
        clone.setTeamId(teamId);
        clone.setCreatedBy(userId);
        clone.setName(request.newName());
        clone.setDescription(source.getDescription());
        clone.setActive(false);

        Environment savedClone = environmentRepository.save(clone);

        List<EnvironmentVariable> sourceVars = environmentVariableRepository.findByEnvironmentId(source.getId());
        List<EnvironmentVariable> clonedVars = new ArrayList<>();
        for (EnvironmentVariable srcVar : sourceVars) {
            EnvironmentVariable varCopy = new EnvironmentVariable();
            varCopy.setVariableKey(srcVar.getVariableKey());
            varCopy.setVariableValue(srcVar.getVariableValue());
            varCopy.setSecret(srcVar.isSecret());
            varCopy.setEnabled(srcVar.isEnabled());
            varCopy.setScope(srcVar.getScope());
            varCopy.setEnvironment(savedClone);
            clonedVars.add(varCopy);
        }
        List<EnvironmentVariable> savedVars = environmentVariableRepository.saveAll(clonedVars);

        log.info("Cloned environment '{}' as '{}' with {} variables", source.getName(), savedClone.getName(), savedVars.size());
        return toResponse(savedClone, savedVars.size());
    }

    /**
     * Gets all variables for an environment with secret values masked.
     *
     * @param environmentId the environment ID
     * @param teamId        the team ID for access validation
     * @return the list of environment variable responses with secrets masked
     * @throws NotFoundException if the environment is not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public List<EnvironmentVariableResponse> getEnvironmentVariables(UUID environmentId, UUID teamId) {
        findEnvironmentAndValidateTeam(environmentId, teamId);
        List<EnvironmentVariable> variables = environmentVariableRepository.findByEnvironmentId(environmentId);
        return variables.stream()
                .map(environmentVariableMapper::toResponse)
                .map(this::maskIfSecret)
                .toList();
    }

    /**
     * Saves variables for an environment using batch replace semantics.
     * Deletes all existing variables and creates new ones from the request.
     *
     * @param environmentId the environment ID
     * @param teamId        the team ID for access validation
     * @param request       the save request containing the variable entries
     * @return the saved variable responses with secrets masked
     * @throws NotFoundException if the environment is not found or belongs to a different team
     */
    public List<EnvironmentVariableResponse> saveEnvironmentVariables(UUID environmentId, UUID teamId, SaveEnvironmentVariablesRequest request) {
        Environment environment = findEnvironmentAndValidateTeam(environmentId, teamId);

        environmentVariableRepository.deleteByEnvironmentId(environmentId);
        environmentVariableRepository.flush();

        List<EnvironmentVariable> newVars = new ArrayList<>();
        for (SaveEnvironmentVariablesRequest.VariableEntry entry : request.variables()) {
            EnvironmentVariable variable = new EnvironmentVariable();
            variable.setVariableKey(entry.variableKey());
            variable.setVariableValue(entry.variableValue());
            variable.setSecret(entry.isSecret());
            variable.setEnabled(entry.isEnabled());
            variable.setScope("ENVIRONMENT");
            variable.setEnvironment(environment);
            newVars.add(variable);
        }

        List<EnvironmentVariable> saved = environmentVariableRepository.saveAll(newVars);
        log.info("Saved {} variables for environment '{}'", saved.size(), environment.getName());
        return saved.stream()
                .map(environmentVariableMapper::toResponse)
                .map(this::maskIfSecret)
                .toList();
    }

    /**
     * Finds an environment by ID and validates team ownership.
     *
     * @param environmentId the environment ID
     * @param teamId        the expected team ID
     * @return the environment entity
     * @throws NotFoundException if not found or wrong team
     */
    Environment findEnvironmentAndValidateTeam(UUID environmentId, UUID teamId) {
        Environment entity = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + environmentId));
        if (!entity.getTeamId().equals(teamId)) {
            throw new NotFoundException("Environment not found: " + environmentId);
        }
        return entity;
    }

    private EnvironmentResponse toResponse(Environment entity, int variableCount) {
        return new EnvironmentResponse(
                entity.getId(),
                entity.getTeamId(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getCreatedBy(),
                variableCount,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private EnvironmentVariableResponse maskIfSecret(EnvironmentVariableResponse response) {
        if (response.isSecret()) {
            return new EnvironmentVariableResponse(
                    response.id(),
                    response.variableKey(),
                    SECRET_MASK,
                    response.isSecret(),
                    response.isEnabled(),
                    response.scope()
            );
        }
        return response;
    }
}
