package com.codeops.courier.service;

import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.entity.enums.ScriptType;
import com.codeops.courier.exception.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostmanImporterTest {

    private PostmanImporter importer;
    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        importer = new PostmanImporter(new ObjectMapper());
    }

    // ─── Basic Collection Parsing ───

    @Test
    void parse_basicCollection() {
        String json = """
                {
                  "info": {
                    "name": "My API",
                    "description": "Test API collection",
                    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                  },
                  "item": []
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);

        assertThat(result.collection().getName()).isEqualTo("My API");
        assertThat(result.collection().getDescription()).isEqualTo("Test API collection");
        assertThat(result.collection().getTeamId()).isEqualTo(TEAM_ID);
        assertThat(result.collection().getCreatedBy()).isEqualTo(USER_ID);
        assertThat(result.foldersImported()).isEqualTo(0);
        assertThat(result.requestsImported()).isEqualTo(0);
    }

    // ─── Folder Parsing ───

    @Test
    void parse_withFolder() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [
                    {
                      "name": "Users",
                      "item": [
                        {
                          "name": "Get Users",
                          "request": {
                            "method": "GET",
                            "url": { "raw": "https://api.example.com/users" }
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);

        assertThat(result.foldersImported()).isEqualTo(1);
        assertThat(result.requestsImported()).isEqualTo(1);
        Collection collection = result.collection();
        assertThat(collection.getFolders()).hasSize(1);
        Folder folder = collection.getFolders().get(0);
        assertThat(folder.getName()).isEqualTo("Users");
        assertThat(folder.getRequests()).hasSize(1);
        assertThat(folder.getRequests().get(0).getName()).isEqualTo("Get Users");
    }

    // ─── Nested Folders ───

    @Test
    void parse_nestedFolders() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [
                    {
                      "name": "API",
                      "item": [
                        {
                          "name": "v1",
                          "item": [
                            {
                              "name": "Get Data",
                              "request": { "method": "GET", "url": { "raw": "https://api.example.com/v1/data" } }
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);

        assertThat(result.foldersImported()).isEqualTo(2);
        Folder apiFolder = result.collection().getFolders().get(0);
        assertThat(apiFolder.getName()).isEqualTo("API");
        assertThat(apiFolder.getSubFolders()).hasSize(1);
        Folder v1Folder = apiFolder.getSubFolders().get(0);
        assertThat(v1Folder.getName()).isEqualTo("v1");
        assertThat(v1Folder.getRequests()).hasSize(1);
    }

    // ─── Request with Headers ───

    @Test
    void parse_requestWithHeaders() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [
                    {
                      "name": "With Headers",
                      "request": {
                        "method": "GET",
                        "url": { "raw": "https://api.example.com/users" },
                        "header": [
                          { "key": "Accept", "value": "application/json" },
                          { "key": "X-Custom", "value": "test", "disabled": true }
                        ]
                      }
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getHeaders()).hasSize(2);
        assertThat(request.getHeaders().get(0).getHeaderKey()).isEqualTo("Accept");
        assertThat(request.getHeaders().get(0).isEnabled()).isTrue();
        assertThat(request.getHeaders().get(1).getHeaderKey()).isEqualTo("X-Custom");
        assertThat(request.getHeaders().get(1).isEnabled()).isFalse();
    }

    // ─── Request with Raw JSON Body ───

    @Test
    void parse_requestWithRawJsonBody() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [
                    {
                      "name": "Create User",
                      "request": {
                        "method": "POST",
                        "url": { "raw": "https://api.example.com/users" },
                        "body": {
                          "mode": "raw",
                          "raw": "{\\\"name\\\":\\\"test\\\"}",
                          "options": { "raw": { "language": "json" } }
                        }
                      }
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getBody()).isNotNull();
        assertThat(request.getBody().getBodyType()).isEqualTo(BodyType.RAW_JSON);
    }

    // ─── Request with Form Data Body ───

    @Test
    void parse_requestWithFormDataBody() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [
                    {
                      "name": "Upload",
                      "request": {
                        "method": "POST",
                        "url": { "raw": "https://api.example.com/upload" },
                        "body": {
                          "mode": "formdata",
                          "formdata": [
                            { "key": "file", "type": "file", "src": "photo.jpg" }
                          ]
                        }
                      }
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getBody()).isNotNull();
        assertThat(request.getBody().getBodyType()).isEqualTo(BodyType.FORM_DATA);
    }

    // ─── Request with GraphQL Body ───

    @Test
    void parse_requestWithGraphqlBody() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [
                    {
                      "name": "GraphQL Query",
                      "request": {
                        "method": "POST",
                        "url": { "raw": "https://api.example.com/graphql" },
                        "body": {
                          "mode": "graphql",
                          "graphql": {
                            "query": "{ users { id name } }",
                            "variables": "{}"
                          }
                        }
                      }
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getBody()).isNotNull();
        assertThat(request.getBody().getBodyType()).isEqualTo(BodyType.GRAPHQL);
        assertThat(request.getBody().getGraphqlQuery()).isEqualTo("{ users { id name } }");
    }

    // ─── Request with Bearer Auth ───

    @Test
    void parse_requestWithBearerAuth() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [
                    {
                      "name": "Authenticated",
                      "request": {
                        "method": "GET",
                        "url": { "raw": "https://api.example.com/me" },
                        "auth": {
                          "type": "bearer",
                          "bearer": [
                            { "key": "token", "value": "my-jwt-token" }
                          ]
                        }
                      }
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getAuth()).isNotNull();
        assertThat(request.getAuth().getAuthType()).isEqualTo(AuthType.BEARER_TOKEN);
        assertThat(request.getAuth().getBearerToken()).isEqualTo("my-jwt-token");
    }

    // ─── Request with Basic Auth ───

    @Test
    void parse_requestWithBasicAuth() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [
                    {
                      "name": "Basic Auth",
                      "request": {
                        "method": "GET",
                        "url": { "raw": "https://api.example.com/admin" },
                        "auth": {
                          "type": "basic",
                          "basic": [
                            { "key": "username", "value": "admin" },
                            { "key": "password", "value": "secret" }
                          ]
                        }
                      }
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getAuth()).isNotNull();
        assertThat(request.getAuth().getAuthType()).isEqualTo(AuthType.BASIC_AUTH);
        assertThat(request.getAuth().getBasicUsername()).isEqualTo("admin");
        assertThat(request.getAuth().getBasicPassword()).isEqualTo("secret");
    }

    // ─── Scripts (Pre-request + Post-response) ───

    @Test
    void parse_requestWithScripts() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [
                    {
                      "name": "With Scripts",
                      "request": {
                        "method": "GET",
                        "url": { "raw": "https://api.example.com/test" }
                      },
                      "event": [
                        {
                          "listen": "prerequest",
                          "script": { "exec": ["console.log('pre');", "pm.variables.set('key', 'value');"] }
                        },
                        {
                          "listen": "test",
                          "script": { "exec": ["pm.test('Status OK', function() {", "  pm.response.to.have.status(200);", "});"] }
                        }
                      ]
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getScripts()).hasSize(2);
        assertThat(request.getScripts().get(0).getScriptType()).isEqualTo(ScriptType.PRE_REQUEST);
        assertThat(request.getScripts().get(0).getContent()).contains("console.log('pre');");
        assertThat(request.getScripts().get(1).getScriptType()).isEqualTo(ScriptType.POST_RESPONSE);
        assertThat(request.getScripts().get(1).getContent()).contains("pm.test");
    }

    // ─── Collection Variables ───

    @Test
    void parse_collectionVariables() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [],
                  "variable": [
                    { "key": "baseUrl", "value": "https://api.example.com" },
                    { "key": "apiKey", "value": "secret123" }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);

        assertThat(result.environmentsImported()).isEqualTo(2);
        assertThat(result.collection().getVariables()).hasSize(2);
        assertThat(result.collection().getVariables().get(0).getVariableKey()).isEqualTo("baseUrl");
        assertThat(result.collection().getVariables().get(0).getScope()).isEqualTo("COLLECTION");
    }

    // ─── Query Params from URL ───

    @Test
    void parse_queryParamsFromUrl() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [
                    {
                      "name": "Search",
                      "request": {
                        "method": "GET",
                        "url": {
                          "raw": "https://api.example.com/search?q=test",
                          "query": [
                            { "key": "q", "value": "test" },
                            { "key": "limit", "value": "10", "disabled": true }
                          ]
                        }
                      }
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getParams()).hasSize(2);
        assertThat(request.getParams().get(0).getParamKey()).isEqualTo("q");
        assertThat(request.getParams().get(1).isEnabled()).isFalse();
    }

    // ─── Root-Level Requests Get Default Folder ───

    @Test
    void parse_rootLevelRequestsGetDefaultFolder() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [
                    {
                      "name": "Root Request",
                      "request": {
                        "method": "GET",
                        "url": { "raw": "https://api.example.com/root" }
                      }
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);

        assertThat(result.foldersImported()).isEqualTo(1);
        assertThat(result.collection().getFolders()).hasSize(1);
        assertThat(result.collection().getFolders().get(0).getName()).isEqualTo("Requests");
    }

    // ─── Collection-Level Auth ───

    @Test
    void parse_collectionLevelAuth() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [],
                  "auth": {
                    "type": "bearer",
                    "bearer": [
                      { "key": "token", "value": "collection-token" }
                    ]
                  }
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);

        assertThat(result.collection().getAuthType()).isEqualTo(AuthType.BEARER_TOKEN);
    }

    // ─── Collection-Level Events ───

    @Test
    void parse_collectionLevelEvents() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [],
                  "event": [
                    {
                      "listen": "prerequest",
                      "script": { "exec": ["console.log('collection pre');"] }
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);

        assertThat(result.collection().getPreRequestScript()).contains("collection pre");
    }

    // ─── Invalid JSON ───

    @Test
    void parse_invalidJson_throws() {
        assertThatThrownBy(() -> importer.parse("not json", TEAM_ID, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid JSON");
    }

    // ─── Missing Info Field ───

    @Test
    void parse_missingInfoField_throws() {
        assertThatThrownBy(() -> importer.parse("{\"item\": []}", TEAM_ID, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("missing 'info' field");
    }

    // ─── URL as String (Not Object) ───

    @Test
    void parse_urlAsString() {
        String json = """
                {
                  "info": { "name": "Test", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
                  "item": [
                    {
                      "name": "Simple URL",
                      "request": {
                        "method": "GET",
                        "url": "https://api.example.com/users"
                      }
                    }
                  ]
                }
                """;

        PostmanImporter.PostmanImportResult result = importer.parse(json, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getUrl()).isEqualTo("https://api.example.com/users");
    }
}
