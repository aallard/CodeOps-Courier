package com.codeops.courier.service;

import com.codeops.courier.config.AppConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable execution context for pre-request and post-response scripts.
 * Holds variables, assertion results, console output, and request/response data.
 * Not a Spring bean — created fresh per script execution.
 */
public class ScriptContext {

    // Variable scopes available to scripts
    private final Map<String, String> globalVariables;
    private final Map<String, String> collectionVariables;
    private final Map<String, String> environmentVariables;
    private final Map<String, String> localVariables;

    // Request data (pre-request scripts can modify, post-response scripts read-only)
    private String requestUrl;
    private String requestMethod;
    private Map<String, String> requestHeaders;
    private String requestBody;

    // Response data (only available in post-response scripts)
    private Integer responseStatus;
    private Map<String, List<String>> responseHeaders;
    private String responseBody;
    private Long responseTimeMs;

    // Script outputs
    private final List<AssertionResult> assertions = new ArrayList<>();
    private final List<String> consoleOutput = new ArrayList<>();
    private boolean requestCancelled = false;

    /**
     * Creates a new ScriptContext with the specified variable scopes and request data.
     *
     * @param globalVariables     global (team-wide) variables
     * @param collectionVariables collection-scoped variables
     * @param environmentVariables environment-scoped variables
     * @param localVariables      runtime-only local variables
     */
    public ScriptContext(Map<String, String> globalVariables,
                         Map<String, String> collectionVariables,
                         Map<String, String> environmentVariables,
                         Map<String, String> localVariables) {
        this.globalVariables = globalVariables != null ? new LinkedHashMap<>(globalVariables) : new LinkedHashMap<>();
        this.collectionVariables = collectionVariables != null ? new LinkedHashMap<>(collectionVariables) : new LinkedHashMap<>();
        this.environmentVariables = environmentVariables != null ? new LinkedHashMap<>(environmentVariables) : new LinkedHashMap<>();
        this.localVariables = localVariables != null ? new LinkedHashMap<>(localVariables) : new LinkedHashMap<>();
    }

    /**
     * Result of a single test/assertion from a script.
     *
     * @param name    the test name
     * @param passed  whether the assertion passed
     * @param message the failure message (empty if passed)
     */
    public record AssertionResult(
            String name,
            boolean passed,
            String message
    ) {}

    /**
     * Adds a test assertion result.
     *
     * @param name    the test name
     * @param passed  whether the assertion passed
     * @param message the failure message
     */
    public void addAssertion(String name, boolean passed, String message) {
        assertions.add(new AssertionResult(name, passed, message));
    }

    /**
     * Adds a console log line, respecting the maximum line limit.
     *
     * @param line the console output line
     */
    public void addConsoleLog(String line) {
        if (consoleOutput.size() < AppConstants.SCRIPT_MAX_CONSOLE_LINES) {
            consoleOutput.add(line);
        }
    }

    // ─── Getters and Setters ───

    /**
     * @return the global variables map (mutable)
     */
    public Map<String, String> getGlobalVariables() {
        return globalVariables;
    }

    /**
     * @return the collection variables map (mutable)
     */
    public Map<String, String> getCollectionVariables() {
        return collectionVariables;
    }

    /**
     * @return the environment variables map (mutable)
     */
    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    /**
     * @return the local variables map (mutable)
     */
    public Map<String, String> getLocalVariables() {
        return localVariables;
    }

    /**
     * @return the request URL
     */
    public String getRequestUrl() {
        return requestUrl;
    }

    /**
     * @param requestUrl the request URL to set
     */
    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    /**
     * @return the request HTTP method
     */
    public String getRequestMethod() {
        return requestMethod;
    }

    /**
     * @param requestMethod the request HTTP method to set
     */
    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    /**
     * @return the request headers map
     */
    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    /**
     * @param requestHeaders the request headers to set
     */
    public void setRequestHeaders(Map<String, String> requestHeaders) {
        this.requestHeaders = requestHeaders != null ? new LinkedHashMap<>(requestHeaders) : new LinkedHashMap<>();
    }

    /**
     * @return the request body
     */
    public String getRequestBody() {
        return requestBody;
    }

    /**
     * @param requestBody the request body to set
     */
    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    /**
     * @return the HTTP response status code
     */
    public Integer getResponseStatus() {
        return responseStatus;
    }

    /**
     * @param responseStatus the response status code to set
     */
    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    /**
     * @return the response headers
     */
    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    /**
     * @param responseHeaders the response headers to set
     */
    public void setResponseHeaders(Map<String, List<String>> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    /**
     * @return the response body
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * @param responseBody the response body to set
     */
    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    /**
     * @return the response time in milliseconds
     */
    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    /**
     * @param responseTimeMs the response time in milliseconds to set
     */
    public void setResponseTimeMs(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    /**
     * @return the list of assertion results from script execution
     */
    public List<AssertionResult> getAssertions() {
        return assertions;
    }

    /**
     * @return the captured console output lines
     */
    public List<String> getConsoleOutput() {
        return consoleOutput;
    }

    /**
     * @return true if the pre-request script cancelled the request
     */
    public boolean isRequestCancelled() {
        return requestCancelled;
    }

    /**
     * @param requestCancelled whether the request was cancelled
     */
    public void setRequestCancelled(boolean requestCancelled) {
        this.requestCancelled = requestCancelled;
    }
}
