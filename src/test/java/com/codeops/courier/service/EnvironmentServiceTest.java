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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EnvironmentService covering CRUD operations,
 * cloning, activation toggling, and environment variable management.
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentServiceTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private EnvironmentVariableRepository environmentVariableRepository;

    @Mock
    private EnvironmentMapper environmentMapper;

    @Mock
    private EnvironmentVariableMapper environmentVariableMapper;

    @InjectMocks
    private EnvironmentService environmentService;

    private UUID teamId;
    private UUID userId;
    private UUID environmentId;
    private Environment environment;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        userId = UUID.randomUUID();
        environmentId = UUID.randomUUID();

        environment = new Environment();
        environment.setId(environmentId);
        environment.setTeamId(teamId);
        environment.setName("Development");
        environment.setDescription("Dev environment");
        environment.setActive(false);
        environment.setCreatedBy(userId);
        environment.setVariables(new ArrayList<>());
        environment.setCreatedAt(Instant.now());
        environment.setUpdatedAt(Instant.now());
    }

    @Test
    void createEnvironment_success() {
        CreateEnvironmentRequest request = new CreateEnvironmentRequest("Development", "Dev environment");

        when(environmentRepository.existsByTeamIdAndName(teamId, "Development")).thenReturn(false);
        when(environmentMapper.toEntity(request)).thenReturn(new Environment());
        when(environmentRepository.countByTeamId(teamId)).thenReturn(1L);
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> {
            Environment e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setCreatedAt(Instant.now());
            e.setUpdatedAt(Instant.now());
            return e;
        });

        EnvironmentResponse response = environmentService.createEnvironment(teamId, userId, request);

        assertThat(response).isNotNull();
        assertThat(response.variableCount()).isZero();
        verify(environmentRepository).save(any(Environment.class));
    }

    @Test
    void createEnvironment_duplicateName_throws() {
        CreateEnvironmentRequest request = new CreateEnvironmentRequest("Development", "Dev environment");

        when(environmentRepository.existsByTeamIdAndName(teamId, "Development")).thenReturn(true);

        assertThatThrownBy(() -> environmentService.createEnvironment(teamId, userId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createEnvironment_firstForTeam_setsActive() {
        CreateEnvironmentRequest request = new CreateEnvironmentRequest("Development", "Dev environment");
        Environment entity = new Environment();

        when(environmentRepository.existsByTeamIdAndName(teamId, "Development")).thenReturn(false);
        when(environmentMapper.toEntity(request)).thenReturn(entity);
        when(environmentRepository.countByTeamId(teamId)).thenReturn(0L);
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> {
            Environment e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setCreatedAt(Instant.now());
            e.setUpdatedAt(Instant.now());
            return e;
        });

        environmentService.createEnvironment(teamId, userId, request);

        assertThat(entity.isActive()).isTrue();
    }

    @Test
    void getEnvironment_success() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentVariableRepository.findByEnvironmentId(environmentId)).thenReturn(List.of());

        EnvironmentResponse response = environmentService.getEnvironment(environmentId, teamId);

        assertThat(response.name()).isEqualTo("Development");
        assertThat(response.variableCount()).isZero();
    }

    @Test
    void getEnvironment_notFound_throws() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> environmentService.getEnvironment(environmentId, teamId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(environmentId.toString());
    }

    @Test
    void getEnvironment_wrongTeam_throws() {
        UUID otherTeamId = UUID.randomUUID();
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        assertThatThrownBy(() -> environmentService.getEnvironment(environmentId, otherTeamId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getEnvironments_success() {
        Environment env2 = new Environment();
        env2.setId(UUID.randomUUID());
        env2.setTeamId(teamId);
        env2.setName("Staging");
        env2.setActive(false);
        env2.setCreatedBy(userId);
        env2.setCreatedAt(Instant.now());
        env2.setUpdatedAt(Instant.now());

        when(environmentRepository.findByTeamId(teamId)).thenReturn(List.of(environment, env2));
        when(environmentVariableRepository.findByEnvironmentId(any(UUID.class))).thenReturn(List.of());

        List<EnvironmentResponse> responses = environmentService.getEnvironments(teamId);

        assertThat(responses).hasSize(2);
    }

    @Test
    void getActiveEnvironment_success() {
        environment.setActive(true);
        when(environmentRepository.findByTeamIdAndIsActiveTrue(teamId)).thenReturn(Optional.of(environment));
        when(environmentVariableRepository.findByEnvironmentId(environmentId)).thenReturn(List.of());

        EnvironmentResponse response = environmentService.getActiveEnvironment(teamId);

        assertThat(response.isActive()).isTrue();
        assertThat(response.name()).isEqualTo("Development");
    }

    @Test
    void getActiveEnvironment_noActive_throws() {
        when(environmentRepository.findByTeamIdAndIsActiveTrue(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> environmentService.getActiveEnvironment(teamId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No active environment");
    }

    @Test
    void setActiveEnvironment_deactivatesPrevious() {
        Environment previous = new Environment();
        previous.setId(UUID.randomUUID());
        previous.setTeamId(teamId);
        previous.setActive(true);
        previous.setCreatedAt(Instant.now());
        previous.setUpdatedAt(Instant.now());

        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.findByTeamIdAndIsActiveTrue(teamId)).thenReturn(Optional.of(previous));
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(environmentVariableRepository.findByEnvironmentId(environmentId)).thenReturn(List.of());

        EnvironmentResponse response = environmentService.setActiveEnvironment(environmentId, teamId);

        assertThat(response.isActive()).isTrue();
        assertThat(previous.isActive()).isFalse();
    }

    @Test
    void setActiveEnvironment_success() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.findByTeamIdAndIsActiveTrue(teamId)).thenReturn(Optional.empty());
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(environmentVariableRepository.findByEnvironmentId(environmentId)).thenReturn(List.of());

        EnvironmentResponse response = environmentService.setActiveEnvironment(environmentId, teamId);

        assertThat(response.isActive()).isTrue();
    }

    @Test
    void updateEnvironment_success() {
        UpdateEnvironmentRequest request = new UpdateEnvironmentRequest("Staging", "Staging env");

        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.existsByTeamIdAndName(teamId, "Staging")).thenReturn(false);
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(environmentVariableRepository.findByEnvironmentId(environmentId)).thenReturn(List.of());

        EnvironmentResponse response = environmentService.updateEnvironment(environmentId, teamId, request);

        assertThat(response.name()).isEqualTo("Staging");
        assertThat(response.description()).isEqualTo("Staging env");
    }

    @Test
    void updateEnvironment_renameToDuplicate_throws() {
        UpdateEnvironmentRequest request = new UpdateEnvironmentRequest("Staging", null);

        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.existsByTeamIdAndName(teamId, "Staging")).thenReturn(true);

        assertThatThrownBy(() -> environmentService.updateEnvironment(environmentId, teamId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deleteEnvironment_success() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        environmentService.deleteEnvironment(environmentId, teamId);

        verify(environmentRepository).delete(environment);
    }

    @Test
    void deleteEnvironment_activeEnvironment_autoActivatesNext() {
        environment.setActive(true);
        Environment next = new Environment();
        next.setId(UUID.randomUUID());
        next.setTeamId(teamId);
        next.setActive(false);
        next.setCreatedAt(Instant.now());
        next.setUpdatedAt(Instant.now());

        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.findByTeamId(teamId)).thenReturn(List.of(next));
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));

        environmentService.deleteEnvironment(environmentId, teamId);

        verify(environmentRepository).delete(environment);
        assertThat(next.isActive()).isTrue();
    }

    @Test
    void cloneEnvironment_success() {
        CloneEnvironmentRequest request = new CloneEnvironmentRequest("Dev Copy");

        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.existsByTeamIdAndName(teamId, "Dev Copy")).thenReturn(false);
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> {
            Environment e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setCreatedAt(Instant.now());
            e.setUpdatedAt(Instant.now());
            return e;
        });
        when(environmentVariableRepository.findByEnvironmentId(environmentId)).thenReturn(List.of());
        when(environmentVariableRepository.saveAll(any())).thenReturn(List.of());

        EnvironmentResponse response = environmentService.cloneEnvironment(environmentId, teamId, userId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Dev Copy");
        assertThat(response.isActive()).isFalse();
    }

    @Test
    void cloneEnvironment_copiesVariables() {
        CloneEnvironmentRequest request = new CloneEnvironmentRequest("Dev Copy");

        EnvironmentVariable sourceVar = new EnvironmentVariable();
        sourceVar.setId(UUID.randomUUID());
        sourceVar.setVariableKey("API_KEY");
        sourceVar.setVariableValue("secret123");
        sourceVar.setSecret(true);
        sourceVar.setEnabled(true);
        sourceVar.setScope("ENVIRONMENT");
        sourceVar.setEnvironment(environment);

        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.existsByTeamIdAndName(teamId, "Dev Copy")).thenReturn(false);
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> {
            Environment e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setCreatedAt(Instant.now());
            e.setUpdatedAt(Instant.now());
            return e;
        });
        when(environmentVariableRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(sourceVar));
        when(environmentVariableRepository.saveAll(any())).thenAnswer(inv -> {
            List<EnvironmentVariable> vars = inv.getArgument(0);
            vars.forEach(v -> v.setId(UUID.randomUUID()));
            return vars;
        });

        EnvironmentResponse response = environmentService.cloneEnvironment(environmentId, teamId, userId, request);

        assertThat(response.variableCount()).isEqualTo(1);
        verify(environmentVariableRepository).saveAll(any());
    }

    @Test
    void cloneEnvironment_duplicateName_throws() {
        CloneEnvironmentRequest request = new CloneEnvironmentRequest("Development");

        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.existsByTeamIdAndName(teamId, "Development")).thenReturn(true);

        assertThatThrownBy(() -> environmentService.cloneEnvironment(environmentId, teamId, userId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getEnvironmentVariables_masksSecrets() {
        EnvironmentVariable secretVar = new EnvironmentVariable();
        secretVar.setId(UUID.randomUUID());
        secretVar.setVariableKey("SECRET_KEY");
        secretVar.setVariableValue("realValue");
        secretVar.setSecret(true);
        secretVar.setEnabled(true);
        secretVar.setScope("ENVIRONMENT");

        EnvironmentVariable plainVar = new EnvironmentVariable();
        plainVar.setId(UUID.randomUUID());
        plainVar.setVariableKey("BASE_URL");
        plainVar.setVariableValue("https://api.example.com");
        plainVar.setSecret(false);
        plainVar.setEnabled(true);
        plainVar.setScope("ENVIRONMENT");

        EnvironmentVariableResponse secretResp = new EnvironmentVariableResponse(
                secretVar.getId(), "SECRET_KEY", "realValue", true, true, "ENVIRONMENT");
        EnvironmentVariableResponse plainResp = new EnvironmentVariableResponse(
                plainVar.getId(), "BASE_URL", "https://api.example.com", false, true, "ENVIRONMENT");

        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentVariableRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(secretVar, plainVar));
        when(environmentVariableMapper.toResponse(secretVar)).thenReturn(secretResp);
        when(environmentVariableMapper.toResponse(plainVar)).thenReturn(plainResp);

        List<EnvironmentVariableResponse> responses = environmentService.getEnvironmentVariables(environmentId, teamId);

        assertThat(responses).hasSize(2);
        EnvironmentVariableResponse maskedSecret = responses.stream()
                .filter(r -> r.variableKey().equals("SECRET_KEY"))
                .findFirst().orElseThrow();
        assertThat(maskedSecret.variableValue()).isEqualTo("••••••••");

        EnvironmentVariableResponse plain = responses.stream()
                .filter(r -> r.variableKey().equals("BASE_URL"))
                .findFirst().orElseThrow();
        assertThat(plain.variableValue()).isEqualTo("https://api.example.com");
    }

    @Test
    void saveEnvironmentVariables_replacesAll() {
        SaveEnvironmentVariablesRequest request = new SaveEnvironmentVariablesRequest(List.of(
                new SaveEnvironmentVariablesRequest.VariableEntry("API_URL", "https://api.dev.com", false, true),
                new SaveEnvironmentVariablesRequest.VariableEntry("API_KEY", "key123", true, true)
        ));

        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentVariableRepository.saveAll(any())).thenAnswer(inv -> {
            List<EnvironmentVariable> vars = inv.getArgument(0);
            vars.forEach(v -> {
                v.setId(UUID.randomUUID());
            });
            return vars;
        });
        when(environmentVariableMapper.toResponse(any(EnvironmentVariable.class))).thenAnswer(inv -> {
            EnvironmentVariable v = inv.getArgument(0);
            return new EnvironmentVariableResponse(
                    v.getId(), v.getVariableKey(), v.getVariableValue(),
                    v.isSecret(), v.isEnabled(), v.getScope());
        });

        List<EnvironmentVariableResponse> responses = environmentService.saveEnvironmentVariables(environmentId, teamId, request);

        assertThat(responses).hasSize(2);
        verify(environmentVariableRepository).deleteByEnvironmentId(environmentId);
        verify(environmentVariableRepository).flush();
        verify(environmentVariableRepository).saveAll(any());

        // Verify secret is masked in response
        EnvironmentVariableResponse secretResp = responses.stream()
                .filter(r -> r.variableKey().equals("API_KEY"))
                .findFirst().orElseThrow();
        assertThat(secretResp.variableValue()).isEqualTo("••••••••");
    }
}
