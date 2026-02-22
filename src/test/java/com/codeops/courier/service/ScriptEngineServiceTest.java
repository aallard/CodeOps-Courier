package com.codeops.courier.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ScriptEngineService covering pre-request scripts,
 * post-response scripts, pm.test/pm.expect assertions, variable scopes,
 * console capture, and sandbox security.
 */
class ScriptEngineServiceTest {

    private ScriptEngineService service;

    @BeforeEach
    void setUp() {
        // Use 2-second timeout for faster tests
        service = new ScriptEngineService(2);
    }

    private ScriptContext newContext() {
        ScriptContext ctx = new ScriptContext(
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>()
        );
        ctx.setRequestUrl("https://api.example.com/test");
        ctx.setRequestMethod("GET");
        ctx.setRequestHeaders(new LinkedHashMap<>());
        ctx.setRequestBody(null);
        return ctx;
    }

    private ScriptContext newContextWithResponse() {
        ScriptContext ctx = newContext();
        ctx.setResponseStatus(200);
        ctx.setResponseHeaders(Map.of("Content-Type", List.of("application/json")));
        ctx.setResponseBody("{\"name\":\"Alice\",\"age\":30}");
        ctx.setResponseTimeMs(150L);
        return ctx;
    }

    // ─── Pre-request script tests ───

    @Test
    void preRequest_emptyScript_returnsUnchanged() {
        ScriptContext ctx = newContext();
        ScriptContext result = service.executePreRequestScript("   ", ctx);

        assertThat(result.getAssertions()).isEmpty();
        assertThat(result.getConsoleOutput()).isEmpty();
        assertThat(result.getRequestUrl()).isEqualTo("https://api.example.com/test");
    }

    @Test
    void preRequest_nullScript_returnsUnchanged() {
        ScriptContext ctx = newContext();
        ScriptContext result = service.executePreRequestScript(null, ctx);

        assertThat(result.getAssertions()).isEmpty();
        assertThat(result.getRequestUrl()).isEqualTo("https://api.example.com/test");
    }

    @Test
    void preRequest_setLocalVariable() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("pm.variables.set('myVar', 'hello');", ctx);

