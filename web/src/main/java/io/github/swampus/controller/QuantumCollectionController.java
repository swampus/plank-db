package io.github.swampus.controller;

import io.github.swampus.dto.*;
import io.github.swampus.mapper.QuantumResultMapper;
import io.github.swampus.model.QuantumResultModel;
import io.github.swampus.usecase.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/rest/v1/collections")
@RequiredArgsConstructor
public class QuantumCollectionController {

    private final AddEntryUseCase addEntryUseCase;
    private final DeleteEntryUseCase deleteEntryUseCase;
    private final SearchEntryUseCase searchEntryUseCase;
    private final RangeQueryUseCase rangeQueryUseCase;
    private final CreateCollectionUseCase createCollectionUseCase;
    private final DeleteCollectionUseCase deleteCollectionUseCase;
    private final GetAllEntriesUseCase getAllEntriesUseCase;
    private final QuantumResultMapper quantumResultMapper;

    @PostConstruct
    private void init() {
        createCollectionUseCase.execute("test");
        addEntry("test", AddEntryRequest.builder().key("k1").value("v1").build());
        addEntry("test", AddEntryRequest.builder().key("k2").value("v2").build());
        addEntry("test", AddEntryRequest.builder().key("k3").value("v3").build());
        addEntry("test", AddEntryRequest.builder().key("k4").value("v4").build());
    }

