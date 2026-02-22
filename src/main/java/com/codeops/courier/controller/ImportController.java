package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.ImportCollectionRequest;
import com.codeops.courier.dto.response.ImportResultResponse;
import com.codeops.courier.security.SecurityUtils;
import com.codeops.courier.service.ImportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for importing API collections from external formats.
 * Supports Postman Collection v2.1 JSON, OpenAPI 3.x YAML/JSON, and cURL commands.
 * All endpoints require authentication and the ADMIN role.
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/import")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Import", description = "Import collections from Postman, OpenAPI, and cURL formats")
public class ImportController {

    private final ImportService importService;

    /**
     * Imports a collection from Postman Collection v2.1 JSON format.
     *
     * @param teamId  the team ID from the request header
     * @param request the import request containing the Postman JSON content
     * @return the import result with collection ID and counts
     */
    @PostMapping("/postman")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResultResponse importPostman(@RequestHeader("X-Team-ID") UUID teamId,
                                               @Valid @RequestBody ImportCollectionRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Importing Postman collection for team {}", teamId);
        return importService.importCollection(teamId, userId,
                new ImportCollectionRequest("postman", request.content()));
    }

    /**
     * Imports a collection from OpenAPI 3.x specification in YAML or JSON format.
     *
     * @param teamId  the team ID from the request header
     * @param request the import request containing the OpenAPI content
     * @return the import result with collection ID and counts
     */
    @PostMapping("/openapi")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResultResponse importOpenApi(@RequestHeader("X-Team-ID") UUID teamId,
                                               @Valid @RequestBody ImportCollectionRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Importing OpenAPI collection for team {}", teamId);
        return importService.importCollection(teamId, userId,
                new ImportCollectionRequest("openapi", request.content()));
    }

    /**
     * Imports a request from a cURL command string.
     *
     * @param teamId  the team ID from the request header
     * @param request the import request containing the cURL command
     * @return the import result with collection ID and counts
     */
    @PostMapping("/curl")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResultResponse importCurl(@RequestHeader("X-Team-ID") UUID teamId,
                                            @Valid @RequestBody ImportCollectionRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.info("Importing cURL command for team {}", teamId);
        return importService.importCollection(teamId, userId,
                new ImportCollectionRequest("curl", request.content()));
    }
}
