package com.dylibso.wasm.opa;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.dylibso.wasm.opa.Utils.getResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultipleEntrypointsTest {
    static Path wasmFile;

    @BeforeAll
    public static void beforeAll() throws Exception {
        wasmFile = OpaCli.compile("multiple-entrypoints", "example", "example/one", "example/two").resolve("policy.wasm");
    }

    @Test
    public void shouldRunWithDefaultEntrypoint() throws Exception {
        try (var policy = Opa.loadPolicy(wasmFile)) {
            var result = getResult(policy.evaluate());

            assertTrue(result.size() > 0);
            assertTrue(result.has("one"));
            assertTrue(result.has("two"));
        }
    }

    @Test
    public void shouldRunWithNumberedEntrypointSpecified() throws Exception {
        try (var policy = Opa.loadPolicy(wasmFile)) {
            policy.input("{}").entrypoint("example/one");

            var result = getResult(policy.evaluate());

            assertTrue(result.size() > 0);
            assertFalse(result.findValue("myRule").asBoolean());
            assertFalse(result.findValue("myOtherRule").asBoolean());
        }
    }

    @Test
    public void shouldRunWithSecondEntrypointSpecified() throws Exception {
        try (var policy = Opa.loadPolicy(wasmFile)) {
            policy.input("{}").entrypoint("example/two");

            var result = getResult(policy.evaluate());

            assertTrue(result.size() > 0);
            assertFalse(result.findValue("ourRule").asBoolean());
            assertFalse(result.findValue("theirRule").asBoolean());
        }
    }

    @Test
    public void shouldNotRunIfEntrypointStringDoesNotExist() throws Exception {
        try (var policy = Opa.loadPolicy(wasmFile)) {
            var exception = assertThrows(IllegalArgumentException.class, () -> policy.entrypoint("not/a/real/entrypoint"));
            assertEquals("entrypoint not/a/real/entrypoint is not valid in this instance", exception.getMessage());
        }
    }
}
