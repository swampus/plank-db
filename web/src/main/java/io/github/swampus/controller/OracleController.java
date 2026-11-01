package io.github.swampus.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import io.github.swampus.dto.oracle.*;
import io.github.swampus.oracle.OracleQueryService;
import io.github.swampus.oracle.OracleFailure;
import io.github.swampus.oracle.OracleQuery;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rest/v1/oracle")
public class OracleController {
    private final OracleQueryService service;
    private final ObjectMapper mapper;
    public OracleController(OracleQueryService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Operation(operationId = "explainOracle", summary = "Compile a reversible predicate oracle and explain the local circuit",
            description = "No measurement is performed. Unknown analytical cardinality requires manual iterations.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    schema = @Schema(implementation = OracleRequest.class), examples = @ExampleObject(value = """
                    {"width":4,"predicate":{"type":"BITMASK_EQUALS","mask":15,"value":9},"iterations":3,"shots":1024,"seed":42}
                    """))))
    @PostMapping(value = "/explain", consumes = "application/json")
    public OracleResponse explain(HttpServletRequest body) {
        long start = System.nanoTime();
        var report = service.explain(readRequest(body));
        return new OracleResponse(report, (System.nanoTime() - start) / 1e6);
    }

    @Operation(operationId = "searchOracle", summary = "Run simulated Grover search with a logic-compiled oracle on local Qiskit Aer",
            description = "Search register q[0] is LSB; measured strings are MSB first. No IBM calls or database records.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    schema = @Schema(implementation = OracleRequest.class), examples = @ExampleObject(value = """
                    {"width":4,"predicate":{"type":"BITMASK_EQUALS","mask":15,"value":9},"iterations":3,"shots":1024,"seed":42}
                    """))))
    @PostMapping(value = "/search", consumes = "application/json")
    public OracleResponse search(HttpServletRequest body) {
        long start = System.nanoTime();
        var report = service.search(readRequest(body));
        return new OracleResponse(report, (System.nanoTime() - start) / 1e6);
    }

    private OracleQuery readRequest(HttpServletRequest request) {
        if (request.getContentLengthLong() > 32768) throw OracleFailure.invalid("request exceeds 32 KiB");
        try {
            byte[] data = request.getInputStream().readNBytes(32769);
            if (data.length > 32768) throw OracleFailure.invalid("request exceeds 32 KiB");
            return OracleRequest.parse(mapper.readTree(data));
        } catch (IOException error) {
            throw OracleFailure.invalid("invalid JSON request");
        }
    }
}
