package com.codeops.courier.service;

import com.codeops.courier.entity.*;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Parses OpenAPI 3.x specifications (JSON or YAML) into Courier entities.
 * Uses tag-based folder grouping: each unique tag becomes a folder. Requests
 * without tags go into a "Default" folder. Supports parameter mapping
 * (path, query, header), request body content type detection, and security
 * scheme mapping (apiKey, http bearer, http basic, oauth2).
 */
@Component
@Slf4j
public class OpenApiImporter {

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    /**
     * Creates a new OpenApiImporter with JSON and YAML ObjectMappers.
     *
     * @param jsonMapper the Jackson ObjectMapper for JSON parsing
     */
    public OpenApiImporter(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Parses an OpenAPI 3.x specification string (JSON or YAML) into a Courier Collection.
     *
     * @param content   the OpenAPI spec content
     * @param isYaml    true if content is YAML, false if JSON
     * @param teamId    the team ID for the imported collection
     * @param createdBy the user ID of the importer
     * @return the result containing the populated Collection, counts, and warnings
     * @throws ValidationException if the content is invalid or not an OpenAPI 3.x spec
     */
    public OpenApiImportResult parse(String content, boolean isYaml, UUID teamId, UUID createdBy) {
        JsonNode root;
        try {
            root = isYaml ? yamlMapper.readTree(content) : jsonMapper.readTree(content);
        } catch (Exception e) {
            throw new ValidationException("Invalid " + (isYaml ? "YAML" : "JSON") + ": " + e.getMessage());
        }

        // Validate OpenAPI 3.x
        String openApiVersion = root.path("openapi").asText("");
        if (!openApiVersion.startsWith("3.")) {
            throw new ValidationException("Not an OpenAPI 3.x specification: openapi field is '" + openApiVersion + "'");
        }

        List<String> warnings = new ArrayList<>();

        // Build collection
        JsonNode infoNode = root.path("info");
        String title = infoNode.path("title").asText("Imported API");
        String description = infoNode.path("description").asText(null);

        Collection collection = new Collection();
        collection.setTeamId(teamId);
        collection.setName(title);
        collection.setDescription(description);
        collection.setCreatedBy(createdBy);
        collection.setFolders(new ArrayList<>());
        collection.setVariables(new ArrayList<>());

        // Extract base URL from servers
        String baseUrl = "";
        JsonNode serversNode = root.path("servers");
        if (serversNode.isArray() && !serversNode.isEmpty()) {
            baseUrl = serversNode.get(0).path("url").asText("");
        }

        // Extract global security schemes for reference
        Map<String, JsonNode> securitySchemes = new HashMap<>();
        JsonNode schemasNode = root.path("components").path("securitySchemes");
        if (schemasNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = schemasNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                securitySchemes.put(entry.getKey(), entry.getValue());
            }
        }

        // Parse paths and group by tag
        Map<String, Folder> tagFolders = new LinkedHashMap<>();
        int folderCount = 0;
        int requestCount = 0;

        JsonNode pathsNode = root.path("paths");
        if (pathsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> paths = pathsNode.fields();
            while (paths.hasNext()) {
                Map.Entry<String, JsonNode> pathEntry = paths.next();
                String path = pathEntry.getKey();
                JsonNode pathItem = pathEntry.getValue();

                // Parse path-level parameters
                List<JsonNode> pathParams = new ArrayList<>();
                JsonNode pathParamsNode = pathItem.path("parameters");
                if (pathParamsNode.isArray()) {
                    for (JsonNode p : pathParamsNode) {
                        pathParams.add(p);
                    }
                }

                Iterator<Map.Entry<String, JsonNode>> operations = pathItem.fields();
                while (operations.hasNext()) {
                    Map.Entry<String, JsonNode> opEntry = operations.next();
                    String httpMethod = opEntry.getKey().toUpperCase();

                    // Skip non-HTTP-method fields
                    if (!isHttpMethod(httpMethod)) continue;

                    JsonNode opNode = opEntry.getValue();

                    // Determine folder by first tag
                    String tagName = "Default";
                    JsonNode tagsNode = opNode.path("tags");
                    if (tagsNode.isArray() && !tagsNode.isEmpty()) {
                        tagName = tagsNode.get(0).asText("Default");
                    }

                    Folder folder = tagFolders.get(tagName);
                    if (folder == null) {
                        folder = new Folder();
                        folder.setName(tagName);
                        folder.setSortOrder(tagFolders.size());
                        folder.setCollection(collection);
                        folder.setSubFolders(new ArrayList<>());
                        folder.setRequests(new ArrayList<>());
                        tagFolders.put(tagName, folder);
                        collection.getFolders().add(folder);
                        folderCount++;
                    }

                    Request request = parseOperation(httpMethod, path, baseUrl, opNode,
                            pathParams, securitySchemes, folder, folder.getRequests().size(), warnings);
                    folder.getRequests().add(request);
                    requestCount++;
                }
            }
        }

        // Import server variables as collection variables
        int envCount = 0;
        if (serversNode.isArray() && !serversNode.isEmpty()) {
            JsonNode serverVars = serversNode.get(0).path("variables");
            if (serverVars.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> vars = serverVars.fields();
                while (vars.hasNext()) {
                    Map.Entry<String, JsonNode> entry = vars.next();
                    EnvironmentVariable envVar = new EnvironmentVariable();
                    envVar.setVariableKey(entry.getKey());
                    envVar.setVariableValue(entry.getValue().path("default").asText(""));
                    envVar.setScope("COLLECTION");
                    envVar.setEnabled(true);
                    envVar.setSecret(false);
                    envVar.setCollection(collection);
                    collection.getVariables().add(envVar);
                    envCount++;
                }
            }
        }

        log.info("Parsed OpenAPI spec '{}': {} folders, {} requests, {} variables",
                title, folderCount, requestCount, envCount);

        return new OpenApiImportResult(collection, folderCount, requestCount, envCount, warnings);
    }

