package com.codeops.courier.service;

import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurlImporterTest {

    private CurlImporter importer;
    private Folder folder;

    @BeforeEach
    void setUp() {
        importer = new CurlImporter();
        folder = new Folder();
        folder.setName("Test Folder");
    }

    // ─── Simple GET ───

    @Test
    void parseCurl_simpleGet() {
        Request request = importer.parseCurl("curl https://api.example.com/users", folder, 0);

        assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(request.getUrl()).isEqualTo("https://api.example.com/users");
        assertThat(request.getFolder()).isEqualTo(folder);
        assertThat(request.getSortOrder()).isEqualTo(0);
        assertThat(request.getName()).isEqualTo("GET /users");
    }

    // ─── Explicit Method ───

    @Test
    void parseCurl_explicitPostMethod() {
        Request request = importer.parseCurl(
                "curl -X POST https://api.example.com/users -d '{\"name\":\"test\"}'", folder, 1);

        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getBody()).isNotNull();
        assertThat(request.getBody().getRawContent()).isEqualTo("{\"name\":\"test\"}");
    }

    // ─── Headers ───

    @Test
    void parseCurl_withHeaders() {
        Request request = importer.parseCurl(
                "curl -H 'Content-Type: application/json' -H 'Accept: text/html' https://api.example.com/users",
                folder, 0);

        assertThat(request.getHeaders()).hasSize(2);
        assertThat(request.getHeaders().get(0).getHeaderKey()).isEqualTo("Content-Type");
        assertThat(request.getHeaders().get(0).getHeaderValue()).isEqualTo("application/json");
        assertThat(request.getHeaders().get(1).getHeaderKey()).isEqualTo("Accept");
        assertThat(request.getHeaders().get(1).getHeaderValue()).isEqualTo("text/html");
    }

    // ─── Data with Content-Type Detection ───

    @Test
    void parseCurl_dataWithJsonContentType() {
        Request request = importer.parseCurl(
                "curl -X POST -H 'Content-Type: application/json' -d '{\"key\":\"value\"}' https://api.example.com/data",
                folder, 0);

        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getBody()).isNotNull();
        assertThat(request.getBody().getBodyType()).isEqualTo(BodyType.RAW_JSON);
        assertThat(request.getBody().getRawContent()).isEqualTo("{\"key\":\"value\"}");
    }

    // ─── Implicit POST When Data Present ───

    @Test
    void parseCurl_implicitPostWhenDataPresent() {
        Request request = importer.parseCurl(
                "curl -d 'name=test' https://api.example.com/users", folder, 0);

        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
    }

    // ─── Form Data ───

    @Test
    void parseCurl_formData() {
        Request request = importer.parseCurl(
                "curl -F 'file=@photo.jpg' -F 'description=My photo' https://api.example.com/upload",
                folder, 0);

        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getBody()).isNotNull();
        assertThat(request.getBody().getBodyType()).isEqualTo(BodyType.FORM_DATA);
        assertThat(request.getBody().getFormData()).contains("file");
        assertThat(request.getBody().getFormData()).contains("description");
    }

    // ─── Basic Auth ───

    @Test
    void parseCurl_basicAuth() {
        Request request = importer.parseCurl(
                "curl -u admin:secret https://api.example.com/admin", folder, 0);

        assertThat(request.getAuth()).isNotNull();
        assertThat(request.getAuth().getAuthType()).isEqualTo(AuthType.BASIC_AUTH);
        assertThat(request.getAuth().getBasicUsername()).isEqualTo("admin");
        assertThat(request.getAuth().getBasicPassword()).isEqualTo("secret");
    }

    // ─── Bearer Token from Authorization Header ───

    @Test
    void parseCurl_bearerTokenFromHeader() {
        Request request = importer.parseCurl(
                "curl -H 'Authorization: Bearer my-token-123' https://api.example.com/users", folder, 0);

        assertThat(request.getAuth()).isNotNull();
        assertThat(request.getAuth().getAuthType()).isEqualTo(AuthType.BEARER_TOKEN);
        assertThat(request.getAuth().getBearerToken()).isEqualTo("my-token-123");
    }

    // ─── Query Params from URL ───

    @Test
    void parseCurl_queryParamsExtracted() {
        Request request = importer.parseCurl(
                "curl 'https://api.example.com/search?q=test&page=1'", folder, 0);

        assertThat(request.getParams()).hasSize(2);
        assertThat(request.getParams().get(0).getParamKey()).isEqualTo("q");
        assertThat(request.getParams().get(0).getParamValue()).isEqualTo("test");
        assertThat(request.getParams().get(1).getParamKey()).isEqualTo("page");
        assertThat(request.getParams().get(1).getParamValue()).isEqualTo("1");
    }

    // ─── Multiline with Backslash Continuations ───

    @Test
    void parseCurl_multilineBackslash() {
        String multiline = "curl \\\n  -X POST \\\n  -H 'Content-Type: application/json' \\\n  https://api.example.com/data";
        Request request = importer.parseCurl(multiline, folder, 0);

        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getUrl()).isEqualTo("https://api.example.com/data");
        assertThat(request.getHeaders()).hasSize(1);
    }

    // ─── Double Quoted Arguments ───

    @Test
    void parseCurl_doubleQuotedArguments() {
        Request request = importer.parseCurl(
                "curl -H \"Content-Type: application/json\" -d \"{\\\"name\\\":\\\"test\\\"}\" https://api.example.com/users",
                folder, 0);

        assertThat(request.getHeaders()).hasSize(1);
        assertThat(request.getBody()).isNotNull();
    }

    // ─── Validation: Not a cURL Command ───

    @Test
    void parseCurl_notCurlCommand_throws() {
        assertThatThrownBy(() -> importer.parseCurl("wget https://example.com", folder, 0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must start with 'curl'");
    }

    // ─── Validation: No URL ───

    @Test
    void parseCurl_noUrl_throws() {
        assertThatThrownBy(() -> importer.parseCurl("curl -H 'Accept: text/html'", folder, 0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No URL found");
    }

    // ─── Validation: Unsupported Method ───

    @Test
    void parseCurl_unsupportedMethod_throws() {
        assertThatThrownBy(() -> importer.parseCurl("curl -X TRACE https://example.com", folder, 0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported HTTP method");
    }

    // ─── Tokenizer Tests ───

    @Test
    void tokenize_respectsSingleQuotes() {
        List<String> tokens = importer.tokenize("curl -H 'Content-Type: application/json' https://example.com");

        assertThat(tokens).containsExactly("curl", "-H", "Content-Type: application/json", "https://example.com");
    }

    @Test
    void tokenize_respectsDoubleQuotes() {
        List<String> tokens = importer.tokenize("curl -d \"hello world\" https://example.com");

        assertThat(tokens).containsExactly("curl", "-d", "hello world", "https://example.com");
    }

    // ─── Normalize Command ───

    @Test
    void normalizeCommand_collapsesBackslashNewlines() {
        String result = importer.normalizeCommand("curl \\\n  -X POST \\\n  https://example.com");

        assertThat(result).isEqualTo("curl -X POST https://example.com");
    }

    // ─── No-Value Flags Ignored ───

    @Test
    void parseCurl_noValueFlagsIgnored() {
        Request request = importer.parseCurl(
                "curl --compressed -k -L -s https://api.example.com/users", folder, 0);

        assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(request.getUrl()).isEqualTo("https://api.example.com/users");
    }

    // ─── XML Content Type Detection ───

    @Test
    void parseCurl_xmlContentType() {
        Request request = importer.parseCurl(
                "curl -X POST -H 'Content-Type: application/xml' -d '<root/>' https://api.example.com/data",
                folder, 0);

        assertThat(request.getBody().getBodyType()).isEqualTo(BodyType.RAW_XML);
    }
}
