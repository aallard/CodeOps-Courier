package com.codeops.courier.service;

import com.codeops.courier.entity.*;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses cURL commands into Courier entities (Request with headers, body, auth, params).
 * Supports common cURL flags: -X, -H, -d, --data, -u, --user, -F, --form,
 * --data-raw, --data-binary, --compressed, -k, --insecure, -L, --location.
 * Handles single-quoted, double-quoted, and unquoted arguments, as well as
 * backslash-continued multiline commands.
 */
@Component
@Slf4j
public class CurlImporter {

    /**
     * Parses a cURL command string into a Request entity attached to the given folder.
     * The request is fully populated with headers, body, auth, and query params
     * extracted from the cURL flags.
     *
     * @param curlCommand the cURL command string (may span multiple lines with backslash continuations)
     * @param folder      the folder to attach the request to
     * @param sortOrder   the sort order for the request within the folder
     * @return the populated Request entity (not yet persisted)
     * @throws ValidationException if the command is not a valid cURL command
     */
    public Request parseCurl(String curlCommand, Folder folder, int sortOrder) {
        String normalized = normalizeCommand(curlCommand);
        List<String> tokens = tokenize(normalized);

        if (tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase("curl")) {
            throw new ValidationException("Not a valid cURL command: must start with 'curl'");
        }

        String url = null;
        String method = null;
        List<String[]> headers = new ArrayList<>();
        List<String> dataEntries = new ArrayList<>();
        List<String[]> formEntries = new ArrayList<>();
        String basicUser = null;

        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            switch (token) {
                case "-X", "--request" -> {
                    if (i + 1 < tokens.size()) {
                        method = tokens.get(++i).toUpperCase();
                    }
                }
                case "-H", "--header" -> {
                    if (i + 1 < tokens.size()) {
                        String headerStr = tokens.get(++i);
                        int colonIdx = headerStr.indexOf(':');
                        if (colonIdx > 0) {
                            String key = headerStr.substring(0, colonIdx).trim();
                            String value = headerStr.substring(colonIdx + 1).trim();
                            headers.add(new String[]{key, value});
                        }
                    }
                }
                case "-d", "--data", "--data-raw", "--data-binary" -> {
                    if (i + 1 < tokens.size()) {
                        dataEntries.add(tokens.get(++i));
                    }
                }
                case "-F", "--form" -> {
                    if (i + 1 < tokens.size()) {
                        String formStr = tokens.get(++i);
                        int eqIdx = formStr.indexOf('=');
                        if (eqIdx > 0) {
                            formEntries.add(new String[]{
                                    formStr.substring(0, eqIdx),
                                    formStr.substring(eqIdx + 1)
                            });
                        }
                    }
                }
                case "-u", "--user" -> {
                    if (i + 1 < tokens.size()) {
                        basicUser = tokens.get(++i);
                    }
                }
                case "--compressed", "-k", "--insecure", "-L", "--location",
                     "-s", "--silent", "-v", "--verbose", "-i", "--include" -> {
                    // no-value flags — skip
                }
                default -> {
                    if (!token.startsWith("-") && url == null) {
                        url = token;
                    }
                }
            }
        }

        if (url == null) {
            throw new ValidationException("No URL found in cURL command");
        }

        // Determine method: explicit > implicit from data/form > GET
        if (method == null) {
            if (!dataEntries.isEmpty() || !formEntries.isEmpty()) {
                method = "POST";
            } else {
                method = "GET";
            }
        }

        HttpMethod httpMethod;
        try {
            httpMethod = HttpMethod.valueOf(method);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unsupported HTTP method: " + method);
        }

        // Parse URL and extract query params
        String baseUrl = url;
        List<String[]> queryParams = new ArrayList<>();
        int qIdx = url.indexOf('?');
        if (qIdx >= 0) {
            baseUrl = url.substring(0, qIdx);
            String queryString = url.substring(qIdx + 1);
            for (String pair : queryString.split("&")) {
                int eqIdx = pair.indexOf('=');
                if (eqIdx > 0) {
                    queryParams.add(new String[]{
                            decode(pair.substring(0, eqIdx)),
                            decode(pair.substring(eqIdx + 1))
                    });
                } else if (!pair.isBlank()) {
                    queryParams.add(new String[]{decode(pair), ""});
                }
            }
        }

        // Build request name from method + URL path
        String requestName = buildRequestName(httpMethod, baseUrl);

        Request request = new Request();
        request.setName(requestName);
        request.setMethod(httpMethod);
        request.setUrl(url);
        request.setSortOrder(sortOrder);
        request.setFolder(folder);
        request.setHeaders(new ArrayList<>());
        request.setParams(new ArrayList<>());
        request.setScripts(new ArrayList<>());

        // Add headers
        for (String[] h : headers) {
            RequestHeader rh = new RequestHeader();
            rh.setHeaderKey(h[0]);
            rh.setHeaderValue(h[1]);
            rh.setEnabled(true);
            rh.setRequest(request);
            request.getHeaders().add(rh);
        }

