package io.github.swampus.controller;


import io.github.swampus.dto.explain.ExplainPlanRequest;
import io.github.swampus.dto.explain.ExplainPlanResponse;
import io.github.swampus.model.ExplainPlanModel;
import io.github.swampus.quantum.DryRunResult;
import io.github.swampus.quantum.QuantumPlan;
import io.github.swampus.usecase.explain.ExplainPlanInput;
import io.github.swampus.usecase.explain.ExplainQuantumPlanUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST endpoint for the "Explain Quantum Plan" feature.
 * - Builds a Grover plan (KEY/RANGE).
 * - If backend is LOCAL and render/noise/topK are provided, performs a simulator dry-run (Aer)
 * and optionally returns base64 PNGs (circuit + histogram).
 * <p>
 * This controller remains thin: request mapping + simple model->DTO mapping.
 * Business logic stays in ExplainQuantumPlanUseCase.
 */
@RestController
@RequestMapping("/api/rest/v1/collections")
@RequiredArgsConstructor
public class ExplainPlanController {

    private final ExplainQuantumPlanUseCase explainUseCase;

    @Operation(
            summary = "Explain Grover plan for a key or a range",
            description = """
                    Builds a deterministic Grover **plan** for the given collection and target (KEY or RANGE).
                    
                    • Backends:
                      - `local` → builds plan **and** performs a fast Aer **dry-run** (probabilities in response)
                      - `ibm`   → builds plan **only** (no dry-run) to avoid queue/noise
                    
                    • Determinism:
                      - Same inputs → same `plan_id` and `encoding_map`
                      - Probabilities are stochastic; set `seed` for reproducibility
                    
                    • JSON naming:
                      - By default fields are **camelCase**
                      - If you enable `spring.jackson.property-naming-strategy=SNAKE_CASE`, response/request use **snake_case**
                    """,
            tags = {"Quantum Explain"}
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ExplainPlanRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "KEY (local, dry-run)",
                                    value = """
                                            {
                                              "mode": "KEY",
                                              "key": "k2",
                                              "backend": "local",
                                              "strategy": "AUTO",
                                              "shots": 512,
                                              "seed": 42
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "RANGE (local, dry-run)",
                                    value = """
                                            {
                                              "mode": "RANGE",
                                              "fromKey": "k2",
                                              "toKey": "k4",
                                              "backend": "local",
                                              "strategy": "AUTO"
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "KEY (IBM, plan only)",
                                    value = """
                                            {
                                              "mode": "KEY",
                                              "key": "k3",
                                              "backend": "ibm"
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "snake_case request (if enabled)",
                                    value = """
                                            {
                                              "mode": "KEY",
                                              "key": "k2",
                                              "backend": "local",
                                              "shots": 256,
                                              "seed": 7
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Explain built successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExplainPlanResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "KEY + local dry-run (snake_case response)",
                                            value = """
                                                    {
                                                      "plan": {
                                                        "mode": "KEY",
                                                        "target_label": "k2",
                                                        "encoding_map": {"k1":"00","k2":"01","k3":"10","k4":"11"},
                                                        "marked_states": ["01"],
                                                        "estimated_m": 1,
                                                        "collection_size_n": 4,
                                                        "num_qubits": 2,
                                                        "optimal_iterations": 1,
                                                        "iterations_used": 1,
                                                        "oracle_expression": "(x01)",
                                                        "estimated_oracle_depth": 4,
                                                        "estimated_gate_counts": {"h":4,"x":2,"mcx":1},
                                                        "backend": {"name":"local/aer_simulator","shots":512,"seed":42,"noise_model":false},
                                                        "notes": [
                                                          "Grover ~√(N/M); AUTO uses floor(π/4 * sqrt(N/M)).",
                                                          "Diffusion amplifies the marked states after the oracle phase flip.",
                                                          "Depth/gate counts are coarse estimates; actual values depend on oracle synthesis."
                                                        ],
                                                        "plan_id": "a1b2c3d4e5f6"
                                                      },
                                                      "dry_run": {
                                                        "top_measurement": "01",
                                                        "probabilities": {"01":0.86,"11":0.14},
                                                        "confidence_score": 0.86,
                                                        "execution_time_ms": 18
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "RANGE + local dry-run",
                                            value = """
                                                    {
                                                      "plan": {
                                                        "mode": "RANGE",
                                                        "target_label": "[k2..k4]",
                                                        "encoding_map": {"k1":"00","k2":"01","k3":"10","k4":"11"},
                                                        "marked_states": ["01","10","11"],
                                                        "estimated_m": 3,
                                                        "collection_size_n": 4,
                                                        "num_qubits": 2,
                                                        "optimal_iterations": 1,
                                                        "iterations_used": 1,
                                                        "oracle_expression": "x01 | x10 | x11",
                                                        "estimated_oracle_depth": 4,
                                                        "estimated_gate_counts": {"h":4,"x":6,"mcx":3},
                                                        "backend": {"name":"local/aer_simulator","shots":2048,"seed":null,"noise_model":false},
                                                        "notes": [],
                                                        "plan_id": "cafe4ed00b1d"
                                                      },
                                                      "dry_run": {
                                                        "top_measurement": "10",
                                                        "probabilities": {"01":0.31,"10":0.36,"11":0.30},
                                                        "confidence_score": 0.36,
                                                        "execution_time_ms": 22
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "IBM (plan only, no dry-run)",
                                            value = """
                                                    {
                                                      "plan": {
                                                        "mode": "KEY",
                                                        "target_label": "k3",
                                                        "encoding_map": {"k1":"00","k2":"01","k3":"10","k4":"11"},
                                                        "marked_states": ["10"],
                                                        "estimated_m": 1,
                                                        "collection_size_n": 4,
                                                        "num_qubits": 2,
                                                        "optimal_iterations": 1,
                                                        "iterations_used": 1,
                                                        "oracle_expression": "(x10)",
                                                        "estimated_oracle_depth": 4,
                                                        "estimated_gate_counts": {"h":4,"x":2,"mcx":1},
                                                        "backend": {"name":"ibm/runtime","shots":2048,"seed":null,"noise_model":true},
                                                        "notes": [],
                                                        "plan_id": "deadbeef42aa"
                                                      }
                                                      /* no dry_run */
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Missing key for KEY mode",
                                    value = """
                                            {
                                              "timestamp": "2025-08-31T12:34:56Z",
                                              "status": 400,
                                              "error": "Bad Request",
                                              "message": "key must be provided for KEY mode",
                                              "path": "/api/rest/v1/collections/test/explain"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Collection/key/range not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "Collection not found",
                                            value = """
                                                    {
                                                      "timestamp": "2025-08-31T12:34:56Z",
                                                      "status": 404,
                                                      "error": "Not Found",
                                                      "message": "Collection not found: test",
                                                      "path": "/api/rest/v1/collections/test/explain"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Key not found",
                                            value = """
                                                    {
                                                      "timestamp": "2025-08-31T12:34:56Z",
                                                      "status": 404,
                                                      "error": "Not Found",
                                                      "message": "Key not found: k9",
                                                      "path": "/api/rest/v1/collections/test/explain"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Simulator/backend error during dry-run",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Aer failure",
                                    value = """
                                            {
                                              "timestamp": "2025-08-31T12:34:56Z",
                                              "status": 502,
                                              "error": "Bad Gateway",
                                              "message": "Aer simulator failed: exit 1",
                                              "path": "/api/rest/v1/collections/test/explain"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected error",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Generic error",
                                    value = """
                                            {
                                              "timestamp": "2025-08-31T12:34:56Z",
                                              "status": 500,
                                              "error": "Internal Server Error",
                                              "message": "Unexpected error",
                                              "path": "/api/rest/v1/collections/test/explain"
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/{collection}/explain")
    public ResponseEntity<ExplainPlanResponse> explain(
            @Parameter(description = "Collection name") @PathVariable String collection,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = ExplainPlanRequest.class))
            )
            @RequestBody ExplainPlanRequest request
    ) {
        // 1) Build use-case input with safe defaults for optional flags
        ExplainPlanInput input = ExplainPlanInput.builder()
                .mode(request.getMode())
                .key(request.getKey())
                .fromKey(request.getFromKey())
                .toKey(request.getToKey())
                .backend(request.getBackend())
                .strategy(request.getStrategy())
                .iterations(request.getIterations())
                .shots(request.getShots())
                .seed(request.getSeed())
                .render(Boolean.TRUE.equals(request.getRender()))
                .noise(request.getNoise() == null ? 0.0 : request.getNoise())
                .topK(request.getTopK())
                .build();

        // 2) Delegate to application layer
        ExplainPlanModel model = explainUseCase.execute(collection, input);

        // 3) Map to web DTO
        ExplainPlanResponse dto = toResponse(model);

        return ResponseEntity.ok(dto);
    }

    // ---------------- mapping (model -> DTO) ----------------

    private ExplainPlanResponse toResponse(ExplainPlanModel model) {
        QuantumPlan p = model.getPlan();

        ExplainPlanResponse.PlanDTO.BackendDTO backendDTO = new ExplainPlanResponse.PlanDTO.BackendDTO(
                p.backend().name(),
                p.backend().shots(),
                p.backend().seed(),
                p.backend().noiseModel()
        );

        ExplainPlanResponse.PlanDTO planDTO = ExplainPlanResponse.PlanDTO.builder()
                .mode(p.mode().name())
                .targetLabel(p.targetLabel())
                .encodingMap(p.encodingMap())
                .markedStates(p.markedStates())
                .estimatedM(p.estimatedM())
                .collectionSizeN(p.collectionSizeN())
                .numQubits(p.numQubits())
                .optimalIterations(p.optimalIterations())
                .iterationsUsed(p.iterationsUsed())
                .oracleExpression(p.oracleExpression())
                .estimatedOracleDepth(p.estimatedOracleDepth())
                .estimatedGateCounts(p.estimatedGateCounts())
                .backend(backendDTO)
                .notes(p.note())
                .planId(p.planId())
                .build();

        ExplainPlanResponse.DryRunDTO dryDTO = toDryRunDTO(model.getDryRun());

        return ExplainPlanResponse.builder()
                .plan(planDTO)
                .dryRun(dryDTO)
                .build();
    }

    private ExplainPlanResponse.DryRunDTO toDryRunDTO(DryRunResult dry) {
        if (dry == null) return null;

        List<ExplainPlanResponse.TopHitDTO> topK = null;
        if (dry.topK() != null) {
            topK = dry.topK().stream()
                    .map(h -> new ExplainPlanResponse.TopHitDTO(h.state(), h.p()))
                    .collect(Collectors.toList());
        }

        return ExplainPlanResponse.DryRunDTO.builder()
                .topMeasurement(dry.topMeasurement())
                .probabilities(dry.probabilities())
                .probabilitiesNoisy(dry.probabilitiesNoisy())
                .confidenceScore(dry.confidenceScore())
                .executionTimeMs(dry.executionTimeMs())
                .topK(topK)
                .circuitPngB64(dry.circuitPngB64())
                .histogramPngB64(dry.histogramPngB64())
                .build();
    }
}
