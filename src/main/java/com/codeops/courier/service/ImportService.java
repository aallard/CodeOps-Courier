package com.codeops.courier.service;

import com.codeops.courier.dto.request.ImportCollectionRequest;
import com.codeops.courier.dto.response.ImportResultResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.CollectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates collection import from external formats: Postman v2.1 JSON,
 * OpenAPI 3.x (JSON/YAML), and cURL commands. Routes to the appropriate importer
 * based on the format field, supports auto-detection, and handles persistence
 * of the fully-assembled Collection entity graph via cascading saves.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ImportService {

    private final CollectionRepository collectionRepository;
    private final PostmanImporter postmanImporter;
    private final OpenApiImporter openApiImporter;
    private final CurlImporter curlImporter;

    /**
     * Imports a collection from an external format into the specified team.
     * Supports three formats: "postman", "openapi", and "curl". Also supports
     * "auto" for auto-detection based on content analysis.
     *
     * @param teamId    the team ID to import into
     * @param userId    the user performing the import
     * @param request   the import request with format and content
     * @return the import result with collection ID, counts, and warnings
     * @throws ValidationException if the format is unsupported, content is invalid,
     *                             or a collection with the same name already exists
     */
    public ImportResultResponse importCollection(UUID teamId, UUID userId, ImportCollectionRequest request) {
        String format = resolveFormat(request.format(), request.content());

        return switch (format) {
            case "postman" -> importPostman(teamId, userId, request.content());
            case "openapi", "openapi-yaml" -> importOpenApi(teamId, userId, request.content(), "openapi-yaml".equals(format));
            case "curl" -> importCurl(teamId, userId, request.content());
            default -> throw new ValidationException("Unsupported import format: " + request.format());
        };
    }

    /**
     * Imports a Postman v2.1 collection JSON into the team.
     *
     * @param teamId  the team ID
     * @param userId  the importing user ID
     * @param content the Postman JSON content
     * @return the import result
     */
    private ImportResultResponse importPostman(UUID teamId, UUID userId, String content) {
        PostmanImporter.PostmanImportResult result = postmanImporter.parse(content, teamId, userId);
        Collection collection = result.collection();
        List<String> warnings = new ArrayList<>(result.warnings());

        // Ensure unique name
        collection.setName(ensureUniqueName(teamId, collection.getName()));

        Collection saved = collectionRepository.save(collection);
        log.info("Imported Postman collection '{}' (ID: {}) for team {}",
                saved.getName(), saved.getId(), teamId);

        return new ImportResultResponse(
                saved.getId(),
                saved.getName(),
                result.foldersImported(),
                result.requestsImported(),
                result.environmentsImported(),
                warnings
        );
    }

    /**
     * Imports an OpenAPI 3.x specification (JSON or YAML) into the team.
     *
     * @param teamId  the team ID
     * @param userId  the importing user ID
     * @param content the OpenAPI spec content
     * @param isYaml  true if the content is YAML
     * @return the import result
     */
    private ImportResultResponse importOpenApi(UUID teamId, UUID userId, String content, boolean isYaml) {
        OpenApiImporter.OpenApiImportResult result = openApiImporter.parse(content, isYaml, teamId, userId);
        Collection collection = result.collection();
        List<String> warnings = new ArrayList<>(result.warnings());

        collection.setName(ensureUniqueName(teamId, collection.getName()));

        Collection saved = collectionRepository.save(collection);
        log.info("Imported OpenAPI spec '{}' (ID: {}) for team {}",
                saved.getName(), saved.getId(), teamId);

        return new ImportResultResponse(
                saved.getId(),
                saved.getName(),
                result.foldersImported(),
                result.requestsImported(),
                result.environmentsImported(),
                warnings
        );
    }

    /**
     * Imports a cURL command into the team as a new collection with a single
     * folder and request.
     *
     * @param teamId  the team ID
     * @param userId  the importing user ID
     * @param content the cURL command string
     * @return the import result
     */
    private ImportResultResponse importCurl(UUID teamId, UUID userId, String content) {
        Collection collection = new Collection();
        collection.setTeamId(teamId);
        collection.setCreatedBy(userId);
        collection.setName(ensureUniqueName(teamId, "cURL Import"));
        collection.setDescription("Imported from cURL command");
        collection.setFolders(new ArrayList<>());
        collection.setVariables(new ArrayList<>());

        Folder folder = new Folder();
        folder.setName("Requests");
        folder.setDescription("Imported cURL requests");
        folder.setSortOrder(0);
        folder.setCollection(collection);
        folder.setSubFolders(new ArrayList<>());
        folder.setRequests(new ArrayList<>());
        collection.getFolders().add(folder);

        com.codeops.courier.entity.Request request = curlImporter.parseCurl(content, folder, 0);
        folder.getRequests().add(request);

        Collection saved = collectionRepository.save(collection);
        log.info("Imported cURL command as collection '{}' (ID: {}) for team {}",
                saved.getName(), saved.getId(), teamId);

        return new ImportResultResponse(
                saved.getId(),
                saved.getName(),
                1,
                1,
                0,
                List.of()
        );
    }

    /**
     * Resolves the import format string, with support for "auto" detection.
     * Auto-detection checks for Postman-specific fields, OpenAPI version field,
     * and cURL command prefix.
     *
     * @param format  the user-specified format
     * @param content the import content for auto-detection
     * @return the resolved format string (lowercase)
     */
    String resolveFormat(String format, String content) {
        String normalized = format.trim().toLowerCase();

        if (!"auto".equals(normalized)) {
            // Normalize openapi variants
            if ("openapi-json".equals(normalized)) return "openapi";
            if ("openapi-yaml".equals(normalized) || "openapi_yaml".equals(normalized)) return "openapi-yaml";
            return normalized;
        }

        // Auto-detect
        String trimmed = content.trim();
        if (trimmed.startsWith("curl ") || trimmed.startsWith("curl\t")) {
            return "curl";
        }

        // Check for JSON structure markers
        if (trimmed.startsWith("{")) {
            if (trimmed.contains("\"info\"") && trimmed.contains("\"item\"")) {
                return "postman";
            }
            if (trimmed.contains("\"openapi\"") && trimmed.contains("\"3.")) {
                return "openapi";
            }
            // Default JSON to Postman
            return "postman";
        }

        // Check for YAML OpenAPI markers
        if (trimmed.startsWith("openapi:") || trimmed.contains("\nopenapi:")) {
            return "openapi-yaml";
        }

        throw new ValidationException("Unable to auto-detect import format. "
                + "Please specify one of: postman, openapi, openapi-yaml, curl");
    }

    /**
     * Ensures a collection name is unique within the team by appending a numeric
     * suffix if a collection with the same name already exists.
     *
     * @param teamId the team ID
     * @param name   the desired collection name
     * @return the unique collection name
     */
    private String ensureUniqueName(UUID teamId, String name) {
        if (!collectionRepository.existsByTeamIdAndName(teamId, name)) {
            return name;
        }
        int suffix = 2;
        while (collectionRepository.existsByTeamIdAndName(teamId, name + " (" + suffix + ")")) {
            suffix++;
        }
        return name + " (" + suffix + ")";
    }
}
