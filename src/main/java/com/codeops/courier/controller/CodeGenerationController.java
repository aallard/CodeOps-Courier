package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import com.codeops.courier.dto.request.GenerateCodeRequest;
import com.codeops.courier.dto.response.CodeSnippetResponse;
import com.codeops.courier.entity.enums.CodeLanguage;
import com.codeops.courier.service.CodeGenerationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for generating code snippets from API requests.
 * Supports 12 languages with built-in generators and custom templates.
 * All endpoints require authentication and the ADMIN role.
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/codegen")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Code Generation", description = "Generate code snippets from API requests")
public class CodeGenerationController {

    private final CodeGenerationService codeGenerationService;

    /**
     * Generates a code snippet for a stored request in the specified language.
     *
     * @param teamId  the team ID from the request header
     * @param request the code generation request with request ID, language, and optional environment
     * @return the generated code snippet
     */
    @PostMapping("/generate")
    public CodeSnippetResponse generate(@RequestHeader("X-Team-ID") UUID teamId,
                                         @Valid @RequestBody GenerateCodeRequest request) {
        log.info("Generating {} code for request {} in team {}", request.language(), request.requestId(), teamId);
        return codeGenerationService.generateCode(request, teamId);
    }

    /**
     * Generates code snippets for a stored request in all available languages.
     * The {@code language} field in the request body is ignored; all languages are generated.
     *
     * @param teamId  the team ID from the request header
     * @param request the code generation request with request ID and optional environment
     * @return list of code snippets, one per supported language
     */
    @PostMapping("/generate/all")
    public List<CodeSnippetResponse> generateAll(@RequestHeader("X-Team-ID") UUID teamId,
                                                   @RequestBody GenerateCodeRequest request) {
        log.info("Generating all languages for request {} in team {}", request.requestId(), teamId);
        return Arrays.stream(CodeLanguage.values())
                .map(lang -> codeGenerationService.generateCode(
                        new GenerateCodeRequest(request.requestId(), lang, request.environmentId()), teamId))
                .toList();
    }

    /**
     * Returns the list of available code generation languages with display names
     * and file extensions.
     *
     * @return list of available language descriptors
     */
    @GetMapping("/languages")
    public List<CodeSnippetResponse> getLanguages() {
        return codeGenerationService.getAvailableLanguages();
    }
}
