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
        var opaExports = opa.exports();

        assertEquals(opaExports.opaWasmAbiVersion().getValue(), 1L);
        assertEquals(opaExports.opaWasmAbiMinorVersion().getValue(), 3L);

        var builtinsAddr = opaExports.builtins();
        var builtinsStringAddr = opaExports.opaJsonDump(builtinsAddr);
        assertEquals("{}", opaExports.memory().readCString(builtinsStringAddr));

        // Following the instructions here:
        // https://www.openpolicyagent.org/docs/latest/wasm/#evaluation
        var ctxAddr = opaExports.opaEvalCtxNew();

        var input = "{\"user\": \"alice\"}";
        var inputStrAddr = opaExports.opaMalloc(input.length());
        opaExports.memory().writeCString(inputStrAddr, input);
        var inputAddr = opaExports.opaJsonParse(inputStrAddr, input.length());
        opaExports.opaFree(inputStrAddr);
        opaExports.opaEvalCtxSetInput(ctxAddr, inputAddr);

        var data = "{ \"role\" : { \"alice\" : \"admin\", \"bob\" : \"user\" } }";
        var dataStrAddr = opaExports.opaMalloc(data.length());
        opaExports.memory().writeCString(dataStrAddr, data);
        var dataAddr = opaExports.opaJsonParse(dataStrAddr, data.length());
        opaExports.opaFree(dataStrAddr);
        opaExports.opaEvalCtxSetData(ctxAddr, dataAddr);

        var evalResult = OpaErrorCode.fromValue(opaExports.eval(ctxAddr));
        assertEquals(OpaErrorCode.OPA_ERR_OK, evalResult);

        int resultAddr = opaExports.opaEvalCtxGetResult(ctxAddr);
        int resultStrAddr = opaExports.opaJsonDump(resultAddr);
        var resultStr = opaExports.memory().readCString(resultStrAddr);
        opaExports.opaFree(resultStrAddr);
        assertEquals("[{\"result\":true}]", resultStr);

        // final cleanup of resources for demo purposes
        opaExports.opaValueFree(inputAddr);
        opaExports.opaValueFree(dataAddr);
        opaExports.opaValueFree(resultAddr);
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