    /**
     * Parses an individual OpenAPI operation into a Courier Request entity.
     *
     * @param httpMethod      the HTTP method (uppercase)
     * @param path            the URL path
     * @param baseUrl         the base server URL
     * @param opNode          the operation JSON node
     * @param pathParams      path-level parameters
     * @param securitySchemes the global security scheme definitions
     * @param folder          the target folder
     * @param sortOrder       the sort order within the folder
     * @param warnings        list to append warnings to
     * @return the populated Request entity
     */
    private Request parseOperation(String httpMethod, String path, String baseUrl,
                                   JsonNode opNode, List<JsonNode> pathParams,
                                   Map<String, JsonNode> securitySchemes,
                                   Folder folder, int sortOrder, List<String> warnings) {
        String operationId = opNode.path("operationId").asText("");
        String summary = opNode.path("summary").asText("");
        String description = opNode.path("description").asText(null);

        String requestName = !operationId.isEmpty() ? operationId
                : !summary.isEmpty() ? summary
                : httpMethod + " " + path;
        if (requestName.length() > 200) {
            requestName = requestName.substring(0, 200);
        }

        String fullUrl = baseUrl + path;

        Request request = new Request();
        request.setName(requestName);
        request.setDescription(description);
        request.setUrl(fullUrl.length() > 2000 ? fullUrl.substring(0, 2000) : fullUrl);
        request.setSortOrder(sortOrder);
        request.setFolder(folder);
        request.setHeaders(new ArrayList<>());
        request.setParams(new ArrayList<>());
        request.setScripts(new ArrayList<>());

        try {
            request.setMethod(HttpMethod.valueOf(httpMethod));
        } catch (IllegalArgumentException e) {
            warnings.add("Unsupported method '" + httpMethod + "' for " + path + ", defaulting to GET");
            request.setMethod(HttpMethod.GET);
        }

        // Merge path-level and operation-level parameters
        List<JsonNode> allParams = new ArrayList<>(pathParams);
        JsonNode opParams = opNode.path("parameters");
        if (opParams.isArray()) {
            for (JsonNode p : opParams) {
                allParams.add(p);
            }
        }

        for (JsonNode paramNode : allParams) {
            String in = paramNode.path("in").asText("");
            String paramName = paramNode.path("name").asText("");
            String paramDesc = paramNode.path("description").asText(null);
            boolean required = paramNode.path("required").asBoolean(false);

            switch (in) {
                case "query", "path" -> {
                    RequestParam param = new RequestParam();
                    param.setParamKey(paramName);
                    param.setParamValue(getExampleValue(paramNode));
                    param.setDescription(paramDesc);
                    param.setEnabled(required);
                    param.setRequest(request);
                    request.getParams().add(param);
                }
                case "header" -> {
                    RequestHeader header = new RequestHeader();
                    header.setHeaderKey(paramName);
                    header.setHeaderValue(getExampleValue(paramNode));
                    header.setDescription(paramDesc);
                    header.setEnabled(required);
                    header.setRequest(request);
                    request.getHeaders().add(header);
                }
            }
        }

        // Parse request body
        JsonNode requestBodyNode = opNode.path("requestBody");
        if (!requestBodyNode.isMissingNode() && requestBodyNode.isObject()) {
            RequestBody body = parseRequestBody(requestBodyNode, request, warnings);
            if (body != null) {
                request.setBody(body);
            }
        }

        // Parse operation-level security for auth
        JsonNode securityNode = opNode.path("security");
        if (securityNode.isArray() && !securityNode.isEmpty()) {
            parseSecurityAuth(securityNode, securitySchemes, request, warnings);
        }

        return request;
    }

