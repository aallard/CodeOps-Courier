package com.codeops.courier.config;

import com.codeops.courier.entity.*;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seeds sample data for development and demo purposes.
 * Creates a sample collection with folders, requests, an environment, and global variables.
 * Only runs in the {@code dev} profile and is idempotent — skips if data already exists.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private static final UUID SEED_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SEED_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final CollectionRepository collectionRepository;
    private final FolderRepository folderRepository;
    private final RequestRepository requestRepository;
    private final EnvironmentRepository environmentRepository;
    private final EnvironmentVariableRepository environmentVariableRepository;
    private final GlobalVariableRepository globalVariableRepository;

    /**
     * Seeds development data on application startup. Checks if data already exists
     * before creating to ensure idempotency.
     *
     * @param args command-line arguments (unused)
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (collectionRepository.countByTeamId(SEED_TEAM_ID) > 0) {
            log.info("DataSeeder: Data already exists for seed team, skipping.");
            return;
        }

        Collection collection = seedCollection();
        int folderCount = 0;
        int requestCount = 0;

        // Authentication folder
        Folder authFolder = seedFolder(collection, null, "Authentication", "Auth endpoints", 0);
        folderCount++;
        seedRequest(authFolder, "Login", HttpMethod.POST, "{{baseUrl}}/api/v1/auth/login", 0);
        seedRequest(authFolder, "Register", HttpMethod.POST, "{{baseUrl}}/api/v1/auth/register", 1);
        seedRequest(authFolder, "Refresh Token", HttpMethod.POST, "{{baseUrl}}/api/v1/auth/refresh", 2);
        requestCount += 3;

        // Teams folder
        Folder teamsFolder = seedFolder(collection, null, "Teams", "Team management endpoints", 1);
        folderCount++;
        seedRequest(teamsFolder, "List Teams", HttpMethod.GET, "{{baseUrl}}/api/v1/teams", 0);
        seedRequest(teamsFolder, "Create Team", HttpMethod.POST, "{{baseUrl}}/api/v1/teams", 1);
        seedRequest(teamsFolder, "Get Team", HttpMethod.GET, "{{baseUrl}}/api/v1/teams/{{teamId}}", 2);
        requestCount += 3;

        // Health Checks folder
        Folder healthFolder = seedFolder(collection, null, "Health Checks", "Service health endpoints", 2);
        folderCount++;
        seedRequest(healthFolder, "Courier Health", HttpMethod.GET, "{{baseUrl}}/api/v1/courier/health", 0);
        seedRequest(healthFolder, "Registry Health", HttpMethod.GET, "{{baseUrl}}/api/v1/registry/health", 1);
        requestCount += 2;

        // Environment
        seedEnvironment();

        // Global variables
        seedGlobalVariables();

        log.info("DataSeeder: Seeded sample collection, {} folders, {} requests, 1 environment",
                folderCount, requestCount);
    }

    private Collection seedCollection() {
        Collection collection = Collection.builder()
                .teamId(SEED_TEAM_ID)
                .name("CodeOps API")
                .description("Sample API collection for CodeOps platform testing")
                .authType(AuthType.NO_AUTH)
                .isShared(false)
                .createdBy(SEED_USER_ID)
                .build();
        return collectionRepository.save(collection);
    }

    private Folder seedFolder(Collection collection, Folder parent, String name, String description, int sortOrder) {
        Folder folder = Folder.builder()
                .collection(collection)
                .parentFolder(parent)
                .name(name)
                .description(description)
                .sortOrder(sortOrder)
                .authType(AuthType.INHERIT_FROM_PARENT)
                .build();
        return folderRepository.save(folder);
    }

    private void seedRequest(Folder folder, String name, HttpMethod method, String url, int sortOrder) {
        Request request = Request.builder()
                .folder(folder)
                .name(name)
                .method(method)
                .url(url)
                .sortOrder(sortOrder)
                .build();
        requestRepository.save(request);
    }

    private void seedEnvironment() {
        Environment env = Environment.builder()
                .teamId(SEED_TEAM_ID)
                .name("Local Development")
                .description("Local development environment")
                .isActive(true)
                .createdBy(SEED_USER_ID)
                .build();
        env = environmentRepository.save(env);

        environmentVariableRepository.save(EnvironmentVariable.builder()
                .environment(env)
                .variableKey("baseUrl")
                .variableValue("http://localhost:8095")
                .isSecret(false)
                .isEnabled(true)
                .scope("ENVIRONMENT")
                .build());

        environmentVariableRepository.save(EnvironmentVariable.builder()
                .environment(env)
                .variableKey("token")
                .variableValue("")
                .isSecret(true)
                .isEnabled(true)
                .scope("ENVIRONMENT")
                .build());
    }

    private void seedGlobalVariables() {
        globalVariableRepository.save(GlobalVariable.builder()
                .teamId(SEED_TEAM_ID)
                .variableKey("baseUrl")
                .variableValue("http://localhost:8095")
                .isSecret(false)
                .isEnabled(true)
                .build());

        globalVariableRepository.save(GlobalVariable.builder()
                .teamId(SEED_TEAM_ID)
                .variableKey("authToken")
                .variableValue("")
                .isSecret(false)
                .isEnabled(true)
                .build());
    }
}
