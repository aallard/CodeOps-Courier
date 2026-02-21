package com.codeops.courier.service;

import com.codeops.courier.dto.response.ExportCollectionResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.EnvironmentVariable;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.RequestAuth;
import com.codeops.courier.entity.RequestBody;
import com.codeops.courier.entity.RequestHeader;
import com.codeops.courier.entity.RequestParam;
import com.codeops.courier.entity.RequestScript;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.ScriptType;
import com.codeops.courier.repository.FolderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for exporting collections in various formats including Postman v2.1,
 * OpenAPI 3.0.3, and native Courier JSON.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExportService {

    private final CollectionService collectionService;
    private final FolderRepository folderRepository;
    private final ObjectMapper objectMapper;

    /**
     * Exports a collection as Postman Collection v2.1 JSON.
     *
     * @param collectionId the collection ID to export
     * @param teamId       the team ID for access validation
     * @return the export response with Postman JSON content
     */
    public ExportCollectionResponse exportAsPostman(UUID collectionId, UUID teamId) {
        Collection collection = collectionService.findCollectionByIdAndTeam(collectionId, teamId);
        List<Folder> rootFolders = folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId);

        Map<String, Object> postman = new LinkedHashMap<>();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("_postman_id", collection.getId().toString());
        info.put("name", collection.getName());
        if (collection.getDescription() != null) {
            info.put("description", collection.getDescription());
        }
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        postman.put("info", info);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Folder folder : rootFolders) {
            items.add(buildPostmanFolder(folder));
        }
        postman.put("item", items);

        if (!collection.getVariables().isEmpty()) {
            List<Map<String, Object>> variables = new ArrayList<>();
            for (EnvironmentVariable var : collection.getVariables()) {
                Map<String, Object> varMap = new LinkedHashMap<>();
                varMap.put("key", var.getVariableKey());
                varMap.put("value", var.getVariableValue() != null ? var.getVariableValue() : "");
                varMap.put("type", "string");
                varMap.put("disabled", !var.isEnabled());
                variables.add(varMap);
            }
            postman.put("variable", variables);
        }

        if (collection.getAuthType() != null && collection.getAuthType() != AuthType.NO_AUTH) {
            postman.put("auth", buildPostmanAuth(collection.getAuthType(), null));
        }

        String content = serializeToJson(postman);
        String filename = sanitizeFilename(collection.getName()) + ".postman_collection.json";

        log.info("Exported collection '{}' as Postman v2.1", collection.getName());
        return new ExportCollectionResponse("POSTMAN_V2_1", content, filename);
    }

    /**
     * Exports a collection as OpenAPI 3.0.3 YAML.
     * This is a best-effort conversion as not all Courier features map to OpenAPI.
     *
     * @param collectionId the collection ID to export
     * @param teamId       the team ID for access validation
     * @return the export response with OpenAPI YAML content
     */
    public ExportCollectionResponse exportAsOpenApi(UUID collectionId, UUID teamId) {
        Collection collection = collectionService.findCollectionByIdAndTeam(collectionId, teamId);
        List<Folder> allFolders = folderRepository.findByCollectionIdOrderBySortOrder(collectionId);

        StringBuilder yaml = new StringBuilder();
        yaml.append("openapi: 3.0.3\n");
        yaml.append("info:\n");
        yaml.append("  title: ").append(escapeYaml(collection.getName())).append("\n");
        if (collection.getDescription() != null) {
            yaml.append("  description: ").append(escapeYaml(collection.getDescription())).append("\n");
        }
        yaml.append("  version: 1.0.0\n");

        Map<String, Map<String, List<Request>>> pathOperations = new LinkedHashMap<>();
        for (Folder folder : allFolders) {
            for (Request req : folder.getRequests()) {
                String path = extractPath(req.getUrl());
                pathOperations
                        .computeIfAbsent(path, k -> new LinkedHashMap<>())
                        .computeIfAbsent(req.getMethod().name().toLowerCase(), k -> new ArrayList<>())
                        .add(req);
            }
        }

        if (pathOperations.isEmpty()) {
            yaml.append("paths: {}\n");
        } else {
            yaml.append("paths:\n");
            for (Map.Entry<String, Map<String, List<Request>>> pathEntry : pathOperations.entrySet()) {
                yaml.append("  ").append(escapeYaml(pathEntry.getKey())).append(":\n");
                for (Map.Entry<String, List<Request>> opEntry : pathEntry.getValue().entrySet()) {
                    Request req = opEntry.getValue().get(0);
                    yaml.append("    ").append(opEntry.getKey()).append(":\n");
                    yaml.append("      summary: ").append(escapeYaml(req.getName())).append("\n");
                    if (req.getDescription() != null) {
                        yaml.append("      description: ").append(escapeYaml(req.getDescription())).append("\n");
                    }

                    List<RequestParam> enabledParams = req.getParams().stream()
                            .filter(RequestParam::isEnabled)
                            .toList();
                    List<RequestHeader> enabledHeaders = req.getHeaders().stream()
                            .filter(RequestHeader::isEnabled)
                            .toList();

                    if (!enabledParams.isEmpty() || !enabledHeaders.isEmpty()) {
                        yaml.append("      parameters:\n");
                        for (RequestParam param : enabledParams) {
                            yaml.append("        - name: ").append(escapeYaml(param.getParamKey())).append("\n");
                            yaml.append("          in: query\n");
                            yaml.append("          schema:\n");
                            yaml.append("            type: string\n");
                        }
                        for (RequestHeader header : enabledHeaders) {
                            yaml.append("        - name: ").append(escapeYaml(header.getHeaderKey())).append("\n");
                            yaml.append("          in: header\n");
                            yaml.append("          schema:\n");
                            yaml.append("            type: string\n");
                        }
                    }

                    if (req.getBody() != null && req.getBody().getBodyType() != BodyType.NONE) {
                        yaml.append("      requestBody:\n");
                        yaml.append("        content:\n");
                        String mediaType = mapBodyTypeToMediaType(req.getBody().getBodyType());
                        yaml.append("          ").append(mediaType).append(":\n");
                        yaml.append("            schema:\n");
                        yaml.append("              type: object\n");
                    }

                    yaml.append("      responses:\n");
                    yaml.append("        '200':\n");
                    yaml.append("          description: Successful response\n");
                }
            }
        }

        if (collection.getAuthType() != null && collection.getAuthType() != AuthType.NO_AUTH
                && collection.getAuthType() != AuthType.INHERIT_FROM_PARENT) {
            yaml.append("components:\n");
            yaml.append("  securitySchemes:\n");
            appendSecurityScheme(yaml, collection.getAuthType());
        }

        String filename = sanitizeFilename(collection.getName()) + ".openapi.yaml";
        log.info("Exported collection '{}' as OpenAPI 3.0.3", collection.getName());
        return new ExportCollectionResponse("OPENAPI_3", yaml.toString(), filename);
    }

    /**
     * Exports a collection as native Courier JSON format.
     * This format includes all nested data for backup/restore and collection sharing.
     *
     * @param collectionId the collection ID to export
     * @param teamId       the team ID for access validation
     * @return the export response with native JSON content
     */
    public ExportCollectionResponse exportAsNative(UUID collectionId, UUID teamId) {
        Collection collection = collectionService.findCollectionByIdAndTeam(collectionId, teamId);
        List<Folder> rootFolders = folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(collectionId);

        Map<String, Object> native_ = new LinkedHashMap<>();
        native_.put("format", "COURIER_NATIVE");
        native_.put("version", "1.0");
        native_.put("name", collection.getName());
        native_.put("description", collection.getDescription());
        native_.put("preRequestScript", collection.getPreRequestScript());
        native_.put("postResponseScript", collection.getPostResponseScript());
        if (collection.getAuthType() != null) {
            native_.put("authType", collection.getAuthType().name());
        }
        native_.put("authConfig", collection.getAuthConfig());

        List<Map<String, Object>> folders = new ArrayList<>();
        for (Folder folder : rootFolders) {
            folders.add(buildNativeFolder(folder));
        }
        native_.put("folders", folders);

        List<Map<String, Object>> variables = new ArrayList<>();
        for (EnvironmentVariable var : collection.getVariables()) {
            Map<String, Object> varMap = new LinkedHashMap<>();
            varMap.put("key", var.getVariableKey());
            varMap.put("value", var.getVariableValue());
            varMap.put("isSecret", var.isSecret());
            varMap.put("isEnabled", var.isEnabled());
            varMap.put("scope", var.getScope());
            variables.add(varMap);
        }
        native_.put("variables", variables);

        String content = serializeToJson(native_);
        String filename = sanitizeFilename(collection.getName()) + ".courier.json";

        log.info("Exported collection '{}' as native Courier format", collection.getName());
        return new ExportCollectionResponse("COURIER_NATIVE", content, filename);
    }

    private Map<String, Object> buildPostmanFolder(Folder folder) {
        Map<String, Object> folderMap = new LinkedHashMap<>();
        folderMap.put("name", folder.getName());
        if (folder.getDescription() != null) {
            folderMap.put("description", folder.getDescription());
        }

        List<Map<String, Object>> items = new ArrayList<>();

        for (Request req : folder.getRequests()) {
            items.add(buildPostmanRequest(req));
        }

        for (Folder subFolder : folder.getSubFolders()) {
            items.add(buildPostmanFolder(subFolder));
        }

        folderMap.put("item", items);

        List<Map<String, Object>> events = new ArrayList<>();
        if (folder.getPreRequestScript() != null) {
            events.add(buildPostmanEvent("prerequest", folder.getPreRequestScript()));
        }
        if (folder.getPostResponseScript() != null) {
            events.add(buildPostmanEvent("test", folder.getPostResponseScript()));
        }
        if (!events.isEmpty()) {
            folderMap.put("event", events);
        }

        return folderMap;
    }

    private Map<String, Object> buildPostmanRequest(Request req) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", req.getName());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", req.getMethod().name());

        if (!req.getHeaders().isEmpty()) {
            List<Map<String, Object>> headers = new ArrayList<>();
            for (RequestHeader h : req.getHeaders()) {
                Map<String, Object> header = new LinkedHashMap<>();
                header.put("key", h.getHeaderKey());
                header.put("value", h.getHeaderValue() != null ? h.getHeaderValue() : "");
                if (h.getDescription() != null) {
                    header.put("description", h.getDescription());
                }
                header.put("disabled", !h.isEnabled());
                headers.add(header);
            }
            request.put("header", headers);
        }

        Map<String, Object> url = new LinkedHashMap<>();
        url.put("raw", req.getUrl());

        if (!req.getParams().isEmpty()) {
            List<Map<String, Object>> queryParams = new ArrayList<>();
            for (RequestParam p : req.getParams()) {
                Map<String, Object> param = new LinkedHashMap<>();
                param.put("key", p.getParamKey());
                param.put("value", p.getParamValue() != null ? p.getParamValue() : "");
                if (p.getDescription() != null) {
                    param.put("description", p.getDescription());
                }
                param.put("disabled", !p.isEnabled());
                queryParams.add(param);
            }
            url.put("query", queryParams);
        }

        request.put("url", url);

        if (req.getBody() != null && req.getBody().getBodyType() != BodyType.NONE) {
            request.put("body", buildPostmanBody(req.getBody()));
        }

        if (req.getAuth() != null && req.getAuth().getAuthType() != AuthType.NO_AUTH) {
            request.put("auth", buildPostmanAuth(req.getAuth().getAuthType(), req.getAuth()));
        }

        item.put("request", request);

        List<Map<String, Object>> events = new ArrayList<>();
        for (RequestScript script : req.getScripts()) {
            if (script.getScriptType() == ScriptType.PRE_REQUEST) {
                events.add(buildPostmanEvent("prerequest", script.getContent()));
            } else if (script.getScriptType() == ScriptType.POST_RESPONSE) {
                events.add(buildPostmanEvent("test", script.getContent()));
            }
        }
        if (!events.isEmpty()) {
            item.put("event", events);
        }

        return item;
    }

    private Map<String, Object> buildPostmanBody(RequestBody body) {
        Map<String, Object> bodyMap = new LinkedHashMap<>();
        switch (body.getBodyType()) {
            case RAW_JSON -> {
                bodyMap.put("mode", "raw");
                bodyMap.put("raw", body.getRawContent() != null ? body.getRawContent() : "");
                Map<String, Object> options = new LinkedHashMap<>();
                Map<String, String> rawOpt = new LinkedHashMap<>();
                rawOpt.put("language", "json");
                options.put("raw", rawOpt);
                bodyMap.put("options", options);
            }
            case RAW_XML -> {
                bodyMap.put("mode", "raw");
                bodyMap.put("raw", body.getRawContent() != null ? body.getRawContent() : "");
                Map<String, Object> options = new LinkedHashMap<>();
                Map<String, String> rawOpt = new LinkedHashMap<>();
                rawOpt.put("language", "xml");
                options.put("raw", rawOpt);
                bodyMap.put("options", options);
            }
            case RAW_HTML -> {
                bodyMap.put("mode", "raw");
                bodyMap.put("raw", body.getRawContent() != null ? body.getRawContent() : "");
                Map<String, Object> options = new LinkedHashMap<>();
                Map<String, String> rawOpt = new LinkedHashMap<>();
                rawOpt.put("language", "html");
                options.put("raw", rawOpt);
                bodyMap.put("options", options);
            }
            case RAW_TEXT -> {
                bodyMap.put("mode", "raw");
                bodyMap.put("raw", body.getRawContent() != null ? body.getRawContent() : "");
                Map<String, Object> options = new LinkedHashMap<>();
                Map<String, String> rawOpt = new LinkedHashMap<>();
                rawOpt.put("language", "text");
                options.put("raw", rawOpt);
                bodyMap.put("options", options);
            }
            case RAW_YAML -> {
                bodyMap.put("mode", "raw");
                bodyMap.put("raw", body.getRawContent() != null ? body.getRawContent() : "");
                Map<String, Object> options = new LinkedHashMap<>();
                Map<String, String> rawOpt = new LinkedHashMap<>();
                rawOpt.put("language", "yaml");
                options.put("raw", rawOpt);
                bodyMap.put("options", options);
            }
            case FORM_DATA, X_WWW_FORM_URLENCODED -> {
                bodyMap.put("mode", "urlencoded");
                bodyMap.put("urlencoded", body.getFormData() != null ? body.getFormData() : "");
            }
            case GRAPHQL -> {
                bodyMap.put("mode", "graphql");
                Map<String, String> graphql = new LinkedHashMap<>();
                graphql.put("query", body.getGraphqlQuery() != null ? body.getGraphqlQuery() : "");
                graphql.put("variables", body.getGraphqlVariables() != null ? body.getGraphqlVariables() : "");
                bodyMap.put("graphql", graphql);
            }
            case BINARY -> {
                bodyMap.put("mode", "file");
                Map<String, String> file = new LinkedHashMap<>();
                file.put("src", body.getBinaryFileName() != null ? body.getBinaryFileName() : "");
                bodyMap.put("file", file);
            }
            default -> bodyMap.put("mode", "none");
        }
        return bodyMap;
    }

    private Map<String, Object> buildPostmanAuth(AuthType authType, RequestAuth auth) {
        Map<String, Object> authMap = new LinkedHashMap<>();
        switch (authType) {
            case BEARER_TOKEN -> {
                authMap.put("type", "bearer");
                List<Map<String, String>> bearer = new ArrayList<>();
                Map<String, String> tokenEntry = new LinkedHashMap<>();
                tokenEntry.put("key", "token");
                tokenEntry.put("value", auth != null && auth.getBearerToken() != null ? auth.getBearerToken() : "");
                tokenEntry.put("type", "string");
                bearer.add(tokenEntry);
                authMap.put("bearer", bearer);
            }
            case BASIC_AUTH -> {
                authMap.put("type", "basic");
                List<Map<String, String>> basic = new ArrayList<>();
                Map<String, String> userEntry = new LinkedHashMap<>();
                userEntry.put("key", "username");
                userEntry.put("value", auth != null && auth.getBasicUsername() != null ? auth.getBasicUsername() : "");
                userEntry.put("type", "string");
                basic.add(userEntry);
                Map<String, String> passEntry = new LinkedHashMap<>();
                passEntry.put("key", "password");
                passEntry.put("value", auth != null && auth.getBasicPassword() != null ? auth.getBasicPassword() : "");
                passEntry.put("type", "string");
                basic.add(passEntry);
                authMap.put("basic", basic);
            }
            case API_KEY -> {
                authMap.put("type", "apikey");
                List<Map<String, String>> apikey = new ArrayList<>();
                Map<String, String> keyEntry = new LinkedHashMap<>();
                keyEntry.put("key", "key");
                keyEntry.put("value", auth != null && auth.getApiKeyHeader() != null ? auth.getApiKeyHeader() : "");
                keyEntry.put("type", "string");
                apikey.add(keyEntry);
                Map<String, String> valEntry = new LinkedHashMap<>();
                valEntry.put("key", "value");
                valEntry.put("value", auth != null && auth.getApiKeyValue() != null ? auth.getApiKeyValue() : "");
                valEntry.put("type", "string");
                apikey.add(valEntry);
                authMap.put("apikey", apikey);
            }
            default -> authMap.put("type", "noauth");
        }
        return authMap;
    }

    private Map<String, Object> buildPostmanEvent(String listen, String scriptContent) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("listen", listen);
        Map<String, Object> script = new LinkedHashMap<>();
        script.put("type", "text/javascript");
        script.put("exec", List.of(scriptContent));
        event.put("script", script);
        return event;
    }

    private Map<String, Object> buildNativeFolder(Folder folder) {
        Map<String, Object> folderMap = new LinkedHashMap<>();
        folderMap.put("name", folder.getName());
        folderMap.put("description", folder.getDescription());
        folderMap.put("sortOrder", folder.getSortOrder());
        folderMap.put("preRequestScript", folder.getPreRequestScript());
        folderMap.put("postResponseScript", folder.getPostResponseScript());
        if (folder.getAuthType() != null) {
            folderMap.put("authType", folder.getAuthType().name());
        }
        folderMap.put("authConfig", folder.getAuthConfig());

        List<Map<String, Object>> requests = new ArrayList<>();
        for (Request req : folder.getRequests()) {
            requests.add(buildNativeRequest(req));
        }
        folderMap.put("requests", requests);

        List<Map<String, Object>> subFolders = new ArrayList<>();
        for (Folder sub : folder.getSubFolders()) {
            subFolders.add(buildNativeFolder(sub));
        }
        folderMap.put("subFolders", subFolders);

        return folderMap;
    }

    private Map<String, Object> buildNativeRequest(Request req) {
        Map<String, Object> reqMap = new LinkedHashMap<>();
        reqMap.put("name", req.getName());
        reqMap.put("description", req.getDescription());
        reqMap.put("method", req.getMethod().name());
        reqMap.put("url", req.getUrl());
        reqMap.put("sortOrder", req.getSortOrder());

        List<Map<String, Object>> headers = new ArrayList<>();
        for (RequestHeader h : req.getHeaders()) {
            Map<String, Object> hMap = new LinkedHashMap<>();
            hMap.put("key", h.getHeaderKey());
            hMap.put("value", h.getHeaderValue());
            hMap.put("description", h.getDescription());
            hMap.put("isEnabled", h.isEnabled());
            headers.add(hMap);
        }
        reqMap.put("headers", headers);

        List<Map<String, Object>> params = new ArrayList<>();
        for (RequestParam p : req.getParams()) {
            Map<String, Object> pMap = new LinkedHashMap<>();
            pMap.put("key", p.getParamKey());
            pMap.put("value", p.getParamValue());
            pMap.put("description", p.getDescription());
            pMap.put("isEnabled", p.isEnabled());
            params.add(pMap);
        }
        reqMap.put("params", params);

        if (req.getBody() != null) {
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("bodyType", req.getBody().getBodyType().name());
            bodyMap.put("rawContent", req.getBody().getRawContent());
            bodyMap.put("formData", req.getBody().getFormData());
            bodyMap.put("graphqlQuery", req.getBody().getGraphqlQuery());
            bodyMap.put("graphqlVariables", req.getBody().getGraphqlVariables());
            bodyMap.put("binaryFileName", req.getBody().getBinaryFileName());
            reqMap.put("body", bodyMap);
        }

        if (req.getAuth() != null) {
            Map<String, Object> authMap = new LinkedHashMap<>();
            authMap.put("authType", req.getAuth().getAuthType().name());
            authMap.put("apiKeyHeader", req.getAuth().getApiKeyHeader());
            authMap.put("apiKeyValue", req.getAuth().getApiKeyValue());
            authMap.put("apiKeyAddTo", req.getAuth().getApiKeyAddTo());
            authMap.put("bearerToken", req.getAuth().getBearerToken());
            authMap.put("basicUsername", req.getAuth().getBasicUsername());
            authMap.put("basicPassword", req.getAuth().getBasicPassword());
            authMap.put("oauth2GrantType", req.getAuth().getOauth2GrantType());
            authMap.put("oauth2AuthUrl", req.getAuth().getOauth2AuthUrl());
            authMap.put("oauth2TokenUrl", req.getAuth().getOauth2TokenUrl());
            authMap.put("oauth2ClientId", req.getAuth().getOauth2ClientId());
            authMap.put("oauth2ClientSecret", req.getAuth().getOauth2ClientSecret());
            authMap.put("oauth2Scope", req.getAuth().getOauth2Scope());
            authMap.put("oauth2CallbackUrl", req.getAuth().getOauth2CallbackUrl());
            authMap.put("oauth2AccessToken", req.getAuth().getOauth2AccessToken());
            authMap.put("jwtSecret", req.getAuth().getJwtSecret());
            authMap.put("jwtPayload", req.getAuth().getJwtPayload());
            authMap.put("jwtAlgorithm", req.getAuth().getJwtAlgorithm());
            reqMap.put("auth", authMap);
        }

        List<Map<String, Object>> scripts = new ArrayList<>();
        for (RequestScript s : req.getScripts()) {
            Map<String, Object> sMap = new LinkedHashMap<>();
            sMap.put("scriptType", s.getScriptType().name());
            sMap.put("content", s.getContent());
            scripts.add(sMap);
        }
        reqMap.put("scripts", scripts);

        return reqMap;
    }

    private String serializeToJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize export content", e);
            return "{}";
        }
    }

    private String extractPath(String url) {
        if (url == null || url.isEmpty()) {
            return "/";
        }
        String path = url;
        if (path.contains("://")) {
            int schemeEnd = path.indexOf("://") + 3;
            int pathStart = path.indexOf('/', schemeEnd);
            path = pathStart >= 0 ? path.substring(pathStart) : "/";
        }
        int queryStart = path.indexOf('?');
        if (queryStart >= 0) {
            path = path.substring(0, queryStart);
        }
        return path.isEmpty() ? "/" : path;
    }

    private String escapeYaml(String value) {
        if (value == null) {
            return "''";
        }
        if (value.contains(":") || value.contains("#") || value.contains("'")
                || value.contains("\"") || value.contains("\n") || value.contains("{")
                || value.contains("}") || value.contains("[") || value.contains("]")) {
            return "'" + value.replace("'", "''") + "'";
        }
        return value;
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private String mapBodyTypeToMediaType(BodyType bodyType) {
        return switch (bodyType) {
            case RAW_JSON -> "application/json";
            case RAW_XML -> "application/xml";
            case RAW_HTML -> "text/html";
            case RAW_TEXT -> "text/plain";
            case RAW_YAML -> "application/yaml";
            case FORM_DATA -> "multipart/form-data";
            case X_WWW_FORM_URLENCODED -> "application/x-www-form-urlencoded";
            case GRAPHQL -> "application/json";
            case BINARY -> "application/octet-stream";
            default -> "application/json";
        };
    }

    private void appendSecurityScheme(StringBuilder yaml, AuthType authType) {
        switch (authType) {
            case BEARER_TOKEN -> {
                yaml.append("    bearerAuth:\n");
                yaml.append("      type: http\n");
                yaml.append("      scheme: bearer\n");
            }
            case BASIC_AUTH -> {
                yaml.append("    basicAuth:\n");
                yaml.append("      type: http\n");
                yaml.append("      scheme: basic\n");
            }
            case API_KEY -> {
                yaml.append("    apiKeyAuth:\n");
                yaml.append("      type: apiKey\n");
                yaml.append("      in: header\n");
                yaml.append("      name: X-API-Key\n");
            }
            default -> {
                // No security scheme for unsupported auth types
            }
        }
    }
}