    /**
     * Parses an OpenAPI requestBody node into a Courier RequestBody entity.
     *
     * @param bodyNode the requestBody JSON node
     * @param request  the parent request
     * @param warnings list to append warnings to
     * @return the RequestBody entity, or null if no content is found
     */
    private RequestBody parseRequestBody(JsonNode bodyNode, Request request, List<String> warnings) {
        JsonNode contentNode = bodyNode.path("content");
        if (!contentNode.isObject()) return null;

        RequestBody body = new RequestBody();
        body.setRequest(request);

        // Prefer application/json, then other types
        if (contentNode.has("application/json")) {
            body.setBodyType(BodyType.RAW_JSON);
            body.setRawContent(extractSchemaExample(contentNode.get("application/json")));
        } else if (contentNode.has("application/xml") || contentNode.has("text/xml")) {
            body.setBodyType(BodyType.RAW_XML);
            JsonNode xmlNode = contentNode.has("application/xml")
                    ? contentNode.get("application/xml") : contentNode.get("text/xml");
            body.setRawContent(extractSchemaExample(xmlNode));
        } else if (contentNode.has("application/x-www-form-urlencoded")) {
            body.setBodyType(BodyType.X_WWW_FORM_URLENCODED);
            body.setFormData(extractSchemaExample(contentNode.get("application/x-www-form-urlencoded")));
        } else if (contentNode.has("multipart/form-data")) {
            body.setBodyType(BodyType.FORM_DATA);
            body.setFormData(extractSchemaExample(contentNode.get("multipart/form-data")));
        } else if (contentNode.has("text/html")) {
            body.setBodyType(BodyType.RAW_HTML);
            body.setRawContent(extractSchemaExample(contentNode.get("text/html")));
        } else {
            // Take the first available content type
            Iterator<Map.Entry<String, JsonNode>> it = contentNode.fields();
            if (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                body.setBodyType(BodyType.RAW_TEXT);
                body.setRawContent(extractSchemaExample(entry.getValue()));
                warnings.add("Unknown content type '" + entry.getKey() + "', importing as raw text");
            } else {
                return null;
            }
        }

        return body;
    }

    /**
     * Parses OpenAPI security requirements and maps the first security scheme
     * to a Courier RequestAuth entity.
     *
     * @param securityNode    the security requirements array
     * @param securitySchemes the global security scheme definitions
     * @param request         the parent request
     * @param warnings        list to append warnings to
     */
    private void parseSecurityAuth(JsonNode securityNode, Map<String, JsonNode> securitySchemes,
                                   Request request, List<String> warnings) {
        for (JsonNode secReq : securityNode) {
            if (!secReq.isObject()) continue;
            Iterator<String> names = secReq.fieldNames();
            if (!names.hasNext()) continue;

            String schemeName = names.next();
            JsonNode schemeDef = securitySchemes.get(schemeName);
            if (schemeDef == null) {
                warnings.add("Security scheme '" + schemeName + "' not found in components/securitySchemes");
                continue;
            }

            RequestAuth auth = mapSecurityScheme(schemeDef, request, warnings);
            if (auth != null) {
                request.setAuth(auth);
                return; // Use first matching scheme
            }
        }
    }

