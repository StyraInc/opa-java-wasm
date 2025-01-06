package com.styra.opa.wasm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dylibso.chicory.runtime.ByteBufferMemory;
import com.dylibso.chicory.wasm.types.MemoryLimits;
import java.io.FileInputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OpaTest {
    static Path wasmFile;

    @BeforeAll
    public static void beforeAll() throws Exception {
        wasmFile = OpaCli.compile("base", "opa/wasm/test/allowed").resolve("policy.wasm");
    }

    @Test
    public void lowLevelAPI() throws Exception {
        var opa =
                OpaWasm.builder()
                        .withJsonMapper(DefaultMappers.jsonMapper)
                        .withMemory(new ByteBufferMemory(new MemoryLimits(2, 2)))
                        .withInputStream(new FileInputStream(wasmFile.toFile()))
                        .build();

        assertEquals(opa.opaWasmAbiVersion().getValue(), 1L);
        assertEquals(opa.opaWasmAbiMinorVersion().getValue(), 3L);

        var builtinsAddr = opa.builtins();
        var builtinsStringAddr = opa.opaJsonDump(builtinsAddr);
        assertEquals("{}", opa.memory().readCString(builtinsStringAddr));

        // Following the instructions here:
        // https://www.openpolicyagent.org/docs/latest/wasm/#evaluation
        var ctxAddr = opa.opaEvalCtxNew();

        var input = "{\"user\": \"alice\"}";
        var inputStrAddr = opa.opaMalloc(input.length());
        opa.memory().writeCString(inputStrAddr, input);
        var inputAddr = opa.opaJsonParse(inputStrAddr, input.length());
        opa.opaFree(inputStrAddr);
        opa.opaEvalCtxSetInput(ctxAddr, inputAddr);

        var data = "{ \"role\" : { \"alice\" : \"admin\", \"bob\" : \"user\" } }";
        var dataStrAddr = opa.opaMalloc(data.length());
        opa.memory().writeCString(dataStrAddr, data);
        var dataAddr = opa.opaJsonParse(dataStrAddr, data.length());
        opa.opaFree(dataStrAddr);
        opa.opaEvalCtxSetData(ctxAddr, dataAddr);

        var evalResult = OpaErrorCode.fromValue(opa.eval(ctxAddr));
        assertEquals(OpaErrorCode.OPA_ERR_OK, evalResult);

        int resultAddr = opa.opaEvalCtxGetResult(ctxAddr);
        int resultStrAddr = opa.opaJsonDump(resultAddr);
        var resultStr = opa.memory().readCString(resultStrAddr);
        opa.opaFree(resultStrAddr);
        assertEquals("[{\"result\":true}]", resultStr);

        // final cleanup of resources for demo purposes
        opa.opaValueFree(inputAddr);
        opa.opaValueFree(dataAddr);
        opa.opaValueFree(resultAddr);
    }

    @Test
    public void highLevelAPI() throws Exception {
        var policy = OpaPolicy.builder().withPolicy(wasmFile).build();
        policy.data("{ \"role\" : { \"alice\" : \"admin\", \"bob\" : \"user\" } }");

        // evaluate the admin
        policy.input("{\"user\": \"alice\"}");
        Assertions.assertTrue(Utils.getResult(policy.evaluate()).asBoolean());

        // evaluate a user
        policy.input("{\"user\": \"bob\"}");
        Assertions.assertFalse(Utils.getResult(policy.evaluate()).asBoolean());

        // change the data of the policy
        policy.data("{ \"role\" : { \"bob\" : \"admin\", \"alice\" : \"user\" } }");

        // evaluate the admin
        policy.input("{\"user\": \"bob\"}");
        Assertions.assertTrue(Utils.getResult(policy.evaluate()).asBoolean());

        // evaluate a user
        policy.input("{\"user\": \"alice\"}");
        Assertions.assertFalse(Utils.getResult(policy.evaluate()).asBoolean());
    }
}
