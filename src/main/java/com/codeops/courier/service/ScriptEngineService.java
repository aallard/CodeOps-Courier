package com.codeops.courier.service;

import com.codeops.courier.config.AppConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * JavaScript sandbox execution engine for pre-request and post-response scripts.
 * Uses GraalJS to run user scripts in a fully sandboxed environment with no access
 * to the file system, network, Java classes, or system properties.
 *
 * <p>Provides a Postman-compatible {@code pm} object API for variable manipulation,
 * assertions (via {@code pm.test} and {@code pm.expect}), request/response inspection,
 * and console output capture.</p>
 */
@Service
@Slf4j
public class ScriptEngineService {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private final int timeoutSeconds;

    /**
     * Creates a ScriptEngineService with the default timeout.
     */
    public ScriptEngineService() {
        this.timeoutSeconds = AppConstants.SCRIPT_TIMEOUT_SECONDS;
    }

    /**
     * Creates a ScriptEngineService with a custom timeout for testing.
     *
     * @param timeoutSeconds the script execution timeout in seconds
     */
    ScriptEngineService(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Executes a pre-request script. The script can modify request URL, headers, body,
     * and variables. It can also cancel the request by calling {@code pm.request.cancel()}.
     *
     * @param scriptContent the JavaScript code to execute
     * @param context       mutable script context with request data and variables
     * @return the modified ScriptContext after execution
     */
    public ScriptContext executePreRequestScript(String scriptContent, ScriptContext context) {
        return executeScript(scriptContent, context, false);
    }

    /**
     * Executes a post-response script. The script can read response data, run assertions
     * via {@code pm.test()}, and modify variables. Request data is read-only.
     *
     * @param scriptContent the JavaScript code to execute
     * @param context       mutable script context with response data and variables
     * @return the modified ScriptContext with assertion results and updated variables
     */
    public ScriptContext executePostResponseScript(String scriptContent, ScriptContext context) {
        return executeScript(scriptContent, context, true);
    }

    /**
     * Executes a script in a sandboxed GraalJS context with the pm object bound.
     *
     * @param scriptContent the user's JavaScript code
     * @param context       the mutable script context
     * @param isPostResponse true for post-response scripts (enables pm.response)
     * @return the modified context
     */
    private ScriptContext executeScript(String scriptContent, ScriptContext context, boolean isPostResponse) {
        if (scriptContent == null || scriptContent.isBlank()) {
            return context;
        }

        Context jsContext = createSandboxedContext();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            String pmSource = buildPmObjectSource(context, isPostResponse);
            String fullScript = pmSource + "\n" + scriptContent;

            Future<?> future = executor.submit(() -> {
                jsContext.eval("js", fullScript);
                readBackState(jsContext, context);
            });

            future.get(timeoutSeconds, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            jsContext.close(true);
            log.warn("Script timed out after {}s", timeoutSeconds);
            context.addAssertion("Script execution", false,
                    "Script timed out after " + timeoutSeconds + " seconds");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String message;
            if (cause instanceof PolyglotException pe) {
                if (pe.isCancelled()) {
                    message = "Script was cancelled";
                } else if (pe.isResourceExhausted()) {
                    message = "Script exceeded resource limits";
                } else {
                    message = pe.getMessage();
                }
            } else {
                message = cause != null ? cause.getMessage() : e.getMessage();
            }
            log.warn("Script execution failed: {}", message);
            context.addAssertion("Script execution", false, "Script error: " + message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Script execution interrupted");
            context.addAssertion("Script execution", false, "Script interrupted");
        } finally {
            try {
                jsContext.close();
            } catch (Exception ignored) {
                // Context may already be closed from timeout
            }
            executor.shutdownNow();
        }

        return context;
    }

    /**
     * Creates a sandboxed GraalJS context with no host, I/O, native, or process access.
     *
     * @return the sandboxed polyglot context
     */
    private Context createSandboxedContext() {
        return Context.newBuilder("js")
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(s -> false)
                .allowNativeAccess(false)
                .allowCreateProcess(false)
                .allowCreateThread(false)
                .allowIO(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .option("js.ecmascript-version", "2022")
                .build();
    }

    /**
     * Builds the JavaScript source that defines the {@code pm} and {@code console} objects.
     * This source is prepended to the user's script before execution.
     *
     * @param context        the current script context with variables and request/response data
     * @param isPostResponse whether this is a post-response script (enables pm.response)
     * @return the JavaScript source string defining the pm API
     */
    private String buildPmObjectSource(ScriptContext context, boolean isPostResponse) {
        StringBuilder js = new StringBuilder();

        // State variables
        js.append("var __globals = ").append(toJsObject(context.getGlobalVariables())).append(";\n");
        js.append("var __collection = ").append(toJsObject(context.getCollectionVariables())).append(";\n");
        js.append("var __environment = ").append(toJsObject(context.getEnvironmentVariables())).append(";\n");
        js.append("var __local = ").append(toJsObject(context.getLocalVariables())).append(";\n");
        js.append("var __assertions = [];\n");
        js.append("var __consoleOutput = [];\n");
        js.append("var __requestCancelled = false;\n");

        // Request data
        js.append("var __requestUrl = ").append(toJsString(context.getRequestUrl())).append(";\n");
        js.append("var __requestMethod = ").append(toJsString(context.getRequestMethod())).append(";\n");
        js.append("var __requestHeaders = ").append(toJsObject(context.getRequestHeaders())).append(";\n");
        js.append("var __requestBody = ").append(toJsString(context.getRequestBody())).append(";\n");

        // Response data (post-response only)
        if (isPostResponse) {
            js.append("var __responseStatus = ").append(context.getResponseStatus() != null ? context.getResponseStatus() : "null").append(";\n");
            js.append("var __responseHeaders = ").append(toJsObject(context.getResponseHeaders())).append(";\n");
            js.append("var __responseBody = ").append(toJsString(context.getResponseBody())).append(";\n");
            js.append("var __responseTime = ").append(context.getResponseTimeMs() != null ? context.getResponseTimeMs() : "null").append(";\n");
        }

        // Console capture
        js.append("""
                var console = {
                    log: function() {
                        var args = [];
                        for (var i = 0; i < arguments.length; i++) args.push(String(arguments[i]));
                        __consoleOutput.push(args.join(' '));
                    },
                    warn: function() {
                        var args = [];
                        for (var i = 0; i < arguments.length; i++) args.push(String(arguments[i]));
                        __consoleOutput.push('[WARN] ' + args.join(' '));
                    },
                    error: function() {
                        var args = [];
                        for (var i = 0; i < arguments.length; i++) args.push(String(arguments[i]));
                        __consoleOutput.push('[ERROR] ' + args.join(' '));
                    },
                    info: function() {
                        var args = [];
                        for (var i = 0; i < arguments.length; i++) args.push(String(arguments[i]));
                        __consoleOutput.push(args.join(' '));
                    }
                };
                """);

        // pm object
        js.append("""
                var pm = {
                    variables: {
                        get: function(key) { return __local.hasOwnProperty(key) ? __local[key] : undefined; },
                        set: function(key, value) { __local[key] = String(value); },
                        has: function(key) { return __local.hasOwnProperty(key); },
                        toObject: function() { return Object.assign({}, __local); }
                    },
                    globals: {
                        get: function(key) { return __globals.hasOwnProperty(key) ? __globals[key] : undefined; },
                        set: function(key, value) { __globals[key] = String(value); },
                        has: function(key) { return __globals.hasOwnProperty(key); },
                        unset: function(key) { delete __globals[key]; },
                        toObject: function() { return Object.assign({}, __globals); }
                    },
                    collectionVariables: {
                        get: function(key) { return __collection.hasOwnProperty(key) ? __collection[key] : undefined; },
                        set: function(key, value) { __collection[key] = String(value); },
                        has: function(key) { return __collection.hasOwnProperty(key); },
                        unset: function(key) { delete __collection[key]; },
                        toObject: function() { return Object.assign({}, __collection); }
                    },
                    environment: {
                        get: function(key) { return __environment.hasOwnProperty(key) ? __environment[key] : undefined; },
                        set: function(key, value) { __environment[key] = String(value); },
                        has: function(key) { return __environment.hasOwnProperty(key); },
                        unset: function(key) { delete __environment[key]; },
                        toObject: function() { return Object.assign({}, __environment); }
                    },
                    request: {
                        get url() { return __requestUrl; },
                        set url(v) { __requestUrl = v; },
                        get method() { return __requestMethod; },
                        set method(v) { __requestMethod = v; },
                        get headers() { return __requestHeaders; },
                        set headers(v) { __requestHeaders = v; },
                        get body() { return __requestBody; },
                        set body(v) { __requestBody = v; },
                        cancel: function() { __requestCancelled = true; }
                    },
                """);

        // pm.response (post-response only)
        if (isPostResponse) {
            js.append("""
                        response: {
                            get code() { return __responseStatus; },
                            get status() { return __responseStatus; },
                            get headers() { return __responseHeaders; },
                            text: function() { return __responseBody; },
                            json: function() { return JSON.parse(__responseBody); },
                            get responseTime() { return __responseTime; }
                        },
                    """);
        }

        // pm.test and pm.expect
        js.append("""
                    test: function(name, fn) {
                        try {
                            fn();
                            __assertions.push({name: name, passed: true, message: ''});
                        } catch(e) {
                            __assertions.push({name: name, passed: false, message: e.message || String(e)});
                        }
                    },
                    expect: function(actual) {
                        var negated = false;

                        function assert_fn(condition, msg) {
                            if (negated ? condition : !condition) {
                                throw new Error(msg);
                            }
                        }

                        var beObj = {
                            above: function(n) { assert_fn(actual > n, 'Expected ' + actual + ' to be above ' + n); return chain; },
                            below: function(n) { assert_fn(actual < n, 'Expected ' + actual + ' to be below ' + n); return chain; },
                            a: function(type) {
                                var actualType;
                                if (Array.isArray(actual)) actualType = 'array';
                                else if (actual === null) actualType = 'null';
                                else actualType = typeof actual;
                                assert_fn(actualType === type, 'Expected type ' + type + ' but got ' + actualType);
                                return chain;
                            },
                            get ok() { assert_fn(!!actual, 'Expected value to be truthy'); return chain; }
                        };

                        Object.defineProperty(beObj, 'true', { get: function() { assert_fn(actual === true, 'Expected true but got ' + actual); return chain; } });
                        Object.defineProperty(beObj, 'false', { get: function() { assert_fn(actual === false, 'Expected false but got ' + actual); return chain; } });
                        Object.defineProperty(beObj, 'null', { get: function() { assert_fn(actual === null, 'Expected null but got ' + actual); return chain; } });
                        Object.defineProperty(beObj, 'undefined', { get: function() { assert_fn(actual === undefined, 'Expected undefined but got ' + actual); return chain; } });
                        Object.defineProperty(beObj, 'empty', { get: function() {
                            if (typeof actual === 'string' || Array.isArray(actual)) {
                                assert_fn(actual.length === 0, 'Expected empty but length is ' + actual.length);
                            } else if (typeof actual === 'object' && actual !== null) {
                                assert_fn(Object.keys(actual).length === 0, 'Expected empty object');
                            }
                            return chain;
                        }});

                        var haveObj = {
                            property: function(key) { assert_fn(actual !== null && actual !== undefined && actual.hasOwnProperty(key), 'Expected to have property ' + key); return chain; },
                            lengthOf: function(n) {
                                var len = actual && actual.length !== undefined ? actual.length : -1;
                                assert_fn(len === n, 'Expected length ' + n + ' but got ' + len);
                                return chain;
                            }
                        };

                        var toObj = {
                            equal: function(expected) { assert_fn(actual === expected, 'Expected ' + JSON.stringify(expected) + ' but got ' + JSON.stringify(actual)); return chain; },
                            include: function(sub) {
                                if (typeof actual === 'string') {
                                    assert_fn(actual.indexOf(sub) !== -1, 'Expected to include ' + JSON.stringify(sub));
                                } else if (Array.isArray(actual)) {
                                    assert_fn(actual.indexOf(sub) !== -1, 'Expected array to include ' + JSON.stringify(sub));
                                }
                                return chain;
                            },
                            be: beObj,
                            have: haveObj,
                            get not() { negated = !negated; return toObj; }
                        };

                        var chain = { to: toObj };
                        return chain;
                    },
                    info: {
                        requestId: '',
                        requestName: '',
                        eventName: '%s'
                    }
                };
                """.formatted(isPostResponse ? "test" : "prerequest"));

        return js.toString();
    }

    /**
     * Reads back modified state from the JS context into the ScriptContext.
     *
     * @param jsContext the GraalJS context after script execution
     * @param context   the ScriptContext to update
     */
    private void readBackState(Context jsContext, ScriptContext context) {
        try {
            // Read back variable scopes
            readBackMap(jsContext, "__globals", context.getGlobalVariables());
            readBackMap(jsContext, "__collection", context.getCollectionVariables());
            readBackMap(jsContext, "__environment", context.getEnvironmentVariables());
            readBackMap(jsContext, "__local", context.getLocalVariables());

            // Read back request modifications
            Value urlVal = jsContext.eval("js", "__requestUrl");
            if (!urlVal.isNull()) {
                context.setRequestUrl(urlVal.asString());
            }
            Value methodVal = jsContext.eval("js", "__requestMethod");
            if (!methodVal.isNull()) {
                context.setRequestMethod(methodVal.asString());
            }
            Value bodyVal = jsContext.eval("js", "__requestBody");
            if (!bodyVal.isNull()) {
                context.setRequestBody(bodyVal.asString());
            }

            // Read back request headers
            readBackMap(jsContext, "__requestHeaders", context.getRequestHeaders());

            // Read back cancellation flag
            Value cancelVal = jsContext.eval("js", "__requestCancelled");
            context.setRequestCancelled(cancelVal.asBoolean());

            // Read back assertions
            Value assertionsVal = jsContext.eval("js", "JSON.stringify(__assertions)");
            String assertionsJson = assertionsVal.asString();
            List<Map<String, Object>> assertionList = JSON_MAPPER.readValue(assertionsJson,
                    new TypeReference<>() {});
            for (Map<String, Object> a : assertionList) {
                context.addAssertion(
                        (String) a.get("name"),
                        Boolean.TRUE.equals(a.get("passed")),
                        (String) a.get("message")
                );
            }

            // Read back console output
            Value consoleVal = jsContext.eval("js", "JSON.stringify(__consoleOutput)");
            String consoleJson = consoleVal.asString();
            List<String> lines = JSON_MAPPER.readValue(consoleJson, new TypeReference<>() {});
            for (String line : lines) {
                context.addConsoleLog(line);
            }
        } catch (Exception e) {
            log.warn("Failed to read back script state: {}", e.getMessage());
        }
    }

    /**
     * Reads back a JS object as a string-string map into the provided Java map.
     *
     * @param jsContext the GraalJS context
     * @param varName   the JS variable name to read
     * @param targetMap the Java map to update
     */
    private void readBackMap(Context jsContext, String varName, Map<String, String> targetMap) {
        if (targetMap == null) {
            return;
        }
        try {
            Value jsonVal = jsContext.eval("js", "JSON.stringify(" + varName + ")");
            String json = jsonVal.asString();
            Map<String, String> parsed = JSON_MAPPER.readValue(json, new TypeReference<>() {});
            targetMap.clear();
            targetMap.putAll(parsed);
        } catch (Exception e) {
            log.warn("Failed to read back {}: {}", varName, e.getMessage());
        }
    }

    /**
     * Serializes a string value for safe embedding in JavaScript source.
     *
     * @param value the Java string (nullable)
     * @return the JavaScript string literal, or "null" if the value is null
     */
    private String toJsString(String value) {
        if (value == null) {
            return "null";
        }
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "null";
        }
    }

    /**
     * Serializes a map to a JavaScript object literal string.
     *
     * @param map the map to serialize (nullable)
     * @return the JavaScript object literal, or "{}" if the map is null/empty
     */
    private String toJsObject(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return JSON_MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
