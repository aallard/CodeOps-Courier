package com.codeops.courier.service;

import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiImporterTest {

    private OpenApiImporter importer;
    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        importer = new OpenApiImporter(new ObjectMapper());
    }

    // ─── Basic JSON Spec ───

    @Test
    void parse_basicJsonSpec() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "Pet Store", "description": "A pet store API", "version": "1.0.0" },
                  "paths": {
                    "/pets": {
                      "get": {
                        "tags": ["pets"],
                        "summary": "List all pets",
                        "operationId": "listPets"
                      }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);

        assertThat(result.collection().getName()).isEqualTo("Pet Store");
        assertThat(result.collection().getDescription()).isEqualTo("A pet store API");
        assertThat(result.foldersImported()).isEqualTo(1);
        assertThat(result.requestsImported()).isEqualTo(1);
    }

    // ─── YAML Spec ───

    @Test
    void parse_yamlSpec() {
        String yaml = """
                openapi: "3.0.3"
                info:
                  title: Pet Store YAML
                  version: "1.0.0"
                paths:
                  /pets:
                    get:
                      tags:
                        - pets
                      summary: List all pets
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(yaml, true, TEAM_ID, USER_ID);

        assertThat(result.collection().getName()).isEqualTo("Pet Store YAML");
        assertThat(result.requestsImported()).isEqualTo(1);
    }

    // ─── Tag-Based Folder Grouping ───

    @Test
    void parse_tagBasedFolderGrouping() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "paths": {
                    "/users": {
                      "get": { "tags": ["Users"], "summary": "List users" },
                      "post": { "tags": ["Users"], "summary": "Create user" }
                    },
                    "/orders": {
                      "get": { "tags": ["Orders"], "summary": "List orders" }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);

        assertThat(result.foldersImported()).isEqualTo(2);
        assertThat(result.requestsImported()).isEqualTo(3);

        Collection collection = result.collection();
        Folder usersFolder = collection.getFolders().stream()
                .filter(f -> "Users".equals(f.getName())).findFirst().orElseThrow();
        assertThat(usersFolder.getRequests()).hasSize(2);

        Folder ordersFolder = collection.getFolders().stream()
                .filter(f -> "Orders".equals(f.getName())).findFirst().orElseThrow();
        assertThat(ordersFolder.getRequests()).hasSize(1);
    }

    // ─── Default Folder for Untagged Operations ───

    @Test
    void parse_defaultFolderForUntagged() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "paths": {
                    "/health": {
                      "get": { "summary": "Health check" }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);

        assertThat(result.foldersImported()).isEqualTo(1);
        assertThat(result.collection().getFolders().get(0).getName()).isEqualTo("Default");
    }

    // ─── Base URL from Servers ───

    @Test
    void parse_baseUrlFromServers() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "servers": [{ "url": "https://api.example.com/v1" }],
                  "paths": {
                    "/users": {
                      "get": { "tags": ["Users"], "summary": "List users" }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getUrl()).isEqualTo("https://api.example.com/v1/users");
    }

    // ─── Query and Header Parameters ───

    @Test
    void parse_queryAndHeaderParameters() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "paths": {
                    "/search": {
                      "get": {
                        "tags": ["Search"],
                        "summary": "Search items",
                        "parameters": [
                          { "name": "q", "in": "query", "required": true, "schema": { "type": "string", "example": "test" } },
                          { "name": "X-Request-Id", "in": "header", "required": false, "schema": { "type": "string" } }
                        ]
                      }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getParams()).hasSize(1);
        assertThat(request.getParams().get(0).getParamKey()).isEqualTo("q");
        assertThat(request.getParams().get(0).getParamValue()).isEqualTo("test");
        assertThat(request.getParams().get(0).isEnabled()).isTrue();

        assertThat(request.getHeaders()).hasSize(1);
        assertThat(request.getHeaders().get(0).getHeaderKey()).isEqualTo("X-Request-Id");
        assertThat(request.getHeaders().get(0).isEnabled()).isFalse();
    }

    // ─── Request Body (JSON) ───

    @Test
    void parse_requestBodyJson() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "paths": {
                    "/users": {
                      "post": {
                        "tags": ["Users"],
                        "summary": "Create user",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": { "type": "object", "properties": { "name": { "type": "string" } } },
                              "example": { "name": "John" }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getBody()).isNotNull();
        assertThat(request.getBody().getBodyType()).isEqualTo(BodyType.RAW_JSON);
        assertThat(request.getBody().getRawContent()).contains("John");
    }

    // ─── Request Body (Form) ───

    @Test
    void parse_requestBodyForm() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "paths": {
                    "/upload": {
                      "post": {
                        "tags": ["Upload"],
                        "summary": "Upload file",
                        "requestBody": {
                          "content": {
                            "multipart/form-data": {
                              "schema": { "type": "object", "properties": { "file": { "type": "string", "format": "binary" } } }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getBody()).isNotNull();
        assertThat(request.getBody().getBodyType()).isEqualTo(BodyType.FORM_DATA);
    }

    // ─── Security Scheme: Bearer ───

    @Test
    void parse_securitySchemeBearerToken() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "components": {
                    "securitySchemes": {
                      "bearerAuth": { "type": "http", "scheme": "bearer", "bearerFormat": "JWT" }
                    }
                  },
                  "paths": {
                    "/me": {
                      "get": {
                        "tags": ["Auth"],
                        "summary": "Get current user",
                        "security": [{ "bearerAuth": [] }]
                      }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getAuth()).isNotNull();
        assertThat(request.getAuth().getAuthType()).isEqualTo(AuthType.BEARER_TOKEN);
    }

    // ─── Security Scheme: API Key ───

    @Test
    void parse_securitySchemeApiKey() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "components": {
                    "securitySchemes": {
                      "apiKey": { "type": "apiKey", "name": "X-API-Key", "in": "header" }
                    }
                  },
                  "paths": {
                    "/data": {
                      "get": {
                        "tags": ["Data"],
                        "summary": "Get data",
                        "security": [{ "apiKey": [] }]
                      }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getAuth()).isNotNull();
        assertThat(request.getAuth().getAuthType()).isEqualTo(AuthType.API_KEY);
        assertThat(request.getAuth().getApiKeyHeader()).isEqualTo("X-API-Key");
        assertThat(request.getAuth().getApiKeyAddTo()).isEqualTo("header");
    }

    // ─── Security Scheme: OAuth2 ───

    @Test
    void parse_securitySchemeOAuth2() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "components": {
                    "securitySchemes": {
                      "oauth2": {
                        "type": "oauth2",
                        "flows": {
                          "authorizationCode": {
                            "authorizationUrl": "https://auth.example.com/authorize",
                            "tokenUrl": "https://auth.example.com/token",
                            "scopes": { "read": "Read access" }
                          }
                        }
                      }
                    }
                  },
                  "paths": {
                    "/protected": {
                      "get": {
                        "tags": ["Protected"],
                        "summary": "Protected resource",
                        "security": [{ "oauth2": ["read"] }]
                      }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getAuth()).isNotNull();
        assertThat(request.getAuth().getAuthType()).isEqualTo(AuthType.OAUTH2_AUTHORIZATION_CODE);
        assertThat(request.getAuth().getOauth2AuthUrl()).isEqualTo("https://auth.example.com/authorize");
        assertThat(request.getAuth().getOauth2TokenUrl()).isEqualTo("https://auth.example.com/token");
    }

    // ─── Server Variables as Collection Variables ───

    @Test
    void parse_serverVariablesImported() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "servers": [
                    {
                      "url": "https://{host}/api/{version}",
                      "variables": {
                        "host": { "default": "api.example.com" },
                        "version": { "default": "v1" }
                      }
                    }
                  ],
                  "paths": {}
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);

        assertThat(result.environmentsImported()).isEqualTo(2);
        assertThat(result.collection().getVariables()).hasSize(2);
    }

    // ─── OperationId as Request Name ───

    @Test
    void parse_operationIdAsRequestName() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "paths": {
                    "/users": {
                      "get": {
                        "tags": ["Users"],
                        "operationId": "getUsers",
                        "summary": "List all users"
                      }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getName()).isEqualTo("getUsers");
    }

    // ─── Multiple Methods on Same Path ───

    @Test
    void parse_multipleMethodsOnSamePath() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "paths": {
                    "/users": {
                      "get": { "tags": ["Users"], "summary": "List users" },
                      "post": { "tags": ["Users"], "summary": "Create user" },
                      "delete": { "tags": ["Users"], "summary": "Delete all users" }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);

        assertThat(result.requestsImported()).isEqualTo(3);
        Folder folder = result.collection().getFolders().get(0);
        assertThat(folder.getRequests()).hasSize(3);
    }

    // ─── Invalid JSON ───

    @Test
    void parse_invalidJson_throws() {
        assertThatThrownBy(() -> importer.parse("not valid json", false, TEAM_ID, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid JSON");
    }

    // ─── Not OpenAPI 3.x ───

    @Test
    void parse_notOpenApi3_throws() {
        String json = """
                {
                  "swagger": "2.0",
                  "info": { "title": "Old API", "version": "1.0.0" }
                }
                """;

        assertThatThrownBy(() -> importer.parse(json, false, TEAM_ID, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Not an OpenAPI 3.x specification");
    }

    // ─── Path-Level Parameters ───

    @Test
    void parse_pathLevelParameters() {
        String json = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "API", "version": "1.0.0" },
                  "paths": {
                    "/users/{userId}": {
                      "parameters": [
                        { "name": "userId", "in": "path", "required": true, "schema": { "type": "string" } }
                      ],
                      "get": {
                        "tags": ["Users"],
                        "summary": "Get user by ID"
                      }
                    }
                  }
                }
                """;

        OpenApiImporter.OpenApiImportResult result = importer.parse(json, false, TEAM_ID, USER_ID);
        Request request = result.collection().getFolders().get(0).getRequests().get(0);

        assertThat(request.getParams()).hasSize(1);
        assertThat(request.getParams().get(0).getParamKey()).isEqualTo("userId");
        assertThat(request.getParams().get(0).isEnabled()).isTrue();
    }
}
