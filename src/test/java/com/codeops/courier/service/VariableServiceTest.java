package com.codeops.courier.service;

import com.codeops.courier.dto.mapper.GlobalVariableMapper;
import com.codeops.courier.dto.request.BatchSaveGlobalVariablesRequest;
import com.codeops.courier.dto.request.SaveGlobalVariableRequest;
import com.codeops.courier.dto.response.GlobalVariableResponse;
import com.codeops.courier.dto.response.RequestHeaderResponse;
import com.codeops.courier.entity.Environment;
import com.codeops.courier.entity.EnvironmentVariable;
import com.codeops.courier.entity.GlobalVariable;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.repository.EnvironmentVariableRepository;
import com.codeops.courier.repository.GlobalVariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for VariableService covering variable resolution engine,
 * scope hierarchy, global variable CRUD, and secret handling.
 */
@ExtendWith(MockitoExtension.class)
class VariableServiceTest {

    @Mock
    private GlobalVariableRepository globalVariableRepository;

    @Mock
    private EnvironmentVariableRepository environmentVariableRepository;

    @Mock
    private GlobalVariableMapper globalVariableMapper;

    @InjectMocks
    private VariableService variableService;

    private UUID teamId;
    private UUID collectionId;
    private UUID environmentId;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        collectionId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
    }

    // ─── resolveVariables ───

    @Test
    void resolveVariables_simpleSubstitution() {
        GlobalVariable gv = createGlobalVariable("baseUrl", "https://api.example.com");
        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of(gv));

        String result = variableService.resolveVariables("{{baseUrl}}/users", teamId, null, null, null);

        assertThat(result).isEqualTo("https://api.example.com/users");
    }

    @Test
    void resolveVariables_multipleVariables() {
        GlobalVariable host = createGlobalVariable("host", "api.example.com");
        GlobalVariable port = createGlobalVariable("port", "8080");
        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of(host, port));

        String result = variableService.resolveVariables("https://{{host}}:{{port}}/api", teamId, null, null, null);

        assertThat(result).isEqualTo("https://api.example.com:8080/api");
    }

    @Test
    void resolveVariables_unresolvedLeftAsIs() {
        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of());

        String result = variableService.resolveVariables("{{unknown}}/path", teamId, null, null, null);

        assertThat(result).isEqualTo("{{unknown}}/path");
    }

    @Test
    void resolveVariables_nullInput_returnsNull() {
        String result = variableService.resolveVariables(null, teamId, null, null, null);

        assertThat(result).isNull();
    }

    @Test
    void resolveVariables_emptyInput_returnsEmpty() {
        String result = variableService.resolveVariables("", teamId, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void resolveVariables_noPlaceholders_returnsUnchanged() {
        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of());

        String result = variableService.resolveVariables("https://api.example.com/users", teamId, null, null, null);

        assertThat(result).isEqualTo("https://api.example.com/users");
    }

    // ─── buildVariableMap ───

    @Test
    void buildVariableMap_globalOnly() {
        GlobalVariable gv = createGlobalVariable("apiKey", "global-key");
        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of(gv));

        Map<String, String> map = variableService.buildVariableMap(teamId, null, null, null);

        assertThat(map).containsEntry("apiKey", "global-key");
        assertThat(map).hasSize(1);
    }

    @Test
    void buildVariableMap_environmentOverridesGlobal() {
        GlobalVariable gv = createGlobalVariable("apiKey", "global-key");
        EnvironmentVariable ev = createEnvVariable("apiKey", "env-key");

        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of(gv));
        when(environmentVariableRepository.findByEnvironmentIdAndIsEnabledTrue(environmentId)).thenReturn(List.of(ev));

        Map<String, String> map = variableService.buildVariableMap(teamId, null, environmentId, null);

        assertThat(map).containsEntry("apiKey", "env-key");
    }

    @Test
    void buildVariableMap_collectionOverridesGlobal() {
        GlobalVariable gv = createGlobalVariable("apiKey", "global-key");
        EnvironmentVariable cv = createEnvVariable("apiKey", "collection-key");

        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of(gv));
        when(environmentVariableRepository.findByCollectionIdAndIsEnabledTrue(collectionId)).thenReturn(List.of(cv));

        Map<String, String> map = variableService.buildVariableMap(teamId, collectionId, null, null);

        assertThat(map).containsEntry("apiKey", "collection-key");
    }

    @Test
    void buildVariableMap_localOverridesAll() {
        GlobalVariable gv = createGlobalVariable("apiKey", "global-key");
        EnvironmentVariable ev = createEnvVariable("apiKey", "env-key");

        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of(gv));
        when(environmentVariableRepository.findByEnvironmentIdAndIsEnabledTrue(environmentId)).thenReturn(List.of(ev));

        Map<String, String> localVars = Map.of("apiKey", "local-key");
        Map<String, String> map = variableService.buildVariableMap(teamId, null, environmentId, localVars);

        assertThat(map).containsEntry("apiKey", "local-key");
    }

    @Test
    void buildVariableMap_fullHierarchy() {
        GlobalVariable gGlobal = createGlobalVariable("globalOnly", "g-val");
        GlobalVariable gOverridden = createGlobalVariable("shared", "global-shared");
        EnvironmentVariable cVar = createEnvVariable("collectionOnly", "c-val");
        EnvironmentVariable cOverride = createEnvVariable("shared", "collection-shared");
        EnvironmentVariable eVar = createEnvVariable("envOnly", "e-val");
        EnvironmentVariable eOverride = createEnvVariable("shared", "env-shared");

        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of(gGlobal, gOverridden));
        when(environmentVariableRepository.findByCollectionIdAndIsEnabledTrue(collectionId)).thenReturn(List.of(cVar, cOverride));
        when(environmentVariableRepository.findByEnvironmentIdAndIsEnabledTrue(environmentId)).thenReturn(List.of(eVar, eOverride));

        Map<String, String> localVars = Map.of("localOnly", "l-val");
        Map<String, String> map = variableService.buildVariableMap(teamId, collectionId, environmentId, localVars);

        assertThat(map).containsEntry("globalOnly", "g-val");
        assertThat(map).containsEntry("collectionOnly", "c-val");
        assertThat(map).containsEntry("envOnly", "e-val");
        assertThat(map).containsEntry("localOnly", "l-val");
        assertThat(map).containsEntry("shared", "env-shared"); // env overrides collection which overrides global
    }

    @Test
    void buildVariableMap_disabledVariablesExcluded() {
        // findByTeamIdAndIsEnabledTrue already filters — return empty to prove disabled are excluded
        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of());

        Map<String, String> map = variableService.buildVariableMap(teamId, null, null, null);

        assertThat(map).isEmpty();
    }

    @Test
    void buildVariableMap_secretVariablesIncluded() {
        GlobalVariable secretVar = createGlobalVariable("apiSecret", "super-secret-value");
        secretVar.setSecret(true);

        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of(secretVar));

        Map<String, String> map = variableService.buildVariableMap(teamId, null, null, null);

        assertThat(map).containsEntry("apiSecret", "super-secret-value");
    }

    // ─── Resolve convenience methods ───

    @Test
    void resolveUrl_withVariables() {
        GlobalVariable gv = createGlobalVariable("baseUrl", "https://api.example.com");
        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of(gv));

        String result = variableService.resolveUrl("{{baseUrl}}/users?page=1", teamId, null, null, null);

        assertThat(result).isEqualTo("https://api.example.com/users?page=1");
    }

    @Test
    void resolveHeaders_withVariables() {
        GlobalVariable gv = createGlobalVariable("token", "Bearer abc123");
        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of(gv));

        List<RequestHeaderResponse> headers = List.of(
                new RequestHeaderResponse(UUID.randomUUID(), "Authorization", "{{token}}", null, true),
                new RequestHeaderResponse(UUID.randomUUID(), "X-Disabled", "value", null, false)
        );

        Map<String, String> resolved = variableService.resolveHeaders(headers, teamId, null, null, null);

        assertThat(resolved).containsEntry("Authorization", "Bearer abc123");
        assertThat(resolved).doesNotContainKey("X-Disabled"); // disabled headers excluded
        assertThat(resolved).hasSize(1);
    }

    @Test
    void resolveBody_withVariables() {
        GlobalVariable gv = createGlobalVariable("userId", "12345");
        when(globalVariableRepository.findByTeamIdAndIsEnabledTrue(teamId)).thenReturn(List.of(gv));

        String result = variableService.resolveBody("{\"user\": \"{{userId}}\"}", teamId, null, null, null);

        assertThat(result).isEqualTo("{\"user\": \"12345\"}");
    }

    // ─── Global Variable CRUD ───

    @Test
    void getGlobalVariables_success() {
        GlobalVariable gv = createGlobalVariable("baseUrl", "https://api.example.com");
        gv.setId(UUID.randomUUID());
        gv.setTeamId(teamId);
        gv.setCreatedAt(Instant.now());
        gv.setUpdatedAt(Instant.now());

        GlobalVariableResponse resp = new GlobalVariableResponse(
                gv.getId(), teamId, "baseUrl", "https://api.example.com", false, true,
                gv.getCreatedAt(), gv.getUpdatedAt());

        when(globalVariableRepository.findByTeamId(teamId)).thenReturn(List.of(gv));
        when(globalVariableMapper.toResponse(gv)).thenReturn(resp);

        List<GlobalVariableResponse> responses = variableService.getGlobalVariables(teamId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).variableValue()).isEqualTo("https://api.example.com");
    }

    @Test
    void getGlobalVariables_masksSecrets() {
        GlobalVariable secretGv = createGlobalVariable("apiKey", "real-secret");
        secretGv.setId(UUID.randomUUID());
        secretGv.setTeamId(teamId);
        secretGv.setSecret(true);
        secretGv.setCreatedAt(Instant.now());
        secretGv.setUpdatedAt(Instant.now());

        GlobalVariableResponse resp = new GlobalVariableResponse(
                secretGv.getId(), teamId, "apiKey", "real-secret", true, true,
                secretGv.getCreatedAt(), secretGv.getUpdatedAt());

        when(globalVariableRepository.findByTeamId(teamId)).thenReturn(List.of(secretGv));
        when(globalVariableMapper.toResponse(secretGv)).thenReturn(resp);

        List<GlobalVariableResponse> responses = variableService.getGlobalVariables(teamId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).variableValue()).isEqualTo("••••••••");
    }

    @Test
    void saveGlobalVariable_createNew() {
        SaveGlobalVariableRequest request = new SaveGlobalVariableRequest("newKey", "newValue", false, true);

        when(globalVariableRepository.findByTeamIdAndVariableKey(teamId, "newKey")).thenReturn(Optional.empty());
        when(globalVariableRepository.save(any(GlobalVariable.class))).thenAnswer(inv -> {
            GlobalVariable v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            v.setCreatedAt(Instant.now());
            v.setUpdatedAt(Instant.now());
            return v;
        });
        when(globalVariableMapper.toResponse(any(GlobalVariable.class))).thenAnswer(inv -> {
            GlobalVariable v = inv.getArgument(0);
            return new GlobalVariableResponse(
                    v.getId(), v.getTeamId(), v.getVariableKey(), v.getVariableValue(),
                    v.isSecret(), v.isEnabled(), v.getCreatedAt(), v.getUpdatedAt());
        });

        GlobalVariableResponse response = variableService.saveGlobalVariable(teamId, request);

        assertThat(response.variableKey()).isEqualTo("newKey");
        assertThat(response.variableValue()).isEqualTo("newValue");
        verify(globalVariableRepository).save(any(GlobalVariable.class));
    }

    @Test
    void saveGlobalVariable_updateExisting() {
        GlobalVariable existing = createGlobalVariable("existingKey", "oldValue");
        existing.setId(UUID.randomUUID());
        existing.setTeamId(teamId);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        SaveGlobalVariableRequest request = new SaveGlobalVariableRequest("existingKey", "updatedValue", false, true);

        when(globalVariableRepository.findByTeamIdAndVariableKey(teamId, "existingKey")).thenReturn(Optional.of(existing));
        when(globalVariableRepository.save(any(GlobalVariable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(globalVariableMapper.toResponse(any(GlobalVariable.class))).thenAnswer(inv -> {
            GlobalVariable v = inv.getArgument(0);
            return new GlobalVariableResponse(
                    v.getId(), v.getTeamId(), v.getVariableKey(), v.getVariableValue(),
                    v.isSecret(), v.isEnabled(), v.getCreatedAt(), v.getUpdatedAt());
        });

        GlobalVariableResponse response = variableService.saveGlobalVariable(teamId, request);

        assertThat(response.variableValue()).isEqualTo("updatedValue");
        assertThat(existing.getVariableValue()).isEqualTo("updatedValue");
    }

    @Test
    void batchSaveGlobalVariables_success() {
        BatchSaveGlobalVariablesRequest request = new BatchSaveGlobalVariablesRequest(List.of(
                new SaveGlobalVariableRequest("key1", "val1", false, true),
                new SaveGlobalVariableRequest("key2", "val2", false, true)
        ));

        when(globalVariableRepository.findByTeamIdAndVariableKey(any(UUID.class), any(String.class))).thenReturn(Optional.empty());
        when(globalVariableRepository.save(any(GlobalVariable.class))).thenAnswer(inv -> {
            GlobalVariable v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            v.setCreatedAt(Instant.now());
            v.setUpdatedAt(Instant.now());
            return v;
        });
        when(globalVariableMapper.toResponse(any(GlobalVariable.class))).thenAnswer(inv -> {
            GlobalVariable v = inv.getArgument(0);
            return new GlobalVariableResponse(
                    v.getId(), v.getTeamId(), v.getVariableKey(), v.getVariableValue(),
                    v.isSecret(), v.isEnabled(), v.getCreatedAt(), v.getUpdatedAt());
        });

        List<GlobalVariableResponse> responses = variableService.batchSaveGlobalVariables(teamId, request);

        assertThat(responses).hasSize(2);
    }

    @Test
    void deleteGlobalVariable_success() {
        UUID variableId = UUID.randomUUID();
        GlobalVariable variable = createGlobalVariable("toDelete", "value");
        variable.setId(variableId);
        variable.setTeamId(teamId);

        when(globalVariableRepository.findById(variableId)).thenReturn(Optional.of(variable));

        variableService.deleteGlobalVariable(variableId, teamId);

        verify(globalVariableRepository).delete(variable);
    }

    @Test
    void deleteGlobalVariable_notFound_throws() {
        UUID variableId = UUID.randomUUID();
        when(globalVariableRepository.findById(variableId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> variableService.deleteGlobalVariable(variableId, teamId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(variableId.toString());
    }

    @Test
    void deleteGlobalVariable_wrongTeam_throws() {
        UUID variableId = UUID.randomUUID();
        GlobalVariable variable = createGlobalVariable("key", "value");
        variable.setId(variableId);
        variable.setTeamId(UUID.randomUUID()); // different team

        when(globalVariableRepository.findById(variableId)).thenReturn(Optional.of(variable));

        assertThatThrownBy(() -> variableService.deleteGlobalVariable(variableId, teamId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getSecretValue_returnsUnmasked() {
        UUID variableId = UUID.randomUUID();
        GlobalVariable secretVar = createGlobalVariable("apiSecret", "the-real-secret");
        secretVar.setId(variableId);
        secretVar.setTeamId(teamId);
        secretVar.setSecret(true);

        when(globalVariableRepository.findById(variableId)).thenReturn(Optional.of(secretVar));

        String value = variableService.getSecretValue(variableId, teamId);

        assertThat(value).isEqualTo("the-real-secret");
    }

    // ─── Helpers ───

    private GlobalVariable createGlobalVariable(String key, String value) {
        GlobalVariable gv = new GlobalVariable();
        gv.setVariableKey(key);
        gv.setVariableValue(value);
        gv.setSecret(false);
        gv.setEnabled(true);
        return gv;
    }

    private EnvironmentVariable createEnvVariable(String key, String value) {
        EnvironmentVariable ev = new EnvironmentVariable();
        ev.setVariableKey(key);
        ev.setVariableValue(value);
        ev.setSecret(false);
        ev.setEnabled(true);
        ev.setScope("ENVIRONMENT");
        return ev;
    }
}
