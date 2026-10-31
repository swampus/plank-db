package io.github.swampus.controller;

import io.github.swampus.oracle.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OracleController.class)
@Import(OracleControllerTest.Config.class)
@TestPropertySource(properties = "spring.jackson.property-naming-strategy=SNAKE_CASE")
class OracleControllerTest {
    @Autowired MockMvc mvc;
    @MockBean OracleCompilerPort compiler;
    @MockBean OracleExecutionPort executor;
    static final String REQUEST = """
            {"width":4,"predicate":{"type":"BITMASK_EQUALS","mask":15,"value":9},"iterations":3,"shots":1024,"seed":42}
            """;

    @TestConfiguration
    static class Config {
        @Bean OracleQueryService oracleService(OracleCompilerPort compiler, OracleExecutionPort executor) {
            return new OracleQueryService(compiler, executor, true, 8, 16, 8192, 32);
        }
    }

    @Test void snakeCaseContractAndSearchDispatch() throws Exception {
        var top = new OracleReport.Measurement("1001", 9, 100, 1, true);
        var report = new OracleReport("test-compiler", 1, Map.of("type", "EQUALS", "value", 9), "a".repeat(64),
                "local/aer_simulator/statevector/CPU", "1.1.1", "0.14.2", true, false, 1, 3, 100, 42,
                .96, 1.0, List.of(top), top, new OracleReport.ClassicalBaseline(List.of(9), 1, .01),
                new OracleReport.Metrics(4, 1, 5, 512, 10, 100, Map.of("cx", 10), 10),
                new OracleReport.Timings(.1, .2, .3, .4, .5, .6, .7, 2.8), "test circuit", List.of());
        when(executor.search(any())).thenReturn(report);
        mvc.perform(post("/api/rest/v1/oracle/search").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.normalized_predicate.value").value(9))
                .andExpect(jsonPath("$.oracle_fingerprint").value("a".repeat(64)))
                .andExpect(jsonPath("$.top_measurement.bits").value("1001"))
                .andExpect(jsonPath("$.top_measurement.valid").value(true))
                .andExpect(jsonPath("$.observed_valid_probability").value(1))
                .andExpect(jsonPath("$.metrics.total_qubits").value(5))
                .andExpect(jsonPath("$.timings.simulation_ms").value(.6))
                .andExpect(jsonPath("$.end_to_end_ms").isNumber());
        verify(executor).search(any());
        verifyNoInteractions(compiler);
    }

    @Test void invalidRequestShapesAndNumbersAreRejectedBeforePython() throws Exception {
        for (String request : List.of("{}", REQUEST.replace("\"width\":4", "\"width\":9"),
                REQUEST.replace("\"width\":4", "\"width\":4.5"), REQUEST.replace("\"width\":4", "\"width\":\"4\""),
                REQUEST.replace("\"shots\":1024", "\"shots\":8193"), REQUEST.replace("\"mask\":15", "\"mask\":16"),
                REQUEST.replace("\"value\":9", "\"value\":-1"), REQUEST.replace("\"iterations\":3", "\"iterations\":33"),
                REQUEST.replace("\"seed\":42", "\"seed\":null"), REQUEST.replace("BITMASK_EQUALS", "SQL"),
                REQUEST.replace("\"seed\":42", "\"seed\":42,\"code\":\"no\""),
                "{\"width\":4,\"predicate\":{\"type\":\"AND\",\"children\":[]}}"))
            mvc.perform(post("/api/rest/v1/oracle/search").contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        verifyNoInteractions(compiler, executor);
    }

    @Test void explainUsesCompilerOnly() throws Exception {
        mvc.perform(post("/api/rest/v1/oracle/explain").contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isOk());
        verify(compiler).compile(any());
        verifyNoInteractions(executor);
    }

    @Test void oversizedMalformedAndDuplicateJsonIsRejected() throws Exception {
        for (String request : List.of(" ".repeat(32769), "{", REQUEST + "{}", REQUEST.replace("\"width\":4", "\"width\":4,\"width\":3")))
            mvc.perform(post("/api/rest/v1/oracle/search").contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(compiler, executor);
    }

    @Test void timeoutAndBackendErrorsHaveStructuredResponses() throws Exception {
        when(executor.search(any())).thenThrow(new OracleFailure(OracleFailure.Kind.TIMEOUT, "oracle Python execution timed out"));
        mvc.perform(post("/api/rest/v1/oracle/search").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.error_id").isString());
        doThrow(new OracleFailure(OracleFailure.Kind.BACKEND, "malformed output")).when(executor).search(any());
        mvc.perform(post("/api/rest/v1/oracle/search").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadGateway());
    }
}