    /**
     * Maps an OpenAPI security scheme definition to a Courier RequestAuth entity.
     *
     * @param schemeDef the security scheme JSON node
     * @param request   the parent request
     * @param warnings  list to append warnings to
     * @return the RequestAuth entity, or null if the scheme is unsupported
     */
    private RequestAuth mapSecurityScheme(JsonNode schemeDef, Request request, List<String> warnings) {
        String type = schemeDef.path("type").asText("");
        RequestAuth auth = new RequestAuth();
        auth.setRequest(request);

        switch (type) {
            case "apiKey" -> {
                auth.setAuthType(AuthType.API_KEY);
                auth.setApiKeyHeader(schemeDef.path("name").asText(""));
                auth.setApiKeyAddTo(schemeDef.path("in").asText("header"));
            }
            case "http" -> {
                String scheme = schemeDef.path("scheme").asText("").toLowerCase();
                if ("bearer".equals(scheme)) {
                    auth.setAuthType(AuthType.BEARER_TOKEN);
                } else if ("basic".equals(scheme)) {
                    auth.setAuthType(AuthType.BASIC_AUTH);
                } else {
                    warnings.add("Unsupported HTTP auth scheme '" + scheme + "'");
                    return null;
                }
            }
            case "oauth2" -> {
                auth.setAuthType(AuthType.OAUTH2_AUTHORIZATION_CODE);
                JsonNode flows = schemeDef.path("flows");
                if (flows.has("authorizationCode")) {
                    JsonNode flow = flows.get("authorizationCode");
                    auth.setOauth2AuthUrl(flow.path("authorizationUrl").asText(""));
                    auth.setOauth2TokenUrl(flow.path("tokenUrl").asText(""));
                } else if (flows.has("clientCredentials")) {
                    auth.setAuthType(AuthType.OAUTH2_CLIENT_CREDENTIALS);
                    JsonNode flow = flows.get("clientCredentials");
                    auth.setOauth2TokenUrl(flow.path("tokenUrl").asText(""));
                } else if (flows.has("implicit")) {
                    auth.setAuthType(AuthType.OAUTH2_IMPLICIT);
                    JsonNode flow = flows.get("implicit");
                    auth.setOauth2AuthUrl(flow.path("authorizationUrl").asText(""));
                }
            }
            default -> {
                warnings.add("Unsupported security scheme type '" + type + "'");
                return null;
            }
        }

        return auth;
    }

    /**
     * Extracts an example value from a parameter or schema node.
     *
     * @param paramNode the parameter JSON node
     * @return the example value as a string, or empty string
     */
    private String getExampleValue(JsonNode paramNode) {
        if (paramNode.has("example")) {
            return paramNode.get("example").asText("");
        }
        JsonNode schema = paramNode.path("schema");
        if (schema.has("example")) {
            return schema.get("example").asText("");
        }
        if (schema.has("default")) {
            return schema.get("default").asText("");
        }
        return "";
    }

    /**
     * Extracts an example or schema representation from a media type node.
     *
     * @param mediaTypeNode the media type JSON node
     * @return the example content as a string, or empty string
     */
    private String extractSchemaExample(JsonNode mediaTypeNode) {
        if (mediaTypeNode.has("example")) {
            JsonNode example = mediaTypeNode.get("example");
            return example.isTextual() ? example.asText("") : example.toString();
        }
        JsonNode schema = mediaTypeNode.path("schema");
        if (schema.has("example")) {
            JsonNode example = schema.get("example");
            return example.isTextual() ? example.asText("") : example.toString();
        }
        if (!schema.isMissingNode()) {
            return schema.toString();
        }
        return "";
    }

    /**
     * Checks if a string is a valid HTTP method name.
     *
     * @param method the string to check
     * @return true if it is a recognized HTTP method
     */
    private boolean isHttpMethod(String method) {
        return switch (method) {
            case "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS" -> true;
            default -> false;
        };
    }

    /**
     * Result of parsing an OpenAPI specification, containing the populated Collection
     * entity and import metrics.
     *
     * @param collection           the parsed Collection with all nested entities
     * @param foldersImported      the number of tag-based folders created
     * @param requestsImported     the number of requests created
     * @param environmentsImported the number of server variables imported
     * @param warnings             any warnings generated during import
     */
    public record OpenApiImportResult(
            Collection collection,
            int foldersImported,
            int requestsImported,
            int environmentsImported,
            List<String> warnings
    ) {}
}