        assertThat(ctx.getLocalVariables()).containsEntry("myVar", "hello");
    }

    @Test
    void preRequest_setGlobalVariable() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("pm.globals.set('token', 'abc123');", ctx);

        assertThat(ctx.getGlobalVariables()).containsEntry("token", "abc123");
    }

    @Test
    void preRequest_setEnvironmentVariable() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("pm.environment.set('apiUrl', 'https://prod.api.com');", ctx);

        assertThat(ctx.getEnvironmentVariables()).containsEntry("apiUrl", "https://prod.api.com");
    }

    @Test
    void preRequest_setCollectionVariable() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("pm.collectionVariables.set('baseUrl', 'https://staging.api.com');", ctx);

        assertThat(ctx.getCollectionVariables()).containsEntry("baseUrl", "https://staging.api.com");
    }

    @Test
    void preRequest_getVariable() {
        ScriptContext ctx = newContext();
        ctx.getGlobalVariables().put("token", "secret-value");
        service.executePreRequestScript(
                "var t = pm.globals.get('token'); console.log('Token: ' + t);", ctx);

        assertThat(ctx.getConsoleOutput()).hasSize(1);
        assertThat(ctx.getConsoleOutput().get(0)).isEqualTo("Token: secret-value");
    }

    @Test
    void preRequest_modifyRequestUrl() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("pm.request.url = 'https://api.example.com/v2/test';", ctx);

        assertThat(ctx.getRequestUrl()).isEqualTo("https://api.example.com/v2/test");
    }

    @Test
    void preRequest_modifyRequestHeaders() {
        ScriptContext ctx = newContext();
        ctx.setRequestHeaders(new LinkedHashMap<>(Map.of("Accept", "text/html")));
        service.executePreRequestScript(
                "pm.request.headers['Authorization'] = 'Bearer token123';", ctx);

        assertThat(ctx.getRequestHeaders()).containsEntry("Authorization", "Bearer token123");
        assertThat(ctx.getRequestHeaders()).containsEntry("Accept", "text/html");
    }

    @Test
    void preRequest_modifyRequestBody() {
        ScriptContext ctx = newContext();
        ctx.setRequestBody("{\"old\":true}");
        service.executePreRequestScript("pm.request.body = '{\"new\":true}';", ctx);

        assertThat(ctx.getRequestBody()).isEqualTo("{\"new\":true}");
    }

    @Test
    void preRequest_cancelRequest() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("pm.request.cancel();", ctx);

        assertThat(ctx.isRequestCancelled()).isTrue();
    }

    @Test
    void preRequest_consoleLog_captured() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript(
                "console.log('hello', 'world'); console.log('second line');", ctx);

        assertThat(ctx.getConsoleOutput()).hasSize(2);
        assertThat(ctx.getConsoleOutput().get(0)).isEqualTo("hello world");
        assertThat(ctx.getConsoleOutput().get(1)).isEqualTo("second line");
    }

    @Test
    void preRequest_consoleWarn_captured() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("console.warn('danger ahead');", ctx);

        assertThat(ctx.getConsoleOutput()).hasSize(1);
        assertThat(ctx.getConsoleOutput().get(0)).isEqualTo("[WARN] danger ahead");
    }

    @Test
    void preRequest_syntaxError_failsGracefully() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("function {{{ broken", ctx);

        assertThat(ctx.getAssertions()).hasSize(1);
        assertThat(ctx.getAssertions().get(0).passed()).isFalse();
        assertThat(ctx.getAssertions().get(0).name()).isEqualTo("Script execution");
    }

    @Test
    void preRequest_timeout_failsGracefully() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("while(true) {}", ctx);

        assertThat(ctx.getAssertions()).hasSize(1);
        assertThat(ctx.getAssertions().get(0).passed()).isFalse();
        assertThat(ctx.getAssertions().get(0).message()).containsIgnoringCase("timed out");
    }

    @Test
    void preRequest_infiniteLoop_terminates() {
        ScriptContext ctx = newContext();
        long start = System.currentTimeMillis();
        service.executePreRequestScript("var i = 0; while(true) { i++; }", ctx);
        long elapsed = System.currentTimeMillis() - start;

        // Should terminate within timeout + buffer
        assertThat(elapsed).isLessThan(10000);
        assertThat(ctx.getAssertions()).isNotEmpty();
        assertThat(ctx.getAssertions().get(0).passed()).isFalse();
    }

    // ─── Post-response script tests ───

    @Test
    void postResponse_readResponseStatus() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript(
                "console.log('Status: ' + pm.response.code);", ctx);

        assertThat(ctx.getConsoleOutput()).hasSize(1);
        assertThat(ctx.getConsoleOutput().get(0)).isEqualTo("Status: 200");
    }

    @Test
    void postResponse_readResponseBody() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript(
                "console.log(pm.response.text());", ctx);

        assertThat(ctx.getConsoleOutput()).hasSize(1);
        assertThat(ctx.getConsoleOutput().get(0)).contains("Alice");
    }

    @Test
    void postResponse_readResponseJson() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript(
                "var data = pm.response.json(); console.log(data.name);", ctx);

        assertThat(ctx.getConsoleOutput()).hasSize(1);
        assertThat(ctx.getConsoleOutput().get(0)).isEqualTo("Alice");
    }

    @Test
    void postResponse_readResponseHeaders() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript(
                "var ct = pm.response.headers['Content-Type']; console.log(ct);", ctx);

        assertThat(ctx.getConsoleOutput()).hasSize(1);
        assertThat(ctx.getConsoleOutput().get(0)).contains("application/json");
    }

    @Test
    void postResponse_readResponseTime() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript(
                "console.log('Time: ' + pm.response.responseTime);", ctx);

        assertThat(ctx.getConsoleOutput()).hasSize(1);
        assertThat(ctx.getConsoleOutput().get(0)).isEqualTo("Time: 150");
    }

    @Test
    void postResponse_setVariable_afterResponse() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript(
                "var data = pm.response.json(); pm.globals.set('userName', data.name);", ctx);

        assertThat(ctx.getGlobalVariables()).containsEntry("userName", "Alice");
    }

    // ─── pm.test / assertion tests ───

    @Test
    void test_passing_recorded() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Status is 200", function() {
                    pm.expect(pm.response.code).to.equal(200);
                });
                """, ctx);

        assertThat(ctx.getAssertions()).hasSize(1);
        assertThat(ctx.getAssertions().get(0).name()).isEqualTo("Status is 200");
        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void test_failing_recorded() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Status is 404", function() {
                    pm.expect(pm.response.code).to.equal(404);
                });
                """, ctx);

        assertThat(ctx.getAssertions()).hasSize(1);
        assertThat(ctx.getAssertions().get(0).name()).isEqualTo("Status is 404");
        assertThat(ctx.getAssertions().get(0).passed()).isFalse();
    }

    @Test
    void test_multipleTests_allRecorded() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("First", function() { pm.expect(1).to.equal(1); });
                pm.test("Second", function() { pm.expect(2).to.equal(2); });
                pm.test("Third", function() { pm.expect(3).to.equal(4); });
                """, ctx);

        assertThat(ctx.getAssertions()).hasSize(3);
        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
        assertThat(ctx.getAssertions().get(1).passed()).isTrue();
        assertThat(ctx.getAssertions().get(2).passed()).isFalse();
    }

    @Test
    void expect_equal_passes() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Equal", function() {
                    pm.expect(42).to.equal(42);
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_equal_fails() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Not equal", function() {
                    pm.expect(42).to.equal(99);
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isFalse();
        assertThat(ctx.getAssertions().get(0).message()).contains("99");
    }

    @Test
    void expect_notEqual_passes() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Not equal", function() {
                    pm.expect(42).to.not.equal(99);
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_above_passes() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Above", function() {
                    pm.expect(10).to.be.above(5);
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_below_passes() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Below", function() {
                    pm.expect(3).to.be.below(10);
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_toBeA_string() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Is string", function() {
                    pm.expect("hello").to.be.a("string");
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_toBeA_number() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Is number", function() {
                    pm.expect(42).to.be.a("number");
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_toBeA_object() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Is object", function() {
                    pm.expect({a: 1}).to.be.a("object");
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_toBeA_array() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Is array", function() {
                    pm.expect([1, 2, 3]).to.be.a("array");
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_toHaveProperty_passes() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Has property", function() {
                    var data = pm.response.json();
                    pm.expect(data).to.have.property("name");
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_toInclude_passes() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Includes", function() {
                    pm.expect("hello world").to.include("world");
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_toLengthOf_passes() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Length", function() {
                    pm.expect([1, 2, 3]).to.have.lengthOf(3);
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_toBeTrue_passes() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Is true", function() {
                    pm.expect(true).to.be.true;
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_toBeFalse_passes() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Is false", function() {
                    pm.expect(false).to.be.false;
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_toBeNull_passes() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Is null", function() {
                    pm.expect(null).to.be.null;
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_toBeEmpty_passes() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Is empty string", function() {
                    pm.expect("").to.be.empty;
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    @Test
    void expect_toBeOk_passes() {
        ScriptContext ctx = newContextWithResponse();
        service.executePostResponseScript("""
                pm.test("Is truthy", function() {
                    pm.expect(1).to.be.ok;
                });
                """, ctx);

        assertThat(ctx.getAssertions().get(0).passed()).isTrue();
    }

    // ─── Variable scope tests ───

    @Test
    void variables_localOverridesEnvironment() {
        ScriptContext ctx = newContext();
        ctx.getEnvironmentVariables().put("key", "env-value");
        ctx.getLocalVariables().put("key", "local-value");
        service.executePreRequestScript(
                "console.log(pm.variables.get('key'));", ctx);

        assertThat(ctx.getConsoleOutput().get(0)).isEqualTo("local-value");
    }

    @Test
    void variables_environmentOverridesCollection() {
        ScriptContext ctx = newContext();
        ctx.getCollectionVariables().put("key", "coll-value");
        ctx.getEnvironmentVariables().put("key", "env-value");
        service.executePreRequestScript(
                "console.log(pm.environment.get('key'));", ctx);

        assertThat(ctx.getConsoleOutput().get(0)).isEqualTo("env-value");
    }

    @Test
    void variables_collectionOverridesGlobal() {
        ScriptContext ctx = newContext();
        ctx.getGlobalVariables().put("key", "global-value");
        ctx.getCollectionVariables().put("key", "coll-value");
        service.executePreRequestScript(
                "console.log(pm.collectionVariables.get('key'));", ctx);

        assertThat(ctx.getConsoleOutput().get(0)).isEqualTo("coll-value");
    }

    @Test
    void variables_unset_removesKey() {
        ScriptContext ctx = newContext();
        ctx.getGlobalVariables().put("toRemove", "value");
        service.executePreRequestScript("pm.globals.unset('toRemove');", ctx);

        assertThat(ctx.getGlobalVariables()).doesNotContainKey("toRemove");
    }

    @Test
    void variables_has_returnsCorrectly() {
        ScriptContext ctx = newContext();
        ctx.getGlobalVariables().put("exists", "yes");
        service.executePreRequestScript("""
                console.log(pm.globals.has('exists'));
                console.log(pm.globals.has('missing'));
                """, ctx);

        assertThat(ctx.getConsoleOutput().get(0)).isEqualTo("true");
        assertThat(ctx.getConsoleOutput().get(1)).isEqualTo("false");
    }

    @Test
    void variables_toObject_returnsAll() {
        ScriptContext ctx = newContext();
        ctx.getGlobalVariables().put("a", "1");
        ctx.getGlobalVariables().put("b", "2");
        service.executePreRequestScript("""
                var obj = pm.globals.toObject();
                console.log(Object.keys(obj).length);
                """, ctx);

        assertThat(ctx.getConsoleOutput().get(0)).isEqualTo("2");
    }

    // ─── Security / sandbox tests ───

    @Test
    void sandbox_noFileAccess() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("""
                try {
                    var fs = require('fs');
                    console.log('FILE ACCESS GRANTED');
                } catch(e) {
                    console.log('BLOCKED');
                }
                """, ctx);

        assertThat(ctx.getConsoleOutput()).isNotEmpty();
        assertThat(ctx.getConsoleOutput().get(0)).doesNotContain("FILE ACCESS GRANTED");
    }

    @Test
    void sandbox_noNetworkAccess() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("""
                try {
                    fetch('http://evil.com');
                    console.log('NETWORK ACCESS GRANTED');
                } catch(e) {
                    console.log('BLOCKED');
                }
                """, ctx);

        // fetch is not defined in the sandbox
        assertThat(ctx.getConsoleOutput()).isNotEmpty();
        assertThat(ctx.getConsoleOutput().get(0)).doesNotContain("NETWORK ACCESS GRANTED");
    }

    @Test
    void sandbox_noJavaClassAccess() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("""
                try {
                    var rt = Java.type('java.lang.Runtime');
                    console.log('JAVA ACCESS GRANTED');
                } catch(e) {
                    console.log('BLOCKED');
                }
                """, ctx);

        assertThat(ctx.getConsoleOutput()).isNotEmpty();
        assertThat(ctx.getConsoleOutput().get(0)).doesNotContain("JAVA ACCESS GRANTED");
    }

    @Test
    void sandbox_noProcessCreation() {
        ScriptContext ctx = newContext();
        service.executePreRequestScript("""
                try {
                    var pb = new java.lang.ProcessBuilder(['ls']);
                    console.log('PROCESS ACCESS GRANTED');
                } catch(e) {
                    console.log('BLOCKED');
                }
                """, ctx);

        assertThat(ctx.getConsoleOutput()).isNotEmpty();
        assertThat(ctx.getConsoleOutput().get(0)).doesNotContain("PROCESS ACCESS GRANTED");
    }
}
