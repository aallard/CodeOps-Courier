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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeGenerationServiceTest {

    @Mock
    private CodeSnippetTemplateRepository templateRepository;

    @Mock
    private RequestService requestService;

    @Mock
    private VariableService variableService;

    @InjectMocks
    private CodeGenerationService service;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID ENV_ID = UUID.randomUUID();

    // ─── generateCode Tests ───

    @Test
    void generateCode_simpleGetRequest_returnsCurlSnippet() {
        RequestResponse reqData = buildRequestResponse(HttpMethod.GET, "https://api.example.com/users",
                List.of(), null, null);
        setupVariablePassthrough();
        when(requestService.getRequest(REQUEST_ID, TEAM_ID)).thenReturn(reqData);
        when(templateRepository.findByLanguage(CodeLanguage.CURL)).thenReturn(Optional.empty());

        CodeSnippetResponse result = service.generateCode(
                new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, ENV_ID), TEAM_ID);

        assertThat(result.language()).isEqualTo(CodeLanguage.CURL);
        assertThat(result.displayName()).isEqualTo("cURL");
        assertThat(result.code()).contains("curl -X GET");
        assertThat(result.code()).contains("https://api.example.com/users");
        assertThat(result.fileExtension()).isEqualTo("sh");
        assertThat(result.contentType()).isEqualTo("text/x-shellscript");
    }

    @Test
    void generateCode_postWithJsonBody_includesBodyInOutput() {
        RequestBodyResponse body = new RequestBodyResponse(UUID.randomUUID(), BodyType.RAW_JSON,
                "{\"name\": \"test\"}", null, null, null, null);
        RequestResponse reqData = buildRequestResponse(HttpMethod.POST, "https://api.example.com/users",
                List.of(), body, null);
        setupVariablePassthrough();
        when(requestService.getRequest(REQUEST_ID, TEAM_ID)).thenReturn(reqData);
        when(templateRepository.findByLanguage(CodeLanguage.CURL)).thenReturn(Optional.empty());

        CodeSnippetResponse result = service.generateCode(
                new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, ENV_ID), TEAM_ID);

        assertThat(result.code()).contains("-d");
        assertThat(result.code()).contains("\"name\": \"test\"");
        assertThat(result.code()).contains("Content-Type: application/json");
    }

    @Test
    void generateCode_withHeaders_includesHeadersInOutput() {
        List<RequestHeaderResponse> headers = List.of(
                new RequestHeaderResponse(UUID.randomUUID(), "Accept", "application/json", null, true),
                new RequestHeaderResponse(UUID.randomUUID(), "X-Api-Key", "key123", null, true)
        );
        RequestResponse reqData = buildRequestResponse(HttpMethod.GET, "https://api.example.com",
                headers, null, null);
        setupVariablePassthrough();
        when(requestService.getRequest(REQUEST_ID, TEAM_ID)).thenReturn(reqData);
        when(templateRepository.findByLanguage(CodeLanguage.PYTHON_REQUESTS)).thenReturn(Optional.empty());

        CodeSnippetResponse result = service.generateCode(
                new GenerateCodeRequest(REQUEST_ID, CodeLanguage.PYTHON_REQUESTS, ENV_ID), TEAM_ID);

        assertThat(result.code()).contains("Accept");
        assertThat(result.code()).contains("application/json");
        assertThat(result.code()).contains("X-Api-Key");
        assertThat(result.code()).contains("key123");
    }

    @Test
    void generateCode_disabledHeadersExcluded() {
        List<RequestHeaderResponse> headers = List.of(
                new RequestHeaderResponse(UUID.randomUUID(), "Active", "yes", null, true),
                new RequestHeaderResponse(UUID.randomUUID(), "Disabled", "no", null, false)
        );
        RequestResponse reqData = buildRequestResponse(HttpMethod.GET, "https://api.example.com",
                headers, null, null);
        setupVariablePassthrough();
        when(requestService.getRequest(REQUEST_ID, TEAM_ID)).thenReturn(reqData);
        when(templateRepository.findByLanguage(CodeLanguage.CURL)).thenReturn(Optional.empty());

        CodeSnippetResponse result = service.generateCode(
                new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, ENV_ID), TEAM_ID);

        assertThat(result.code()).contains("Active: yes");
        assertThat(result.code()).doesNotContain("Disabled");
    }

    @Test
    void generateCode_withBearerAuth_includesAuthorizationHeader() {
        RequestAuthResponse auth = buildAuth(AuthType.BEARER_TOKEN);
        RequestResponse reqData = buildRequestResponse(HttpMethod.GET, "https://api.example.com",
                List.of(), null, auth);
        setupVariablePassthrough();
        when(requestService.getRequest(REQUEST_ID, TEAM_ID)).thenReturn(reqData);
        when(templateRepository.findByLanguage(CodeLanguage.CURL)).thenReturn(Optional.empty());

        CodeSnippetResponse result = service.generateCode(
                new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, ENV_ID), TEAM_ID);

        assertThat(result.code()).contains("Authorization: Bearer mytoken123");
    }

    @Test
    void generateCode_withBasicAuth_includesBase64Header() {
        RequestAuthResponse auth = new RequestAuthResponse(UUID.randomUUID(), AuthType.BASIC_AUTH,
                null, null, null, null, "admin", "secret",
                null, null, null, null, null, null, null, null, null, null, null);
        RequestResponse reqData = buildRequestResponse(HttpMethod.GET, "https://api.example.com",
                List.of(), null, auth);
        setupVariablePassthrough();
        when(requestService.getRequest(REQUEST_ID, TEAM_ID)).thenReturn(reqData);
        when(templateRepository.findByLanguage(CodeLanguage.CURL)).thenReturn(Optional.empty());

        CodeSnippetResponse result = service.generateCode(
                new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, ENV_ID), TEAM_ID);

        String expected = Base64.getEncoder().encodeToString("admin:secret".getBytes(StandardCharsets.UTF_8));
        assertThat(result.code()).contains("Authorization: Basic " + expected);
    }

    @Test
    void generateCode_withApiKeyAuth_addsCustomHeader() {
        RequestAuthResponse auth = new RequestAuthResponse(UUID.randomUUID(), AuthType.API_KEY,
                "X-Custom-Key", "apikey999", "header", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        RequestResponse reqData = buildRequestResponse(HttpMethod.GET, "https://api.example.com",
                List.of(), null, auth);
        setupVariablePassthrough();
        when(requestService.getRequest(REQUEST_ID, TEAM_ID)).thenReturn(reqData);
        when(templateRepository.findByLanguage(CodeLanguage.CURL)).thenReturn(Optional.empty());

        CodeSnippetResponse result = service.generateCode(
                new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, ENV_ID), TEAM_ID);

        assertThat(result.code()).contains("X-Custom-Key: apikey999");
    }

    @Test
    void generateCode_getRequestHasNoBody() {
        RequestBodyResponse body = new RequestBodyResponse(UUID.randomUUID(), BodyType.RAW_JSON,
                "{\"data\":true}", null, null, null, null);
        RequestResponse reqData = buildRequestResponse(HttpMethod.GET, "https://api.example.com",
                List.of(), body, null);
        setupVariablePassthrough();
        when(requestService.getRequest(REQUEST_ID, TEAM_ID)).thenReturn(reqData);
        when(templateRepository.findByLanguage(CodeLanguage.CURL)).thenReturn(Optional.empty());

        CodeSnippetResponse result = service.generateCode(
                new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, ENV_ID), TEAM_ID);

        assertThat(result.code()).doesNotContain("-d");
        assertThat(result.code()).doesNotContain("data");
    }

    @Test
    void generateCode_customTemplateOverridesBuiltIn() {
        RequestResponse reqData = buildRequestResponse(HttpMethod.POST, "https://api.example.com",
                List.of(), null, null);
        setupVariablePassthrough();
        when(requestService.getRequest(REQUEST_ID, TEAM_ID)).thenReturn(reqData);

        CodeSnippetTemplate custom = new CodeSnippetTemplate();
        custom.setLanguage(CodeLanguage.CURL);
        custom.setDisplayName("Custom cURL");
        custom.setTemplateContent("http {{method}} {{url}}");
        custom.setFileExtension("sh");
        custom.setContentType("text/x-shellscript");
        when(templateRepository.findByLanguage(CodeLanguage.CURL)).thenReturn(Optional.of(custom));

        CodeSnippetResponse result = service.generateCode(
                new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, ENV_ID), TEAM_ID);

        assertThat(result.code()).isEqualTo("http POST https://api.example.com");
        assertThat(result.displayName()).isEqualTo("Custom cURL");
    }

    @Test
    void generateCode_requestNotFound_throwsNotFoundException() {
        when(requestService.getRequest(REQUEST_ID, TEAM_ID))
                .thenThrow(new NotFoundException("Request not found: " + REQUEST_ID));

        assertThatThrownBy(() -> service.generateCode(
                new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, ENV_ID), TEAM_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void generateCode_variablesAreResolved() {
        RequestResponse reqData = buildRequestResponse(HttpMethod.GET, "{{baseUrl}}/users",
                List.of(), null, null);
        when(requestService.getRequest(REQUEST_ID, TEAM_ID)).thenReturn(reqData);
        when(templateRepository.findByLanguage(CodeLanguage.CURL)).thenReturn(Optional.empty());
        when(variableService.resolveVariables(eq("{{baseUrl}}/users"), eq(TEAM_ID),
                isNull(), eq(ENV_ID), isNull()))
                .thenReturn("https://api.prod.com/users");

        CodeSnippetResponse result = service.generateCode(
                new GenerateCodeRequest(REQUEST_ID, CodeLanguage.CURL, ENV_ID), TEAM_ID);

        assertThat(result.code()).contains("https://api.prod.com/users");
        assertThat(result.code()).doesNotContain("{{baseUrl}}");
    }

    // ─── All 12 Languages Produce Code ───

    @Test
    void generateBuiltIn_allLanguagesProduceNonEmptyCode() {
        CodeGenerationService.ResolvedRequest req = new CodeGenerationService.ResolvedRequest(
                "POST", "https://api.example.com/data",
                new LinkedHashMap<>(Map.of("Content-Type", "application/json", "Accept", "application/json")),
                "{\"key\":\"value\"}", "application/json");

        for (CodeLanguage lang : CodeLanguage.values()) {
            String code = service.generateBuiltIn(lang, req);
            assertThat(code)
                    .as("Code for %s should not be empty", lang)
                    .isNotBlank();
            assertThat(code)
                    .as("Code for %s should contain the URL", lang)
                    .contains("https://api.example.com/data");
        }
    }

    // ─── Specific Generator Tests ───

    @Test
    void generateCurl_formatsCorrectly() {
        CodeGenerationService.ResolvedRequest req = new CodeGenerationService.ResolvedRequest(
                "POST", "https://api.example.com",
                new LinkedHashMap<>(Map.of("Content-Type", "application/json")),
                "{\"key\":\"val\"}", "application/json");

        String code = service.generateBuiltIn(CodeLanguage.CURL, req);

        assertThat(code).startsWith("curl -X POST");
        assertThat(code).contains("-H 'Content-Type: application/json'");
        assertThat(code).contains("-d '{\"key\":\"val\"}'");
    }

    @Test
    void generatePythonRequests_formatsCorrectly() {
        CodeGenerationService.ResolvedRequest req = new CodeGenerationService.ResolvedRequest(
                "GET", "https://api.example.com", new LinkedHashMap<>(), null, null);

        String code = service.generateBuiltIn(CodeLanguage.PYTHON_REQUESTS, req);

        assertThat(code).contains("import requests");
        assertThat(code).contains("url = \"https://api.example.com\"");
        assertThat(code).contains("requests.get(url)");
        assertThat(code).contains("print(response.status_code)");
    }

    @Test
    void generateJavaScriptFetch_formatsCorrectly() {
        CodeGenerationService.ResolvedRequest req = new CodeGenerationService.ResolvedRequest(
                "POST", "https://api.example.com",
                new LinkedHashMap<>(Map.of("Content-Type", "application/json")),
                "{}", "application/json");

        String code = service.generateBuiltIn(CodeLanguage.JAVASCRIPT_FETCH, req);

        assertThat(code).contains("fetch(url, options)");
        assertThat(code).contains("method: \"POST\"");
        assertThat(code).contains("body:");
    }

    @Test
    void generateJavaHttpClient_getWithNoBody() {
        CodeGenerationService.ResolvedRequest req = new CodeGenerationService.ResolvedRequest(
                "GET", "https://api.example.com", new LinkedHashMap<>(), null, null);

        String code = service.generateBuiltIn(CodeLanguage.JAVA_HTTP_CLIENT, req);

        assertThat(code).contains(".GET()");
        assertThat(code).doesNotContain("BodyPublishers.ofString");
    }

    @Test
    void generateJavaHttpClient_postWithBody() {
        CodeGenerationService.ResolvedRequest req = new CodeGenerationService.ResolvedRequest(
                "POST", "https://api.example.com",
                new LinkedHashMap<>(Map.of("Content-Type", "application/json")),
                "{\"a\":1}", "application/json");

        String code = service.generateBuiltIn(CodeLanguage.JAVA_HTTP_CLIENT, req);

        assertThat(code).contains("BodyPublishers.ofString");
        assertThat(code).contains(".method(\"POST\"");
    }

    @Test
    void generateGo_includesStringsImportOnlyWhenBodyPresent() {
        CodeGenerationService.ResolvedRequest noBody = new CodeGenerationService.ResolvedRequest(
                "GET", "https://api.example.com", new LinkedHashMap<>(), null, null);
        CodeGenerationService.ResolvedRequest withBody = new CodeGenerationService.ResolvedRequest(
                "POST", "https://api.example.com", new LinkedHashMap<>(),
                "{\"a\":1}", "application/json");

        String codeNoBody = service.generateBuiltIn(CodeLanguage.GO, noBody);
        String codeWithBody = service.generateBuiltIn(CodeLanguage.GO, withBody);

        assertThat(codeNoBody).doesNotContain("\"strings\"");
        assertThat(codeWithBody).contains("\"strings\"");
        assertThat(codeWithBody).contains("strings.NewReader");
    }

    @Test
    void generateCSharp_excludesContentTypeFromHeaders() {
        CodeGenerationService.ResolvedRequest req = new CodeGenerationService.ResolvedRequest(
                "POST", "https://api.example.com",
                new LinkedHashMap<>(Map.of("Content-Type", "application/json", "Accept", "application/json")),
                "{\"a\":1}", "application/json");

        String code = service.generateBuiltIn(CodeLanguage.CSHARP_HTTP_CLIENT, req);

        assertThat(code).contains("request.Headers.Add(\"Accept\"");
        assertThat(code).doesNotContain("request.Headers.Add(\"Content-Type\"");
        assertThat(code).contains("new StringContent");
    }

    // ─── processTemplate Tests ───

    @Test
    void processTemplate_replacesAllPlaceholders() {
        CodeGenerationService.ResolvedRequest req = new CodeGenerationService.ResolvedRequest(
                "POST", "https://api.example.com",
                new LinkedHashMap<>(Map.of("Authorization", "Bearer tok", "Content-Type", "application/json")),
                "{\"key\":\"value\"}", "application/json");

        String template = "{{method}} {{method_lower}} {{url}} {{headers_json}} {{body}} {{body_escaped}} {{content_type}} {{auth_header}}";
        String result = service.processTemplate(template, req);

        assertThat(result).contains("POST");
        assertThat(result).contains("post");
        assertThat(result).contains("https://api.example.com");
        assertThat(result).contains("\"Authorization\": \"Bearer tok\"");
        assertThat(result).contains("{\"key\":\"value\"}");
        assertThat(result).contains("application/json");
        assertThat(result).contains("Bearer tok");
    }

    @Test
    void processTemplate_unreplacedPlaceholdersLeftAsIs() {
        CodeGenerationService.ResolvedRequest req = new CodeGenerationService.ResolvedRequest(
                "GET", "https://test.com", new LinkedHashMap<>(), null, null);

        String template = "{{method}} {{unknown_placeholder}}";
        String result = service.processTemplate(template, req);

        assertThat(result).isEqualTo("GET {{unknown_placeholder}}");
    }

    @Test
    void processTemplate_nullBodyReplacedWithEmpty() {
        CodeGenerationService.ResolvedRequest req = new CodeGenerationService.ResolvedRequest(
                "GET", "https://test.com", new LinkedHashMap<>(), null, null);

        String template = "body=[{{body}}] escaped=[{{body_escaped}}]";
        String result = service.processTemplate(template, req);

        assertThat(result).isEqualTo("body=[] escaped=[]");
    }

    // ─── resolveRequest Tests ───

    @Test
    void resolveRequest_resolvesVariablesInUrl() {
        RequestResponse reqData = buildRequestResponse(HttpMethod.GET, "{{host}}/api",
                List.of(), null, null);
        when(variableService.resolveVariables("{{host}}/api", TEAM_ID, null, ENV_ID, null))
                .thenReturn("https://prod.com/api");

        CodeGenerationService.ResolvedRequest resolved = service.resolveRequest(reqData, TEAM_ID, ENV_ID);

        assertThat(resolved.url()).isEqualTo("https://prod.com/api");
    }

    @Test
    void resolveRequest_addsContentTypeFromBodyType() {
        RequestBodyResponse body = new RequestBodyResponse(UUID.randomUUID(), BodyType.RAW_XML,
                "<root/>", null, null, null, null);
        RequestResponse reqData = buildRequestResponse(HttpMethod.POST, "https://api.example.com",
                List.of(), body, null);
        setupVariablePassthrough();

        CodeGenerationService.ResolvedRequest resolved = service.resolveRequest(reqData, TEAM_ID, ENV_ID);

        assertThat(resolved.headers()).containsEntry("Content-Type", "application/xml");
        assertThat(resolved.contentType()).isEqualTo("application/xml");
    }

    @Test
    void resolveRequest_noBodyForGetMethod() {
        RequestBodyResponse body = new RequestBodyResponse(UUID.randomUUID(), BodyType.RAW_JSON,
                "{\"a\":1}", null, null, null, null);
        RequestResponse reqData = buildRequestResponse(HttpMethod.GET, "https://api.example.com",
                List.of(), body, null);
        setupVariablePassthrough();

        CodeGenerationService.ResolvedRequest resolved = service.resolveRequest(reqData, TEAM_ID, ENV_ID);

        assertThat(resolved.body()).isNull();
        assertThat(resolved.contentType()).isNull();
    }

    @Test
    void resolveRequest_noBodyForHeadMethod() {
        RequestBodyResponse body = new RequestBodyResponse(UUID.randomUUID(), BodyType.RAW_JSON,
                "{}", null, null, null, null);
        RequestResponse reqData = buildRequestResponse(HttpMethod.HEAD, "https://api.example.com",
                List.of(), body, null);
        setupVariablePassthrough();

        CodeGenerationService.ResolvedRequest resolved = service.resolveRequest(reqData, TEAM_ID, ENV_ID);

        assertThat(resolved.body()).isNull();
    }

    @Test
    void resolveRequest_doesNotDuplicateContentTypeHeader() {
        List<RequestHeaderResponse> headers = List.of(
                new RequestHeaderResponse(UUID.randomUUID(), "Content-Type", "text/xml", null, true)
        );
        RequestBodyResponse body = new RequestBodyResponse(UUID.randomUUID(), BodyType.RAW_JSON,
                "{}", null, null, null, null);
        RequestResponse reqData = buildRequestResponse(HttpMethod.POST, "https://api.example.com",
                headers, body, null);
        setupVariablePassthrough();

        CodeGenerationService.ResolvedRequest resolved = service.resolveRequest(reqData, TEAM_ID, ENV_ID);

        long contentTypeCount = resolved.headers().keySet().stream()
                .filter(k -> k.equalsIgnoreCase("Content-Type")).count();
        assertThat(contentTypeCount).isEqualTo(1);
        assertThat(resolved.headers().get("Content-Type")).isEqualTo("text/xml");
    }

    // ─── getAvailableLanguages Tests ───

    @Test
    void getAvailableLanguages_returnsAll12Languages() {
        when(templateRepository.findAllByOrderByDisplayNameAsc()).thenReturn(Collections.emptyList());

        List<CodeSnippetResponse> languages = service.getAvailableLanguages();

        assertThat(languages).hasSize(12);
        assertThat(languages.stream().map(CodeSnippetResponse::language))
                .containsExactlyInAnyOrder(CodeLanguage.values());
    }

    @Test
    void getAvailableLanguages_customTemplateOverridesMetadata() {
        CodeSnippetTemplate custom = new CodeSnippetTemplate();
        custom.setLanguage(CodeLanguage.CURL);
        custom.setDisplayName("My cURL");
        custom.setFileExtension("curl");
        custom.setContentType("text/x-curl");
        custom.setTemplateContent("template");
        when(templateRepository.findAllByOrderByDisplayNameAsc()).thenReturn(List.of(custom));

        List<CodeSnippetResponse> languages = service.getAvailableLanguages();

        CodeSnippetResponse curl = languages.stream()
                .filter(l -> l.language() == CodeLanguage.CURL).findFirst().orElseThrow();
        assertThat(curl.displayName()).isEqualTo("My cURL");
        assertThat(curl.fileExtension()).isEqualTo("curl");
        assertThat(curl.contentType()).isEqualTo("text/x-curl");
        assertThat(curl.code()).isNull();
    }

    // ─── Template CRUD Tests ───

    @Test
    void getTemplates_returnsAllTemplates() {
        CodeSnippetTemplate t1 = buildTemplate(CodeLanguage.CURL, "cURL Template");
        CodeSnippetTemplate t2 = buildTemplate(CodeLanguage.GO, "Go Template");
        when(templateRepository.findAllByOrderByDisplayNameAsc()).thenReturn(List.of(t1, t2));

        List<CodeSnippetTemplateResponse> result = service.getTemplates();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).displayName()).isEqualTo("cURL Template");
        assertThat(result.get(1).displayName()).isEqualTo("Go Template");
    }

    @Test
    void getTemplate_returnsSingleTemplate() {
        UUID templateId = UUID.randomUUID();
        CodeSnippetTemplate template = buildTemplate(CodeLanguage.RUBY, "Ruby Template");
        template.setId(templateId);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

        CodeSnippetTemplateResponse result = service.getTemplate(templateId);

        assertThat(result.id()).isEqualTo(templateId);
        assertThat(result.language()).isEqualTo(CodeLanguage.RUBY);
        assertThat(result.displayName()).isEqualTo("Ruby Template");
    }

    @Test
    void getTemplate_notFound_throwsNotFoundException() {
        UUID templateId = UUID.randomUUID();
        when(templateRepository.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTemplate(templateId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Code snippet template not found");
    }

    @Test
    void saveTemplate_createsNewTemplate() {
        when(templateRepository.findByLanguage(CodeLanguage.PHP)).thenReturn(Optional.empty());
        when(templateRepository.save(any(CodeSnippetTemplate.class)))
                .thenAnswer(invocation -> {
                    CodeSnippetTemplate t = invocation.getArgument(0);
                    t.setId(UUID.randomUUID());
                    return t;
                });

        CodeSnippetTemplateResponse result = service.saveTemplate(
                CodeLanguage.PHP, "PHP Template", "<?php echo {{url}};", "php", "text/x-php");

        assertThat(result.language()).isEqualTo(CodeLanguage.PHP);
        assertThat(result.displayName()).isEqualTo("PHP Template");
        assertThat(result.templateContent()).isEqualTo("<?php echo {{url}};");
        verify(templateRepository).save(any(CodeSnippetTemplate.class));
    }

    @Test
    void saveTemplate_updatesExistingTemplate() {
        CodeSnippetTemplate existing = buildTemplate(CodeLanguage.PHP, "Old PHP");
        when(templateRepository.findByLanguage(CodeLanguage.PHP)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(CodeSnippetTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CodeSnippetTemplateResponse result = service.saveTemplate(
                CodeLanguage.PHP, "New PHP", "updated content", "php", "text/x-php");

        assertThat(result.displayName()).isEqualTo("New PHP");
        assertThat(result.templateContent()).isEqualTo("updated content");
    }

    @Test
    void saveTemplate_blankContent_throwsValidationException() {
        assertThatThrownBy(() -> service.saveTemplate(
                CodeLanguage.CURL, "cURL", "  ", "sh", "text/x-shellscript"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Template content is required");
    }

    @Test
    void saveTemplate_nullContent_throwsValidationException() {
        assertThatThrownBy(() -> service.saveTemplate(
                CodeLanguage.CURL, "cURL", null, "sh", "text/x-shellscript"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Template content is required");
    }

    @Test
    void deleteTemplate_deletesExistingTemplate() {
        UUID templateId = UUID.randomUUID();
        CodeSnippetTemplate template = buildTemplate(CodeLanguage.SWIFT, "Swift Template");
        template.setId(templateId);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

        service.deleteTemplate(templateId);

        verify(templateRepository).delete(template);
    }

    @Test
    void deleteTemplate_notFound_throwsNotFoundException() {
        UUID templateId = UUID.randomUUID();
        when(templateRepository.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTemplate(templateId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Code snippet template not found");
    }

    // ─── Helper Methods ───

    private void setupVariablePassthrough() {
        when(variableService.resolveVariables(any(), eq(TEAM_ID), isNull(), eq(ENV_ID), isNull()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private RequestResponse buildRequestResponse(HttpMethod method, String url,
                                                  List<RequestHeaderResponse> headers,
                                                  RequestBodyResponse body,
                                                  RequestAuthResponse auth) {
        return new RequestResponse(
                REQUEST_ID, UUID.randomUUID(), "Test Request", "desc",
                method, url, 0,
                headers, List.of(), body, auth, List.of(),
                Instant.now(), Instant.now()
        );
    }

    private RequestAuthResponse buildAuth(AuthType authType) {
        return switch (authType) {
            case BEARER_TOKEN -> new RequestAuthResponse(UUID.randomUUID(), AuthType.BEARER_TOKEN,
                    null, null, null, "mytoken123", null, null,
                    null, null, null, null, null, null, null, null, null, null, null);
            case BASIC_AUTH -> new RequestAuthResponse(UUID.randomUUID(), AuthType.BASIC_AUTH,
                    null, null, null, null, "admin", "password",
                    null, null, null, null, null, null, null, null, null, null, null);
            case API_KEY -> new RequestAuthResponse(UUID.randomUUID(), AuthType.API_KEY,
                    "X-Api-Key", "key123", "header", null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null);
            default -> new RequestAuthResponse(UUID.randomUUID(), AuthType.NO_AUTH,
                    null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null);
        };
    }

    private CodeSnippetTemplate buildTemplate(CodeLanguage language, String displayName) {
        CodeSnippetTemplate template = new CodeSnippetTemplate();
        template.setLanguage(language);
        template.setDisplayName(displayName);
        template.setTemplateContent("{{method}} {{url}}");
        template.setFileExtension("txt");
        template.setContentType("text/plain");
        return template;
    }
}
