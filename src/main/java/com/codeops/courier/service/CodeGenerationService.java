package com.codeops.courier.service;

import com.codeops.courier.dto.request.GenerateCodeRequest;
import com.codeops.courier.dto.response.CodeSnippetResponse;
import com.codeops.courier.dto.response.CodeSnippetTemplateResponse;
import com.codeops.courier.dto.response.RequestAuthResponse;
import com.codeops.courier.dto.response.RequestBodyResponse;
import com.codeops.courier.dto.response.RequestHeaderResponse;
import com.codeops.courier.dto.response.RequestResponse;
import com.codeops.courier.entity.CodeSnippetTemplate;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.CodeLanguage;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.CodeSnippetTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for generating code snippets from stored API requests in 12 languages.
 * Supports a two-tier approach: built-in hardcoded generators for each language
 * with optional custom template overrides stored in the database.
 * Templates use Mustache-style {{placeholder}} substitution.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CodeGenerationService {

    private final CodeSnippetTemplateRepository templateRepository;
    private final RequestService requestService;
    private final VariableService variableService;

    /**
     * Language metadata containing display name, file extension, and MIME content type.
     *
     * @param displayName human-readable language name
     * @param fileExtension file extension without dot
     * @param contentType MIME content type for the generated code
     */
    record LanguageMeta(String displayName, String fileExtension, String contentType) {}

    /**
     * Resolved request data with all variables substituted and auth converted to headers.
     *
     * @param method HTTP method (uppercase)
     * @param url resolved URL with variables substituted
     * @param headers resolved headers including auth and content-type
     * @param body resolved request body (null for GET/HEAD or bodyless requests)
     * @param contentType content type derived from body type
     */
    record ResolvedRequest(String method, String url, Map<String, String> headers,
                           String body, String contentType) {}

    private static final Map<CodeLanguage, LanguageMeta> LANGUAGE_META = Map.ofEntries(
            Map.entry(CodeLanguage.CURL, new LanguageMeta("cURL", "sh", "text/x-shellscript")),
            Map.entry(CodeLanguage.PYTHON_REQUESTS, new LanguageMeta("Python - Requests", "py", "text/x-python")),
            Map.entry(CodeLanguage.JAVASCRIPT_FETCH, new LanguageMeta("JavaScript - Fetch", "js", "text/javascript")),
            Map.entry(CodeLanguage.JAVASCRIPT_AXIOS, new LanguageMeta("JavaScript - Axios", "js", "text/javascript")),
            Map.entry(CodeLanguage.JAVA_HTTP_CLIENT, new LanguageMeta("Java - HttpClient", "java", "text/x-java")),
            Map.entry(CodeLanguage.JAVA_OKHTTP, new LanguageMeta("Java - OkHttp", "java", "text/x-java")),
            Map.entry(CodeLanguage.CSHARP_HTTP_CLIENT, new LanguageMeta("C# - HttpClient", "cs", "text/x-csharp")),
            Map.entry(CodeLanguage.GO, new LanguageMeta("Go", "go", "text/x-go")),
            Map.entry(CodeLanguage.RUBY, new LanguageMeta("Ruby", "rb", "text/x-ruby")),
            Map.entry(CodeLanguage.PHP, new LanguageMeta("PHP", "php", "text/x-php")),
            Map.entry(CodeLanguage.SWIFT, new LanguageMeta("Swift", "swift", "text/x-swift")),
            Map.entry(CodeLanguage.KOTLIN, new LanguageMeta("Kotlin", "kt", "text/x-kotlin"))
    );

    // ─── Code Generation ───

    /**
     * Generates a code snippet for a stored request in the specified language.
     * Resolves variables using global and environment scopes, converts auth to headers,
     * and applies either a custom DB template or the built-in generator.
     *
     * @param request the code generation request with requestId, language, and optional environmentId
     * @param teamId  the team ID for access validation and variable resolution
     * @return the generated code snippet with language metadata
     * @throws NotFoundException if the request is not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public CodeSnippetResponse generateCode(GenerateCodeRequest request, UUID teamId) {
        RequestResponse reqData = requestService.getRequest(request.requestId(), teamId);
        ResolvedRequest resolved = resolveRequest(reqData, teamId, request.environmentId());

        Optional<CodeSnippetTemplate> customTemplate = templateRepository.findByLanguage(request.language());

        String code;
        if (customTemplate.isPresent()) {
            code = processTemplate(customTemplate.get().getTemplateContent(), resolved);
            log.info("Generated {} code using custom template for request {}",
                    request.language(), request.requestId());
        } else {
            code = generateBuiltIn(request.language(), resolved);
            log.info("Generated {} code using built-in generator for request {}",
                    request.language(), request.requestId());
        }

        LanguageMeta meta = LANGUAGE_META.get(request.language());
        String displayName = customTemplate.map(CodeSnippetTemplate::getDisplayName)
                .orElse(meta != null ? meta.displayName() : request.language().name());
        String fileExtension = customTemplate.map(CodeSnippetTemplate::getFileExtension)
                .orElse(meta != null ? meta.fileExtension() : "txt");
        String contentType = customTemplate.map(CodeSnippetTemplate::getContentType)
                .orElse(meta != null ? meta.contentType() : "text/plain");

        return new CodeSnippetResponse(request.language(), displayName, code, fileExtension, contentType);
    }

    /**
     * Returns metadata for all available code generation languages.
     * Custom templates override default metadata when present.
     *
     * @return list of language metadata (code field is null — metadata only)
     */
    @Transactional(readOnly = true)
    public List<CodeSnippetResponse> getAvailableLanguages() {
        Map<CodeLanguage, CodeSnippetTemplate> customTemplates = templateRepository
                .findAllByOrderByDisplayNameAsc().stream()
                .collect(Collectors.toMap(CodeSnippetTemplate::getLanguage, t -> t));

        List<CodeSnippetResponse> languages = new ArrayList<>();
        for (CodeLanguage lang : CodeLanguage.values()) {
            CodeSnippetTemplate custom = customTemplates.get(lang);
            LanguageMeta meta = LANGUAGE_META.get(lang);

            languages.add(new CodeSnippetResponse(
                    lang,
                    custom != null ? custom.getDisplayName() : (meta != null ? meta.displayName() : lang.name()),
                    null,
                    custom != null ? custom.getFileExtension() : (meta != null ? meta.fileExtension() : "txt"),
                    custom != null ? custom.getContentType() : (meta != null ? meta.contentType() : "text/plain")
            ));
        }
        return languages;
    }

    // ─── Template CRUD ───

    /**
     * Returns all custom code snippet templates ordered by display name.
     *
     * @return list of template responses
     */
    @Transactional(readOnly = true)
    public List<CodeSnippetTemplateResponse> getTemplates() {
        return templateRepository.findAllByOrderByDisplayNameAsc().stream()
                .map(this::toTemplateResponse)
                .toList();
    }

    /**
     * Returns a single custom code snippet template by ID.
     *
     * @param templateId the template ID
     * @return the template response
     * @throws NotFoundException if the template is not found
     */
    @Transactional(readOnly = true)
    public CodeSnippetTemplateResponse getTemplate(UUID templateId) {
        CodeSnippetTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Code snippet template not found: " + templateId));
        return toTemplateResponse(template);
    }

    /**
     * Saves a custom code snippet template (upsert by language).
     * If a template already exists for the language, it is updated.
     * Otherwise, a new template is created.
     *
     * @param language        the target language
     * @param displayName     human-readable display name
     * @param templateContent the template content with {{placeholder}} syntax
     * @param fileExtension   file extension without dot
     * @param contentType     MIME content type
     * @return the saved template response
     * @throws ValidationException if template content is blank
     */
    public CodeSnippetTemplateResponse saveTemplate(CodeLanguage language, String displayName,
                                                    String templateContent, String fileExtension,
                                                    String contentType) {
        if (templateContent == null || templateContent.isBlank()) {
            throw new ValidationException("Template content is required");
        }

        CodeSnippetTemplate template = templateRepository.findByLanguage(language)
                .orElseGet(() -> {
                    CodeSnippetTemplate t = new CodeSnippetTemplate();
                    t.setLanguage(language);
                    return t;
                });

        template.setDisplayName(displayName);
        template.setTemplateContent(templateContent);
        template.setFileExtension(fileExtension);
        template.setContentType(contentType);

        CodeSnippetTemplate saved = templateRepository.save(template);
        log.info("Saved code snippet template for language {}", language);
        return toTemplateResponse(saved);
    }

    /**
     * Deletes a custom code snippet template by ID.
     *
     * @param templateId the template ID
     * @throws NotFoundException if the template is not found
     */
    public void deleteTemplate(UUID templateId) {
        CodeSnippetTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Code snippet template not found: " + templateId));
        templateRepository.delete(template);
        log.info("Deleted code snippet template for language {} ({})", template.getLanguage(), templateId);
    }

    // ─── Request Resolution ───

    /**
     * Resolves a request response into a fully-substituted ResolvedRequest.
     * Resolves variables in URL, headers, and body. Converts auth to headers.
     * Adds Content-Type header from body type when not explicitly set.
     * Package-private for testing.
     *
     * @param reqData       the request response from RequestService
     * @param teamId        the team ID for variable resolution
     * @param environmentId the environment ID for variable resolution (nullable)
     * @return the resolved request with all variables substituted
     */
    ResolvedRequest resolveRequest(RequestResponse reqData, UUID teamId, UUID environmentId) {
        String url = variableService.resolveVariables(
                reqData.url(), teamId, null, environmentId, null);

        Map<String, String> headers = new LinkedHashMap<>();
        if (reqData.headers() != null) {
            for (RequestHeaderResponse h : reqData.headers()) {
                if (h.isEnabled()) {
                    String key = variableService.resolveVariables(
                            h.headerKey(), teamId, null, environmentId, null);
                    String value = variableService.resolveVariables(
                            h.headerValue(), teamId, null, environmentId, null);
                    headers.put(key, value);
                }
            }
        }

        String body = null;
        String contentType = null;
        boolean hasBody = reqData.method() != HttpMethod.GET && reqData.method() != HttpMethod.HEAD;

        if (hasBody && reqData.body() != null && reqData.body().bodyType() != BodyType.NONE) {
            body = extractBody(reqData.body());
            if (body != null) {
                body = variableService.resolveVariables(body, teamId, null, environmentId, null);
            }
            contentType = resolveContentType(reqData.body().bodyType());
        }

        if (contentType != null && !headerContainsKey(headers, "Content-Type")) {
            headers.put("Content-Type", contentType);
        }

        if (reqData.auth() != null && reqData.auth().authType() != null
                && reqData.auth().authType() != AuthType.NO_AUTH
                && reqData.auth().authType() != AuthType.INHERIT_FROM_PARENT) {
            resolveAuthToHeaders(reqData.auth(), headers, teamId, environmentId);
        }

        return new ResolvedRequest(reqData.method().name(), url, headers, body, contentType);
    }

    /**
     * Extracts the body content from a RequestBodyResponse based on body type.
     *
     * @param bodyResp the request body response
     * @return the extracted body string, or null for binary/none types
     */
    private String extractBody(RequestBodyResponse bodyResp) {
        return switch (bodyResp.bodyType()) {
            case FORM_DATA, X_WWW_FORM_URLENCODED -> bodyResp.formData();
            case GRAPHQL -> buildGraphqlBody(bodyResp);
            case BINARY, NONE -> null;
            default -> bodyResp.rawContent();
        };
    }

    /**
     * Builds a JSON body from GraphQL query and variables fields.
     * Falls back to rawContent if the query is not set.
     *
     * @param bodyResp the request body response containing GraphQL data
     * @return the constructed JSON body string
     */
    private String buildGraphqlBody(RequestBodyResponse bodyResp) {
        if (bodyResp.graphqlQuery() == null || bodyResp.graphqlQuery().isBlank()) {
            return bodyResp.rawContent();
        }
        StringBuilder sb = new StringBuilder("{\"query\":\"");
        sb.append(bodyResp.graphqlQuery()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", ""));
        sb.append("\"");
        if (bodyResp.graphqlVariables() != null && !bodyResp.graphqlVariables().isBlank()) {
            sb.append(",\"variables\":").append(bodyResp.graphqlVariables());
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Checks if a header key exists in the map (case-insensitive).
     *
     * @param headers the headers map
     * @param key     the header key to check
     * @return true if the key exists (case-insensitive)
     */
    private boolean headerContainsKey(Map<String, String> headers, String key) {
        return headers.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(key));
    }

    /**
     * Converts auth configuration to HTTP headers. Supports Bearer, Basic, and API Key auth.
     * OAuth2 and JWT types are not reducible to a single header and are skipped.
     *
     * @param auth          the request auth response
     * @param headers       the mutable headers map to add auth headers to
     * @param teamId        the team ID for variable resolution
     * @param environmentId the environment ID for variable resolution (nullable)
     */
    private void resolveAuthToHeaders(RequestAuthResponse auth, Map<String, String> headers,
                                      UUID teamId, UUID environmentId) {
        switch (auth.authType()) {
            case BEARER_TOKEN -> {
                String token = variableService.resolveVariables(
                        auth.bearerToken(), teamId, null, environmentId, null);
                if (token != null && !token.isBlank()) {
                    headers.put("Authorization", "Bearer " + token);
                }
            }
            case BASIC_AUTH -> {
                String user = variableService.resolveVariables(
                        auth.basicUsername(), teamId, null, environmentId, null);
                String pass = variableService.resolveVariables(
                        auth.basicPassword(), teamId, null, environmentId, null);
                String credentials = (user != null ? user : "") + ":" + (pass != null ? pass : "");
                String encoded = Base64.getEncoder().encodeToString(
                        credentials.getBytes(StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + encoded);
            }
            case API_KEY -> {
                String headerName = variableService.resolveVariables(
                        auth.apiKeyHeader(), teamId, null, environmentId, null);
                String headerValue = variableService.resolveVariables(
                        auth.apiKeyValue(), teamId, null, environmentId, null);
                if (headerName != null && !headerName.isBlank()
                        && "header".equalsIgnoreCase(auth.apiKeyAddTo())) {
                    headers.put(headerName, headerValue != null ? headerValue : "");
                }
            }
            default -> {
                // OAuth2, JWT — too complex for simple header resolution in code generation
            }
        }
    }

    // ─── Content Type Resolution ───

    /**
     * Maps a BodyType enum to its corresponding MIME content type string.
     *
     * @param bodyType the body type
     * @return the MIME content type, or null for BINARY and NONE
     */
    private String resolveContentType(BodyType bodyType) {
        return switch (bodyType) {
            case RAW_JSON -> "application/json";
            case RAW_XML -> "application/xml";
            case RAW_HTML -> "text/html";
            case RAW_TEXT -> "text/plain";
            case RAW_YAML -> "application/yaml";
            case FORM_DATA -> "multipart/form-data";
            case X_WWW_FORM_URLENCODED -> "application/x-www-form-urlencoded";
            case GRAPHQL -> "application/json";
            case BINARY, NONE -> null;
        };
    }

    // ─── Built-in Generators ───

    /**
     * Dispatches to the appropriate built-in code generator for the specified language.
     * Package-private for testing.
     *
     * @param language the target language
     * @param req      the resolved request
     * @return the generated code string
     */
    String generateBuiltIn(CodeLanguage language, ResolvedRequest req) {
        return switch (language) {
            case CURL -> generateCurl(req);
            case PYTHON_REQUESTS -> generatePythonRequests(req);
            case JAVASCRIPT_FETCH -> generateJavaScriptFetch(req);
            case JAVASCRIPT_AXIOS -> generateJavaScriptAxios(req);
            case JAVA_HTTP_CLIENT -> generateJavaHttpClient(req);
            case JAVA_OKHTTP -> generateJavaOkHttp(req);
            case CSHARP_HTTP_CLIENT -> generateCSharpHttpClient(req);
            case GO -> generateGo(req);
            case RUBY -> generateRuby(req);
            case PHP -> generatePhp(req);
            case SWIFT -> generateSwift(req);
            case KOTLIN -> generateKotlin(req);
        };
    }

    /**
     * Generates a cURL command for the resolved request.
     *
     * @param req the resolved request
     * @return the cURL command string
     */
    private String generateCurl(ResolvedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X ").append(req.method())
                .append(" '").append(req.url()).append("'");

        for (Map.Entry<String, String> header : req.headers().entrySet()) {
            sb.append(" \\\n  -H '").append(header.getKey()).append(": ")
                    .append(header.getValue()).append("'");
        }

        if (req.body() != null && !req.body().isEmpty()) {
            sb.append(" \\\n  -d '").append(req.body().replace("'", "'\\''")).append("'");
        }

        return sb.toString();
    }

    /**
     * Generates Python code using the requests library.
     *
     * @param req the resolved request
     * @return the Python code string
     */
    private String generatePythonRequests(ResolvedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("import requests\n\n");
        sb.append("url = \"").append(req.url()).append("\"\n");

        if (!req.headers().isEmpty()) {
            sb.append("headers = {\n");
            List<Map.Entry<String, String>> entries = new ArrayList<>(req.headers().entrySet());
            for (int i = 0; i < entries.size(); i++) {
                sb.append("    \"").append(entries.get(i).getKey()).append("\": \"")
                        .append(entries.get(i).getValue()).append("\"");
                if (i < entries.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("}\n");
        }

        if (req.body() != null && !req.body().isEmpty()) {
            sb.append("payload = '").append(req.body().replace("'", "\\'")).append("'\n");
        }

        sb.append("\nresponse = requests.").append(req.method().toLowerCase()).append("(url");
        if (!req.headers().isEmpty()) sb.append(", headers=headers");
        if (req.body() != null && !req.body().isEmpty()) sb.append(", data=payload");
        sb.append(")\n");
        sb.append("print(response.status_code)\n");
        sb.append("print(response.text)\n");

        return sb.toString();
    }

    /**
     * Generates JavaScript code using the Fetch API.
     *
     * @param req the resolved request
     * @return the JavaScript Fetch code string
     */
    private String generateJavaScriptFetch(ResolvedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("const url = \"").append(req.url()).append("\";\n");
        sb.append("const options = {\n");
        sb.append("  method: \"").append(req.method()).append("\"");

        if (!req.headers().isEmpty()) {
            sb.append(",\n  headers: {\n");
            List<Map.Entry<String, String>> entries = new ArrayList<>(req.headers().entrySet());
            for (int i = 0; i < entries.size(); i++) {
                sb.append("    \"").append(entries.get(i).getKey()).append("\": \"")
                        .append(entries.get(i).getValue()).append("\"");
                if (i < entries.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  }");
        }

        if (req.body() != null && !req.body().isEmpty()) {
            sb.append(",\n  body: '").append(req.body().replace("'", "\\'")).append("'");
        }

        sb.append("\n};\n\n");
        sb.append("fetch(url, options)\n");
        sb.append("  .then(response => response.json())\n");
        sb.append("  .then(data => console.log(data))\n");
        sb.append("  .catch(error => console.error(\"Error:\", error));\n");

        return sb.toString();
    }

    /**
     * Generates JavaScript code using the Axios library.
     *
     * @param req the resolved request
     * @return the JavaScript Axios code string
     */
    private String generateJavaScriptAxios(ResolvedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("const axios = require(\"axios\");\n\n");
        sb.append("const options = {\n");
        sb.append("  method: \"").append(req.method()).append("\",\n");
        sb.append("  url: \"").append(req.url()).append("\"");

        if (!req.headers().isEmpty()) {
            sb.append(",\n  headers: {\n");
            List<Map.Entry<String, String>> entries = new ArrayList<>(req.headers().entrySet());
            for (int i = 0; i < entries.size(); i++) {
                sb.append("    \"").append(entries.get(i).getKey()).append("\": \"")
                        .append(entries.get(i).getValue()).append("\"");
                if (i < entries.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  }");
        }

        if (req.body() != null && !req.body().isEmpty()) {
            sb.append(",\n  data: '").append(req.body().replace("'", "\\'")).append("'");
        }

        sb.append("\n};\n\n");
        sb.append("axios(options)\n");
        sb.append("  .then(response => console.log(response.data))\n");
        sb.append("  .catch(error => console.error(error));\n");

        return sb.toString();
    }

    /**
     * Generates Java code using the java.net.http.HttpClient API.
     *
     * @param req the resolved request
     * @return the Java HttpClient code string
     */
    private String generateJavaHttpClient(ResolvedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("import java.net.URI;\n");
        sb.append("import java.net.http.HttpClient;\n");
        sb.append("import java.net.http.HttpRequest;\n");
        sb.append("import java.net.http.HttpResponse;\n\n");
        sb.append("HttpClient client = HttpClient.newHttpClient();\n");
        sb.append("HttpRequest request = HttpRequest.newBuilder()\n");
        sb.append("    .uri(URI.create(\"").append(req.url()).append("\"))\n");

        if (req.body() != null && !req.body().isEmpty()) {
            sb.append("    .method(\"").append(req.method())
                    .append("\", HttpRequest.BodyPublishers.ofString(\"")
                    .append(req.body().replace("\"", "\\\"")).append("\"))\n");
        } else if ("GET".equals(req.method())) {
            sb.append("    .GET()\n");
        } else if ("DELETE".equals(req.method())) {
            sb.append("    .DELETE()\n");
        } else {
            sb.append("    .method(\"").append(req.method())
                    .append("\", HttpRequest.BodyPublishers.noBody())\n");
        }

        for (Map.Entry<String, String> header : req.headers().entrySet()) {
            sb.append("    .header(\"").append(header.getKey()).append("\", \"")
                    .append(header.getValue()).append("\")\n");
        }

        sb.append("    .build();\n\n");
        sb.append("HttpResponse<String> response = client.send(request,");
        sb.append(" HttpResponse.BodyHandlers.ofString());\n");
        sb.append("System.out.println(response.statusCode());\n");
        sb.append("System.out.println(response.body());\n");

        return sb.toString();
    }

    /**
     * Generates Java code using the OkHttp library.
     *
     * @param req the resolved request
     * @return the Java OkHttp code string
     */
    private String generateJavaOkHttp(ResolvedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("import okhttp3.*;\n\n");
        sb.append("OkHttpClient client = new OkHttpClient();\n");

        if (req.body() != null && !req.body().isEmpty()) {
            String mediaType = req.contentType() != null ? req.contentType() : "application/json";
            sb.append("MediaType mediaType = MediaType.parse(\"").append(mediaType).append("\");\n");
            sb.append("RequestBody body = RequestBody.create(mediaType, \"")
                    .append(req.body().replace("\"", "\\\"")).append("\");\n\n");
        }

        sb.append("Request request = new Request.Builder()\n");
        sb.append("    .url(\"").append(req.url()).append("\")\n");

        if (req.body() != null && !req.body().isEmpty()) {
            sb.append("    .method(\"").append(req.method()).append("\", body)\n");
        } else if ("GET".equals(req.method())) {
            sb.append("    .get()\n");
        } else if ("DELETE".equals(req.method())) {
            sb.append("    .delete()\n");
        } else {
            sb.append("    .method(\"").append(req.method()).append("\", null)\n");
        }

        for (Map.Entry<String, String> header : req.headers().entrySet()) {
            sb.append("    .addHeader(\"").append(header.getKey()).append("\", \"")
                    .append(header.getValue()).append("\")\n");
        }

        sb.append("    .build();\n\n");
        sb.append("Response response = client.newCall(request).execute();\n");
        sb.append("System.out.println(response.code());\n");
        sb.append("System.out.println(response.body().string());\n");

        return sb.toString();
    }

    /**
     * Generates C# code using the System.Net.Http.HttpClient API.
     *
     * @param req the resolved request
     * @return the C# HttpClient code string
     */
    private String generateCSharpHttpClient(ResolvedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("using System.Net.Http;\n");
        sb.append("using System.Text;\n\n");
        sb.append("var client = new HttpClient();\n");

        String httpMethod = switch (req.method()) {
            case "GET" -> "HttpMethod.Get";
            case "POST" -> "HttpMethod.Post";
            case "PUT" -> "HttpMethod.Put";
            case "PATCH" -> "HttpMethod.Patch";
            case "DELETE" -> "HttpMethod.Delete";
            case "HEAD" -> "HttpMethod.Head";
            case "OPTIONS" -> "HttpMethod.Options";
            default -> "new HttpMethod(\"" + req.method() + "\")";
        };

        sb.append("var request = new HttpRequestMessage(").append(httpMethod)
                .append(", \"").append(req.url()).append("\");\n");

        for (Map.Entry<String, String> header : req.headers().entrySet()) {
            if (!"Content-Type".equalsIgnoreCase(header.getKey())) {
                sb.append("request.Headers.Add(\"").append(header.getKey()).append("\", \"")
                        .append(header.getValue()).append("\");\n");
            }
        }

        if (req.body() != null && !req.body().isEmpty()) {
            String ct = req.contentType() != null ? req.contentType() : "application/json";
            sb.append("request.Content = new StringContent(\"")
                    .append(req.body().replace("\"", "\\\""))
                    .append("\", Encoding.UTF8, \"").append(ct).append("\");\n");
        }

        sb.append("\nvar response = await client.SendAsync(request);\n");
        sb.append("Console.WriteLine(response.StatusCode);\n");
        sb.append("Console.WriteLine(await response.Content.ReadAsStringAsync());\n");

        return sb.toString();
    }

    /**
     * Generates Go code using the net/http standard library.
     *
     * @param req the resolved request
     * @return the Go code string
     */
    private String generateGo(ResolvedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("package main\n\n");
        sb.append("import (\n");
        sb.append("    \"fmt\"\n");
        sb.append("    \"io\"\n");
        sb.append("    \"net/http\"\n");
        if (req.body() != null && !req.body().isEmpty()) {
            sb.append("    \"strings\"\n");
        }
        sb.append(")\n\n");
        sb.append("func main() {\n");
        sb.append("    url := \"").append(req.url()).append("\"\n");

        if (req.body() != null && !req.body().isEmpty()) {
            sb.append("    payload := strings.NewReader(`").append(req.body()).append("`)\n");
            sb.append("    req, _ := http.NewRequest(\"").append(req.method())
                    .append("\", url, payload)\n");
        } else {
            sb.append("    req, _ := http.NewRequest(\"").append(req.method())
                    .append("\", url, nil)\n");
        }

        for (Map.Entry<String, String> header : req.headers().entrySet()) {
            sb.append("    req.Header.Add(\"").append(header.getKey()).append("\", \"")
                    .append(header.getValue()).append("\")\n");
        }

        sb.append("\n    res, _ := http.DefaultClient.Do(req)\n");
        sb.append("    defer res.Body.Close()\n\n");
        sb.append("    body, _ := io.ReadAll(res.Body)\n");
        sb.append("    fmt.Println(res.StatusCode)\n");
        sb.append("    fmt.Println(string(body))\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Generates Ruby code using the net/http standard library.
     *
     * @param req the resolved request
     * @return the Ruby code string
     */
    private String generateRuby(ResolvedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("require \"net/http\"\n");
        sb.append("require \"uri\"\n\n");
        sb.append("uri = URI.parse(\"").append(req.url()).append("\")\n");
        sb.append("http = Net::HTTP.new(uri.host, uri.port)\n");
        sb.append("http.use_ssl = uri.scheme == \"https\"\n\n");

        String rubyMethod = capitalize(req.method().toLowerCase());
        sb.append("request = Net::HTTP::").append(rubyMethod).append(".new(uri.request_uri)\n");

        for (Map.Entry<String, String> header : req.headers().entrySet()) {
            sb.append("request[\"").append(header.getKey()).append("\"] = \"")
                    .append(header.getValue()).append("\"\n");
        }

        if (req.body() != null && !req.body().isEmpty()) {
            sb.append("request.body = '").append(req.body().replace("'", "\\'")).append("'\n");
        }

        sb.append("\nresponse = http.request(request)\n");
        sb.append("puts response.code\n");
        sb.append("puts response.body\n");

        return sb.toString();
    }

    /**
     * Generates PHP code using the cURL extension.
     *
     * @param req the resolved request
     * @return the PHP code string
     */
    private String generatePhp(ResolvedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?php\n\n");
        sb.append("$ch = curl_init();\n");
        sb.append("curl_setopt($ch, CURLOPT_URL, \"").append(req.url()).append("\");\n");
        sb.append("curl_setopt($ch, CURLOPT_CUSTOMREQUEST, \"").append(req.method()).append("\");\n");
        sb.append("curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);\n");

        if (!req.headers().isEmpty()) {
            sb.append("curl_setopt($ch, CURLOPT_HTTPHEADER, [\n");
            List<Map.Entry<String, String>> entries = new ArrayList<>(req.headers().entrySet());
            for (int i = 0; i < entries.size(); i++) {
                sb.append("    \"").append(entries.get(i).getKey()).append(": ")
                        .append(entries.get(i).getValue()).append("\"");
                if (i < entries.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]);\n");
        }

        if (req.body() != null && !req.body().isEmpty()) {
            sb.append("curl_setopt($ch, CURLOPT_POSTFIELDS, '")
                    .append(req.body().replace("'", "\\'")).append("');\n");
        }

        sb.append("\n$response = curl_exec($ch);\n");
        sb.append("$httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);\n");
        sb.append("curl_close($ch);\n\n");
        sb.append("echo $httpCode . \"\\n\";\n");
        sb.append("echo $response . \"\\n\";\n");

        return sb.toString();
    }

    /**
     * Generates Swift code using the URLSession API.
     *
     * @param req the resolved request
     * @return the Swift code string
     */
    private String generateSwift(ResolvedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("import Foundation\n\n");
        sb.append("let url = URL(string: \"").append(req.url()).append("\")!\n");
        sb.append("var request = URLRequest(url: url)\n");
        sb.append("request.httpMethod = \"").append(req.method()).append("\"\n");

        for (Map.Entry<String, String> header : req.headers().entrySet()) {
            sb.append("request.addValue(\"").append(header.getValue())
                    .append("\", forHTTPHeaderField: \"").append(header.getKey()).append("\")\n");
        }

        if (req.body() != null && !req.body().isEmpty()) {
            sb.append("request.httpBody = \"").append(req.body().replace("\"", "\\\""))
                    .append("\".data(using: .utf8)\n");
        }

        sb.append("\nlet task = URLSession.shared.dataTask(with: request) { data, response, error in\n");
        sb.append("    if let httpResponse = response as? HTTPURLResponse {\n");
        sb.append("        print(httpResponse.statusCode)\n");
        sb.append("    }\n");
        sb.append("    if let data = data, let body = String(data: data, encoding: .utf8) {\n");
        sb.append("        print(body)\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("task.resume()\n");

        return sb.toString();
    }

    /**
     * Generates Kotlin code using the java.net.http.HttpClient API.
     *
     * @param req the resolved request
     * @return the Kotlin code string
     */
    private String generateKotlin(ResolvedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("import java.net.URI\n");
        sb.append("import java.net.http.HttpClient\n");
        sb.append("import java.net.http.HttpRequest\n");
        sb.append("import java.net.http.HttpResponse\n\n");
        sb.append("val client = HttpClient.newHttpClient()\n");
        sb.append("val request = HttpRequest.newBuilder()\n");
        sb.append("    .uri(URI.create(\"").append(req.url()).append("\"))\n");

        if (req.body() != null && !req.body().isEmpty()) {
            sb.append("    .method(\"").append(req.method())
                    .append("\", HttpRequest.BodyPublishers.ofString(\"")
                    .append(req.body().replace("\"", "\\\"")).append("\"))\n");
        } else if ("GET".equals(req.method())) {
            sb.append("    .GET()\n");
        } else if ("DELETE".equals(req.method())) {
            sb.append("    .DELETE()\n");
        } else {
            sb.append("    .method(\"").append(req.method())
                    .append("\", HttpRequest.BodyPublishers.noBody())\n");
        }

        for (Map.Entry<String, String> header : req.headers().entrySet()) {
            sb.append("    .header(\"").append(header.getKey()).append("\", \"")
                    .append(header.getValue()).append("\")\n");
        }

        sb.append("    .build()\n\n");
        sb.append("val response = client.send(request, HttpResponse.BodyHandlers.ofString())\n");
        sb.append("println(response.statusCode())\n");
        sb.append("println(response.body())\n");

        return sb.toString();
    }

    // ─── Template Engine ───

    /**
     * Processes a custom template by replacing Mustache-style {{placeholder}} tokens
     * with resolved request values. Supported placeholders:
     * <ul>
     *   <li>{{method}} — HTTP method (uppercase)</li>
     *   <li>{{method_lower}} — HTTP method (lowercase)</li>
     *   <li>{{url}} — resolved URL</li>
     *   <li>{{headers_json}} — headers as JSON object string</li>
     *   <li>{{body}} — raw request body</li>
     *   <li>{{body_escaped}} — body with special characters escaped</li>
     *   <li>{{content_type}} — MIME content type</li>
     *   <li>{{auth_header}} — Authorization header value</li>
     * </ul>
     * Unrecognized placeholders are left as-is.
     * Package-private for testing.
     *
     * @param template the template string with {{placeholder}} tokens
     * @param req      the resolved request providing substitution values
     * @return the processed template with placeholders replaced
     */
    String processTemplate(String template, ResolvedRequest req) {
        String result = template;
        result = result.replace("{{method}}", req.method());
        result = result.replace("{{method_lower}}", req.method().toLowerCase());
        result = result.replace("{{url}}", req.url() != null ? req.url() : "");
        result = result.replace("{{headers_json}}", headersToJson(req.headers()));
        result = result.replace("{{body}}", req.body() != null ? req.body() : "");
        result = result.replace("{{body_escaped}}", escapeBody(req.body()));
        result = result.replace("{{content_type}}", req.contentType() != null ? req.contentType() : "");

        String authHeader = req.headers().getOrDefault("Authorization", "");
        result = result.replace("{{auth_header}}", authHeader);

        return result;
    }

    // ─── Helpers ───

    /**
     * Converts a headers map to a JSON object string.
     *
     * @param headers the headers map
     * @return the JSON object string representation
     */
    private String headersToJson(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        List<Map.Entry<String, String>> entries = new ArrayList<>(headers.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(entries.get(i).getKey()).append("\": \"")
                    .append(entries.get(i).getValue().replace("\"", "\\\"")).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Escapes special characters in a body string for safe embedding in code.
     *
     * @param body the body string to escape
     * @return the escaped body string
     */
    private String escapeBody(String body) {
        if (body == null) return "";
        return body.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Capitalizes the first character of a string.
     *
     * @param s the string to capitalize
     * @return the capitalized string
     */
    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Converts a CodeSnippetTemplate entity to a CodeSnippetTemplateResponse.
     *
     * @param template the template entity
     * @return the template response
     */
    private CodeSnippetTemplateResponse toTemplateResponse(CodeSnippetTemplate template) {
        return new CodeSnippetTemplateResponse(
                template.getId(),
                template.getLanguage(),
                template.getDisplayName(),
                template.getTemplateContent(),
                template.getFileExtension(),
                template.getContentType()
        );
    }
}
