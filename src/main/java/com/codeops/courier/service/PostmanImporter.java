package com.codeops.courier.service;

import com.codeops.courier.entity.*;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.entity.enums.ScriptType;
import com.codeops.courier.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Parses Postman Collection v2.1 JSON format into Courier entities.
 * Supports nested folders (item groups), request auth mapping, body mode mapping,
 * pre-request/post-response script event mapping, and collection-level variables.
 * The parser maps Postman's auth types, body modes, and event scripts to their
 * Courier equivalents.
 */
@Component
@Slf4j
public class PostmanImporter {

    private final ObjectMapper objectMapper;

    /**
     * Creates a new PostmanImporter with the given ObjectMapper.
     *
     * @param objectMapper the Jackson ObjectMapper for JSON parsing
     */
    public PostmanImporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses a Postman Collection v2.1 JSON string into a Courier Collection entity
     * with all nested folders, requests, headers, bodies, auth configs, scripts, and variables.
     *
     * @param json      the Postman v2.1 JSON content
     * @param teamId    the team ID for the imported collection
     * @param createdBy the user ID of the importer
     * @return the result containing the populated Collection, counts, and warnings
     * @throws ValidationException if the JSON is invalid or not a Postman v2.1 collection
     */
    public PostmanImportResult parse(String json, UUID teamId, UUID createdBy) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new ValidationException("Invalid JSON: " + e.getMessage());
        }

        // Validate Postman v2.1 format
        JsonNode infoNode = root.path("info");
        if (infoNode.isMissingNode()) {
            throw new ValidationException("Not a valid Postman collection: missing 'info' field");
        }

        String schemaUrl = infoNode.path("schema").asText("");
        if (!schemaUrl.isEmpty() && !schemaUrl.contains("v2.1") && !schemaUrl.contains("v2.0")) {
            log.warn("Postman schema version may not be v2.1: {}", schemaUrl);
        }

        List<String> warnings = new ArrayList<>();

        // Build collection
        String collectionName = infoNode.path("name").asText("Imported Collection");
        String description = infoNode.path("description").asText(null);

        Collection collection = new Collection();
        collection.setTeamId(teamId);
        collection.setName(collectionName);
        collection.setDescription(description);
        collection.setCreatedBy(createdBy);
        collection.setFolders(new ArrayList<>());
        collection.setVariables(new ArrayList<>());

        // Parse collection-level auth
        if (root.has("auth")) {
            parseCollectionAuth(root.get("auth"), collection, warnings);
        }

        // Parse collection-level events (pre-request/post-response scripts)
        if (root.has("event")) {
            parseCollectionEvents(root.get("event"), collection, warnings);
        }

        // Parse items (folders and requests)
        int[] counters = {0, 0}; // [folders, requests]
        JsonNode itemsNode = root.path("item");
        if (itemsNode.isArray()) {
            int sortOrder = 0;
            for (JsonNode itemNode : itemsNode) {
                parseItem(itemNode, collection, null, sortOrder++, counters, warnings);
            }
        }

        // Parse collection-level variables
        int envCount = 0;
        JsonNode variablesNode = root.path("variable");
        if (variablesNode.isArray()) {
            for (JsonNode varNode : variablesNode) {
                EnvironmentVariable envVar = new EnvironmentVariable();
                envVar.setVariableKey(varNode.path("key").asText(""));
                envVar.setVariableValue(varNode.path("value").asText(""));
                envVar.setScope("COLLECTION");
                envVar.setEnabled(true);
                envVar.setSecret(false);
                envVar.setCollection(collection);
                collection.getVariables().add(envVar);
                envCount++;
            }
        }

        log.info("Parsed Postman collection '{}': {} folders, {} requests, {} variables",
                collectionName, counters[0], counters[1], envCount);

        return new PostmanImportResult(collection, counters[0], counters[1], envCount, warnings);
    }

    /**
     * Recursively parses a Postman item node, which can be either a folder (has "item" array)
     * or a request (has "request" object).
     *
     * @param itemNode   the JSON node for the item
     * @param collection the parent collection
     * @param parent     the parent folder (null for root-level items)
     * @param sortOrder  the sort order for this item
     * @param counters   [folderCount, requestCount] — mutated in place
     * @param warnings   list to append warnings to
     */
    private void parseItem(JsonNode itemNode, Collection collection, Folder parent,
                           int sortOrder, int[] counters, List<String> warnings) {
        if (itemNode.has("item") && itemNode.get("item").isArray()) {
            // This is a folder
            Folder folder = new Folder();
            folder.setName(itemNode.path("name").asText("Unnamed Folder"));
            folder.setDescription(itemNode.path("description").asText(null));
            folder.setSortOrder(sortOrder);
            folder.setCollection(collection);
            folder.setParentFolder(parent);
            folder.setSubFolders(new ArrayList<>());
            folder.setRequests(new ArrayList<>());

            // Parse folder-level auth
            if (itemNode.has("auth")) {
                parseFolderAuth(itemNode.get("auth"), folder, warnings);
            }

            // Parse folder-level events
            if (itemNode.has("event")) {
                parseFolderEvents(itemNode.get("event"), folder, warnings);
            }

            if (parent != null) {
                parent.getSubFolders().add(folder);
            } else {
                collection.getFolders().add(folder);
            }
            counters[0]++;

            int childSort = 0;
            for (JsonNode child : itemNode.get("item")) {
                parseItem(child, collection, folder, childSort++, counters, warnings);
            }
        } else if (itemNode.has("request")) {
            // This is a request — ensure it has a folder
            Folder targetFolder = parent;
            if (targetFolder == null) {
                // Create a default folder for root-level requests
                targetFolder = getOrCreateDefaultFolder(collection);
                if (targetFolder.getId() == null && !collection.getFolders().contains(targetFolder)) {
                    collection.getFolders().add(targetFolder);
                    counters[0]++;
                }
            }

            Request request = parseRequest(itemNode, targetFolder, sortOrder, warnings);
            targetFolder.getRequests().add(request);
            counters[1]++;
        }
    }

    /**
     * Parses a Postman request item into a Courier Request entity.
     *
     * @param itemNode  the item JSON node containing "name" and "request"
     * @param folder    the folder to attach the request to
     * @param sortOrder the sort order
     * @param warnings  list to append warnings to
     * @return the populated Request entity
     */
    private Request parseRequest(JsonNode itemNode, Folder folder, int sortOrder, List<String> warnings) {
        String name = itemNode.path("name").asText("Unnamed Request");
        JsonNode reqNode = itemNode.get("request");

        Request request = new Request();
        request.setName(name.length() > 200 ? name.substring(0, 200) : name);
        request.setDescription(reqNode.path("description").asText(null));
        request.setSortOrder(sortOrder);
        request.setFolder(folder);
        request.setHeaders(new ArrayList<>());
        request.setParams(new ArrayList<>());
        request.setScripts(new ArrayList<>());

        // Method
        String methodStr = reqNode.path("method").asText("GET").toUpperCase();
        try {
            request.setMethod(HttpMethod.valueOf(methodStr));
        } catch (IllegalArgumentException e) {
            warnings.add("Unsupported method '" + methodStr + "' for request '" + name + "', defaulting to GET");
            request.setMethod(HttpMethod.GET);
        }

        // URL
        JsonNode urlNode = reqNode.path("url");
        String urlString;
        if (urlNode.isTextual()) {
            urlString = urlNode.asText();
        } else {
            urlString = urlNode.path("raw").asText("");
            // Parse query params from url.query array
            JsonNode queryNode = urlNode.path("query");
            if (queryNode.isArray()) {
                for (JsonNode qp : queryNode) {
                    RequestParam param = new RequestParam();
                    param.setParamKey(qp.path("key").asText(""));
                    param.setParamValue(qp.path("value").asText(""));
                    param.setDescription(qp.path("description").asText(null));
                    param.setEnabled(!qp.path("disabled").asBoolean(false));
                    param.setRequest(request);
                    request.getParams().add(param);
                }
            }
        }
        request.setUrl(urlString.length() > 2000 ? urlString.substring(0, 2000) : urlString);

        // Headers
        JsonNode headersNode = reqNode.path("header");
        if (headersNode.isArray()) {
            for (JsonNode hNode : headersNode) {
                RequestHeader header = new RequestHeader();
                header.setHeaderKey(hNode.path("key").asText(""));
                header.setHeaderValue(hNode.path("value").asText(""));
                header.setDescription(hNode.path("description").asText(null));
                header.setEnabled(!hNode.path("disabled").asBoolean(false));
                header.setRequest(request);
                request.getHeaders().add(header);
            }
        }

        // Body
        JsonNode bodyNode = reqNode.path("body");
        if (!bodyNode.isMissingNode() && bodyNode.isObject()) {
            RequestBody body = parseBody(bodyNode, request, warnings);
            if (body != null) {
                request.setBody(body);
            }
        }

        // Auth
        JsonNode authNode = reqNode.path("auth");
        if (!authNode.isMissingNode() && authNode.isObject()) {
            RequestAuth auth = parseAuth(authNode, request, warnings);
            if (auth != null) {
                request.setAuth(auth);
            }
        }

        // Events (scripts)
        JsonNode eventsNode = itemNode.path("event");
        if (eventsNode.isArray()) {
            for (JsonNode eventNode : eventsNode) {
                parseScript(eventNode, request, warnings);
            }
        }

        return request;
    }

    /**
     * Parses a Postman body node into a Courier RequestBody entity.
     *
     * @param bodyNode the body JSON node
     * @param request  the parent request
     * @param warnings list to append warnings to
     * @return the RequestBody entity, or null if the mode is "none"
     */
    private RequestBody parseBody(JsonNode bodyNode, Request request, List<String> warnings) {
        String mode = bodyNode.path("mode").asText("none");
        RequestBody body = new RequestBody();
        body.setRequest(request);

        switch (mode) {
            case "raw" -> {
                body.setRawContent(bodyNode.path("raw").asText(""));
                // Detect raw language
                String language = bodyNode.path("options").path("raw").path("language").asText("text");
                body.setBodyType(mapRawLanguage(language));
            }
            case "formdata" -> {
                body.setBodyType(BodyType.FORM_DATA);
                body.setFormData(bodyNode.path("formdata").toString());
            }
            case "urlencoded" -> {
                body.setBodyType(BodyType.X_WWW_FORM_URLENCODED);
                body.setFormData(bodyNode.path("urlencoded").toString());
            }
            case "graphql" -> {
                body.setBodyType(BodyType.GRAPHQL);
                JsonNode gql = bodyNode.path("graphql");
                body.setGraphqlQuery(gql.path("query").asText(""));
                body.setGraphqlVariables(gql.path("variables").asText(""));
            }
            case "file" -> {
                body.setBodyType(BodyType.BINARY);
                body.setBinaryFileName(bodyNode.path("file").path("src").asText(""));
            }
            case "none" -> {
                return null;
            }
            default -> {
                warnings.add("Unknown body mode '" + mode + "', importing as raw text");
                body.setBodyType(BodyType.RAW_TEXT);
                body.setRawContent(bodyNode.toString());
            }
        }

        return body;
    }

    /**
     * Parses a Postman auth node into a Courier RequestAuth entity.
     *
     * @param authNode the auth JSON node
     * @param request  the parent request
     * @param warnings list to append warnings to
     * @return the RequestAuth entity, or null if no auth type specified
     */
    private RequestAuth parseAuth(JsonNode authNode, Request request, List<String> warnings) {
        String type = authNode.path("type").asText("noauth");
        RequestAuth auth = new RequestAuth();
        auth.setRequest(request);

        switch (type) {
            case "noauth" -> {
                return null;
            }
            case "apikey" -> {
                auth.setAuthType(AuthType.API_KEY);
                JsonNode apikeyArr = authNode.path("apikey");
                if (apikeyArr.isArray()) {
                    for (JsonNode kv : apikeyArr) {
                        String key = kv.path("key").asText("");
                        String value = kv.path("value").asText("");
                        switch (key) {
                            case "key" -> auth.setApiKeyHeader(value);
                            case "value" -> auth.setApiKeyValue(value);
                            case "in" -> auth.setApiKeyAddTo(value);
                        }
                    }
                }
            }
            case "bearer" -> {
                auth.setAuthType(AuthType.BEARER_TOKEN);
                JsonNode bearerArr = authNode.path("bearer");
                if (bearerArr.isArray()) {
                    for (JsonNode kv : bearerArr) {
                        if ("token".equals(kv.path("key").asText())) {
                            auth.setBearerToken(kv.path("value").asText(""));
                        }
                    }
                }
            }
            case "basic" -> {
                auth.setAuthType(AuthType.BASIC_AUTH);
                JsonNode basicArr = authNode.path("basic");
                if (basicArr.isArray()) {
                    for (JsonNode kv : basicArr) {
                        String key = kv.path("key").asText("");
                        String value = kv.path("value").asText("");
                        switch (key) {
                            case "username" -> auth.setBasicUsername(value);
                            case "password" -> auth.setBasicPassword(value);
                        }
                    }
                }
            }
            case "oauth2" -> {
                auth.setAuthType(AuthType.OAUTH2_AUTHORIZATION_CODE);
                JsonNode oauthArr = authNode.path("oauth2");
                if (oauthArr.isArray()) {
                    for (JsonNode kv : oauthArr) {
                        String key = kv.path("key").asText("");
                        String value = kv.path("value").asText("");
                        switch (key) {
                            case "accessToken" -> auth.setOauth2AccessToken(value);
                            case "tokenUrl", "accessTokenUrl" -> auth.setOauth2TokenUrl(value);
                            case "authUrl" -> auth.setOauth2AuthUrl(value);
                            case "clientId" -> auth.setOauth2ClientId(value);
                            case "clientSecret" -> auth.setOauth2ClientSecret(value);
                            case "scope" -> auth.setOauth2Scope(value);
                            case "callbackUrl", "redirect_uri" -> auth.setOauth2CallbackUrl(value);
                            case "grant_type" -> auth.setOauth2GrantType(value);
                        }
                    }
                }
            }
            default -> {
                warnings.add("Unsupported auth type '" + type + "', skipping");
                return null;
            }
        }

        return auth;
    }

    /**
     * Parses a Postman event node into a Courier RequestScript entity.
     *
     * @param eventNode the event JSON node
     * @param request   the parent request
     * @param warnings  list to append warnings to
     */
    private void parseScript(JsonNode eventNode, Request request, List<String> warnings) {
        String listen = eventNode.path("listen").asText("");
        JsonNode scriptNode = eventNode.path("script");
        if (scriptNode.isMissingNode()) return;

        ScriptType scriptType;
        switch (listen) {
            case "prerequest" -> scriptType = ScriptType.PRE_REQUEST;
            case "test" -> scriptType = ScriptType.POST_RESPONSE;
            default -> {
                warnings.add("Unknown event type '" + listen + "', skipping");
                return;
            }
        }

        JsonNode execNode = scriptNode.path("exec");
        String content;
        if (execNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode line : execNode) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(line.asText(""));
            }
            content = sb.toString();
        } else {
            content = scriptNode.path("exec").asText("");
        }

        if (!content.isBlank()) {
            RequestScript script = new RequestScript();
            script.setScriptType(scriptType);
            script.setContent(content);
            script.setRequest(request);
            request.getScripts().add(script);
        }
    }

    /**
     * Parses collection-level auth into collection fields.
     *
     * @param authNode   the auth JSON node
     * @param collection the collection to update
     * @param warnings   list to append warnings to
     */
    private void parseCollectionAuth(JsonNode authNode, Collection collection, List<String> warnings) {
        String type = authNode.path("type").asText("noauth");
        try {
            AuthType authType = mapPostmanAuthType(type);
            collection.setAuthType(authType);
            collection.setAuthConfig(authNode.toString());
        } catch (Exception e) {
            warnings.add("Unsupported collection auth type '" + type + "', skipping");
        }
    }

    /**
     * Parses collection-level events into collection script fields.
     *
     * @param eventsNode the events JSON array
     * @param collection the collection to update
     * @param warnings   list to append warnings to
     */
    private void parseCollectionEvents(JsonNode eventsNode, Collection collection, List<String> warnings) {
        if (!eventsNode.isArray()) return;
        for (JsonNode eventNode : eventsNode) {
            String listen = eventNode.path("listen").asText("");
            JsonNode execNode = eventNode.path("script").path("exec");
            String content = extractExecContent(execNode);
            if (content.isBlank()) continue;

            switch (listen) {
                case "prerequest" -> collection.setPreRequestScript(content);
                case "test" -> collection.setPostResponseScript(content);
                default -> warnings.add("Unknown collection event type '" + listen + "'");
            }
        }
    }

    /**
     * Parses folder-level auth into folder fields.
     *
     * @param authNode the auth JSON node
     * @param folder   the folder to update
     * @param warnings list to append warnings to
     */
    private void parseFolderAuth(JsonNode authNode, Folder folder, List<String> warnings) {
        String type = authNode.path("type").asText("noauth");
        try {
            AuthType authType = mapPostmanAuthType(type);
            folder.setAuthType(authType);
            folder.setAuthConfig(authNode.toString());
        } catch (Exception e) {
            warnings.add("Unsupported folder auth type '" + type + "', skipping");
        }
    }

    /**
     * Parses folder-level events into folder script fields.
     *
     * @param eventsNode the events JSON array
     * @param folder     the folder to update
     * @param warnings   list to append warnings to
     */
    private void parseFolderEvents(JsonNode eventsNode, Folder folder, List<String> warnings) {
        if (!eventsNode.isArray()) return;
        for (JsonNode eventNode : eventsNode) {
            String listen = eventNode.path("listen").asText("");
            JsonNode execNode = eventNode.path("script").path("exec");
            String content = extractExecContent(execNode);
            if (content.isBlank()) continue;

            switch (listen) {
                case "prerequest" -> folder.setPreRequestScript(content);
                case "test" -> folder.setPostResponseScript(content);
                default -> warnings.add("Unknown folder event type '" + listen + "'");
            }
        }
    }

    /**
     * Extracts script content from a Postman exec node (array of lines or single string).
     *
     * @param execNode the exec JSON node
     * @return the concatenated script content
     */
    private String extractExecContent(JsonNode execNode) {
        if (execNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode line : execNode) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(line.asText(""));
            }
            return sb.toString();
        }
        return execNode.asText("");
    }

    /**
     * Maps a Postman auth type string to a Courier AuthType enum value.
     *
     * @param postmanType the Postman auth type string
     * @return the corresponding AuthType
     */
    private AuthType mapPostmanAuthType(String postmanType) {
        return switch (postmanType) {
            case "noauth" -> AuthType.NO_AUTH;
            case "apikey" -> AuthType.API_KEY;
            case "bearer" -> AuthType.BEARER_TOKEN;
            case "basic" -> AuthType.BASIC_AUTH;
            case "oauth2" -> AuthType.OAUTH2_AUTHORIZATION_CODE;
            default -> AuthType.NO_AUTH;
        };
    }

    /**
     * Maps a Postman raw language to a Courier BodyType.
     *
     * @param language the Postman raw language string
     * @return the corresponding BodyType
     */
    private BodyType mapRawLanguage(String language) {
        return switch (language.toLowerCase()) {
            case "json" -> BodyType.RAW_JSON;
            case "xml" -> BodyType.RAW_XML;
            case "html" -> BodyType.RAW_HTML;
            case "yaml" -> BodyType.RAW_YAML;
            default -> BodyType.RAW_TEXT;
        };
    }

    /**
     * Gets or creates a default "Requests" folder for root-level requests
     * that are not inside any Postman folder.
     *
     * @param collection the parent collection
     * @return the default folder
     */
    private Folder getOrCreateDefaultFolder(Collection collection) {
        for (Folder f : collection.getFolders()) {
            if ("Requests".equals(f.getName())) {
                return f;
            }
        }
        Folder defaultFolder = new Folder();
        defaultFolder.setName("Requests");
        defaultFolder.setDescription("Imported root-level requests");
        defaultFolder.setSortOrder(collection.getFolders().size());
        defaultFolder.setCollection(collection);
        defaultFolder.setSubFolders(new ArrayList<>());
        defaultFolder.setRequests(new ArrayList<>());
        return defaultFolder;
    }

    /**
     * Result of parsing a Postman collection, containing the populated Collection entity
     * and import metrics.
     *
     * @param collection           the parsed Collection with all nested entities
     * @param foldersImported      the number of folders created
     * @param requestsImported     the number of requests created
     * @param environmentsImported the number of collection variables imported
     * @param warnings             any warnings generated during import
     */
    public record PostmanImportResult(
            Collection collection,
            int foldersImported,
            int requestsImported,
            int environmentsImported,
            List<String> warnings
    ) {}
}