    @Operation(
            summary = "Add key-value pair to a quantum collection",
            description = "Stores a new key-value pair into the specified collection. " +
                    "Fails with 404 if the collection does not exist."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entry added successfully"),
            @ApiResponse(responseCode = "404", description = "Collection not found")
    })
    @PostMapping("/{collection}/entries")
    public ResponseEntity<Void> addEntry(
            @Parameter(description = "Collection name") @PathVariable String collection,
            @RequestBody AddEntryRequest request) {
        addEntryUseCase.execute(collection, request.getKey(), request.getValue());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Delete entry from a quantum collection",
            description = "Deletes an existing key from the specified collection."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entry deleted"),
            @ApiResponse(responseCode = "404", description = "Collection or key not found")
    })
    @DeleteMapping("/{collection}/entries")
    public ResponseEntity<Void> deleteEntry(
            @Parameter(description = "Collection name") @PathVariable String collection,
            @RequestBody DeleteEntryRequest request) {
        deleteEntryUseCase.execute(collection, request.getKey());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Quantum Grover search by key",
            description = """
                    Executes Grover's quantum search algorithm to locate a specific key within a quantum-encoded collection.
                    Internally, the input keys are binary-encoded, and a quantum oracle circuit is built to mark the desired state.
                    Grover's algorithm then amplifies the probability amplitude of the matching key.

                    The response includes:
                    - the matched key, value, and index (if found),
                    - the top quantum measurement and full probability distribution,
                    - confidence score and note (e.g., "Success" or "Low confidence"),
                    - quantum execution metadata (oracle depth, number of iterations, execution time),
                    - and detailed scientific notes explaining how the algorithm works.

                    ⚙️ **Backend selection**:
                    - If the application is configured with `backend=local`, the algorithm is executed using a quantum **simulator** (Qiskit Aer).
                    - If `backend=ibm`, the algorithm is executed on a real quantum processor via IBM Quantum services (requires an API token and registration).

                    📘 For backend configuration, see the `application.yml` or refer to the project README: https://github.com/swampus/plank-db

                    ⚠️ IBM execution may be slower and may introduce noise due to hardware constraints.
                    """,
            tags = {"Quantum Search"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Quantum search completed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = QuantumResultDTO.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "quantum_result": {
                                        "matched_key": "k2",
                                        "matched_value": "kiwi",
                                        "matched_index": 1,
                                        "top_measurement": "01",
                                        "oracle_expression": "(x01)",
                                        "num_qubits": 2,
                                        "probabilities": {
                                          "01": 0.86,
                                          "11": 0.14
                                        },
                                        "confidence_score": 0.86,
                                        "execution_time_ms": 17,
                                        "oracle_depth": 3,
                                        "iterations": 1,
                                        "note": "Success"
                                      },
                                      "scientific_notes": {
                                        "principle": "Grover's algorithm enables quadratic speedup for unstructured search problems.",
                                        "theory": "Grover's algorithm finds a marked item in √N steps using quantum amplitude amplification, compared to O(N) in classical search.",
                                        "circuit_behavior": "The oracle flips the phase of the target state. The diffusion operator then amplifies its probability.",
                                        "confidence_interpretation": "Low confidence (< 0.6) suggests insufficient amplification or noise; more iterations may help.",
                                        "qubit_commentary": "This execution used 2 qubits, allowing for 4 possible states in superposition.",
                                        "encoding_map": {
                                          "k1": "00",
                                          "k2": "01",
                                          "k3": "10",
                                          "k4": "11"
                                        },
                                        "used_iterations": 1
                                      }
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input (e.g., malformed JSON, null key, or invalid collection name)"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Specified key not found in the target collection"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal error during quantum execution (e.g., backend failure, script crash)"
            )
    })
    @PostMapping("/{collection}/search")
    public ResponseEntity<QuantumResultDTO> search(
            @Parameter(description = "Collection name") @PathVariable String collection,
            @RequestBody SearchRequest request) {
        QuantumResultModel value = searchEntryUseCase.execute(collection, request.getKey());
        QuantumResultDTO dto = quantumResultMapper.toDto(value);
        return ResponseEntity.ok(dto);
    }

    @Operation(
            summary = "Quantum range query",
            description = """
                Executes Grover's quantum algorithm to search for any matching key within the given range [`fromKey`, `toKey`] in the specified collection.
                
                This operation uses a binary-encoded oracle circuit constructed dynamically for all keys that fall lexicographically within the provided range.
                Internally, quantum amplitude amplification is applied to increase the likelihood of observing a valid match.
                
                **Note:** Due to the probabilistic nature of Grover's algorithm, the result may be `null` even if valid keys exist in the range.
                The algorithm returns *one* matched key with the highest observed probability — not necessarily the same on every run.
                
                You can optionally specify the backend to be either:
                - `"local"` — Qiskit Aer (CPU simulation, fast, default)
                - `"ibm"` — IBM Quantum backend (requires `.env` token `QUANTUM_IBM_TOKEN` and free IBM Quantum account)

                The response includes:
                - observed top measurement,
                - matched key and metadata (if available),
                - probabilities for each binary state,
                - oracle construction info,
                - execution statistics and timing,
                - and scientific notes explaining the underlying theory.
                """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Quantum range search executed",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = QuantumResultDTO.class),
                            examples = @ExampleObject(name = "Range Match", value = """
                                {
                                  "quantum_result": {
                                    "matched_key": "k3",
                                    "matched_value": "value_for_k3",
                                    "matched_index": 2,
                                    "top_measurement": "10",
                                    "oracle_expression": "x01 | x10 | x11",
                                    "num_qubits": 2,
                                    "probabilities": {
                                      "10": 0.78,
                                      "00": 0.22
                                    },
                                    "confidence_score": 0.78,
                                    "execution_time_ms": 1270,
                                    "oracle_depth": 3,
                                    "iterations": 1,
                                    "note": "Success"
                                  },
                                  "scientific_notes": {
                                    "principle": "Grover's algorithm enables quadratic speedup for unstructured search problems.",
                                    "theory": "Grover's algorithm is particularly powerful when the solution lies within a known subset (range). By building an oracle over a filtered set, we apply quantum amplitude amplification only where needed.",
                                    "circuit_behavior": "The oracle flips the phase of all matching states in the range. The diffusion operator amplifies their probability amplitude.",
                                    "confidence_interpretation": "Confidence is high. Match likely correct.",
                                    "qubit_commentary": "Used 2 qubit(s) (4 possible states). Observed match for 'k3'.",
                                    "encoding_map": {
                                      "k1": "00",
                                      "k2": "01",
                                      "k3": "10",
                                      "k4": "11"
                                    },
                                    "used_iterations": 1,
                                    "range_bounds": ["k2", "k4"]
                                  }
                                }
                                """)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "No key found in the given range or collection not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected error during quantum execution")
    })
    @PostMapping("/{collection}/range")
    public ResponseEntity<?> rangeQuery(
            @Parameter(description = "Collection name") @PathVariable String collection,
            @RequestBody RangeQueryRequest request) {

        QuantumResultModel model = rangeQueryUseCase.execute(collection, request.getFromKey(), request.getToKey());
        QuantumResultDTO dto = quantumResultMapper.toDto(model);
        return ResponseEntity.ok(dto);
    }

    @Operation(
            summary = "Get all entries in a collection",
            description = "Returns all key-value entries in the specified quantum collection."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Collection entries returned",
                    content = @Content(schema = @Schema(implementation = GetAllEntriesResponse.class))),
            @ApiResponse(responseCode = "404", description = "Collection not found")
    })
    @GetMapping("/{collection}/entries")
    public ResponseEntity<GetAllEntriesResponse> getAllEntries(
            @Parameter(description = "Collection name") @PathVariable String collection) {
        return ResponseEntity.ok(new GetAllEntriesResponse(getAllEntriesUseCase.execute(collection)));
    }

    @Operation(
            summary = "Create a new quantum collection",
            description = "Creates a new collection with the specified name. " +
                    "If the collection already exists, this operation is a no-op."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Collection created or already exists")
    })
    @PostMapping("/{collection}")
    public ResponseEntity<Void> createCollection(
            @Parameter(description = "Collection name") @PathVariable String collection) {
        createCollectionUseCase.execute(collection);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Delete a quantum collection",
            description = "Deletes the entire quantum collection by name. No undo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Collection deleted")
    })
    @DeleteMapping("/{collection}")
    public ResponseEntity<Void> deleteCollection(
            @Parameter(description = "Collection name") @PathVariable String collection) {
        deleteCollectionUseCase.execute(collection);
        return ResponseEntity.ok().build();
    }
}