        // Add query params
        for (String[] qp : queryParams) {
            RequestParam rp = new RequestParam();
            rp.setParamKey(qp[0]);
            rp.setParamValue(qp[1]);
            rp.setEnabled(true);
            rp.setRequest(request);
            request.getParams().add(rp);
        }

        // Set body
        if (!dataEntries.isEmpty()) {
            String bodyContent = String.join("&", dataEntries);
            RequestBody body = new RequestBody();
            body.setRequest(request);

            // Detect body type from Content-Type header
            BodyType bodyType = detectBodyType(headers);
            body.setBodyType(bodyType);
            body.setRawContent(bodyContent);
            request.setBody(body);
        } else if (!formEntries.isEmpty()) {
            RequestBody body = new RequestBody();
            body.setRequest(request);
            body.setBodyType(BodyType.FORM_DATA);
            StringBuilder formJson = new StringBuilder("[");
            for (int i = 0; i < formEntries.size(); i++) {
                if (i > 0) formJson.append(",");
                formJson.append("{\"key\":\"").append(escapeJson(formEntries.get(i)[0]))
                        .append("\",\"value\":\"").append(escapeJson(formEntries.get(i)[1]))
                        .append("\",\"type\":\"text\"}");
            }
            formJson.append("]");
            body.setFormData(formJson.toString());
            request.setBody(body);
        }

        // Set auth
        if (basicUser != null) {
            RequestAuth auth = new RequestAuth();
            auth.setAuthType(AuthType.BASIC_AUTH);
            auth.setRequest(request);
            int colonIdx = basicUser.indexOf(':');
            if (colonIdx >= 0) {
                auth.setBasicUsername(basicUser.substring(0, colonIdx));
                auth.setBasicPassword(basicUser.substring(colonIdx + 1));
            } else {
                auth.setBasicUsername(basicUser);
                auth.setBasicPassword("");
            }
            request.setAuth(auth);
        } else {
            // Check for Authorization header to extract Bearer token
            for (String[] h : headers) {
                if ("Authorization".equalsIgnoreCase(h[0]) && h[1].startsWith("Bearer ")) {
                    RequestAuth auth = new RequestAuth();
                    auth.setAuthType(AuthType.BEARER_TOKEN);
                    auth.setBearerToken(h[1].substring(7).trim());
                    auth.setRequest(request);
                    request.setAuth(auth);
                    break;
                }
            }
        }

        log.debug("Parsed cURL command: {} {}", httpMethod, url);
        return request;
    }

    /**
     * Normalizes a cURL command by collapsing backslash-newline continuations
     * and trimming excess whitespace.
     *
     * @param command the raw cURL command text
     * @return normalized single-line command
     */
    String normalizeCommand(String command) {
        // Remove backslash-newline continuations
        String normalized = command.replaceAll("\\\\\\s*\\n", " ");
        // Collapse multiple whitespace
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    /**
     * Tokenizes a normalized cURL command, respecting single and double quotes.
     * Quoted strings are returned without the surrounding quotes.
     *
     * @param command the normalized command string
     * @return ordered list of tokens
     */
    List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaped = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && !inSingle) {
                escaped = true;
                continue;
            }

            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }

            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }

            if (c == ' ' && !inSingle && !inDouble) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    /**
     * Detects the body type from the Content-Type header value.
     *
     * @param headers the list of header key-value pairs
     * @return the detected BodyType, defaulting to RAW_TEXT
     */
    private BodyType detectBodyType(List<String[]> headers) {
        for (String[] h : headers) {
            if ("Content-Type".equalsIgnoreCase(h[0])) {
                String ct = h[1].toLowerCase();
                if (ct.contains("application/json")) return BodyType.RAW_JSON;
                if (ct.contains("application/xml") || ct.contains("text/xml")) return BodyType.RAW_XML;
                if (ct.contains("text/html")) return BodyType.RAW_HTML;
                if (ct.contains("application/x-www-form-urlencoded")) return BodyType.X_WWW_FORM_URLENCODED;
                if (ct.contains("application/yaml") || ct.contains("text/yaml")) return BodyType.RAW_YAML;
            }
        }
        return BodyType.RAW_TEXT;
    }

    /**
     * Builds a human-readable request name from the HTTP method and URL.
     *
     * @param method  the HTTP method
     * @param baseUrl the base URL (without query string)
     * @return a short request name like "GET /users"
     */
    private String buildRequestName(HttpMethod method, String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String name = method.name() + " " + path;
            return name.length() > 200 ? name.substring(0, 200) : name;
        } catch (Exception e) {
            String name = method.name() + " " + baseUrl;
            return name.length() > 200 ? name.substring(0, 200) : name;
        }
    }

    /**
     * URL-decodes a string, returning the original if decoding fails.
     *
     * @param value the URL-encoded string
     * @return the decoded string
     */
    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * Escapes a string for safe embedding in a JSON value.
     *
     * @param value the raw string
     * @return the escaped string
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
