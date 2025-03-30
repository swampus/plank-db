package io.github.swampus.controller;

import io.github.swampus.dto.*;
import io.github.swampus.usecase.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/rest/v1/collections")
@RequiredArgsConstructor
@Tag(name = "Quantum Collections", description = """
        Quantum key-value collection storage with Grover-based search capabilities.
        All search operations are powered by Grover's quantum algorithm, executed on IBM Quantum or local emulator.
        """)
public class QuantumCollectionController {

    private final AddEntryUseCase addEntryUseCase;
    private final DeleteEntryUseCase deleteEntryUseCase;
    private final SearchEntryUseCase searchEntryUseCase;
    private final RangeQueryUseCase rangeQueryUseCase;
    private final CreateCollectionUseCase createCollectionUseCase;
    private final DeleteCollectionUseCase deleteCollectionUseCase;
    private final GetAllEntriesUseCase getAllEntriesUseCase;

    @Operation(
            summary = "Add a key-value pair to a quantum collection",
            description = "Stores a key-value pair in the specified collection. "
                    + "If the collection doesn't exist, this will return an error."
    )
    @ApiResponse(responseCode = "200", description = "Entry successfully added")
    @PostMapping("/{collection}/entries")
    public ResponseEntity<Void> addEntry(
            @PathVariable String collection,
            @RequestBody AddEntryRequest request) {
        addEntryUseCase.execute(collection, request.getKey(), request.getValue());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Delete a key from a quantum collection",
            description = "Removes the specified key from the given collection."
    )
    @ApiResponse(responseCode = "200", description = "Entry deleted")
    @DeleteMapping("/{collection}/entries")
    public ResponseEntity<Void> deleteEntry(
            @PathVariable String collection,
            @RequestBody DeleteEntryRequest request) {
        deleteEntryUseCase.execute(collection, request.getKey());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Search by key (quantum-powered)",
            description = """
                Uses Grover's Algorithm to search for the key.
                Result is probabilistic and executed via quantum simulator or IBM hardware.
                """
    )
    @ApiResponse(responseCode = "200", description = "Value found")
    @ApiResponse(responseCode = "404", description = "Key or collection not found")
    @PostMapping("/{collection}/search")
    public ResponseEntity<SearchResponse> search(
            @PathVariable String collection,
            @RequestBody SearchRequest request) {
        String value = searchEntryUseCase.execute(collection, request.getKey());
        return ResponseEntity.ok(new SearchResponse(value));
    }

    @Operation(
            summary = "Range query using Grover’s algorithm",
            description = """
                Performs a quantum-powered range search.
                May return any key within the range if a match is found.
                Note: the result is nullable due to the probabilistic nature of quantum search.
                """
    )
    @ApiResponse(responseCode = "200", description = "Match found")
    @ApiResponse(responseCode = "404", description = "Collection or match not found")
    @PostMapping("/{collection}/range")
    public ResponseEntity<?> rangeQuery(
            @PathVariable String collection,
            @RequestBody RangeQueryRequest request) {
        Optional<String> result = rangeQueryUseCase.execute(
                collection, request.getFromKey(), request.getToKey());
        return result.map(res -> ResponseEntity.ok(new RangeQueryResponse(res)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new RangeQueryResponse(null)));
    }

    @Operation(
            summary = "Get all entries",
            description = "Returns all key-value pairs in the specified collection."
    )
    @ApiResponse(responseCode = "200", description = "Collection contents returned")
    @GetMapping("/{collection}/entries")
    public ResponseEntity<GetAllEntriesResponse> getAllEntries(
            @PathVariable String collection) {
        return ResponseEntity.ok(new GetAllEntriesResponse(getAllEntriesUseCase.execute(collection)));
    }

    @Operation(
            summary = "Create a new quantum collection",
            description = "Initializes a new named key-value collection in memory."
    )
    @ApiResponse(responseCode = "200", description = "Collection created")
    @PostMapping("/{collection}")
    public ResponseEntity<Void> createCollection(@PathVariable String collection) {
        createCollectionUseCase.execute(collection);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Delete a quantum collection",
            description = "Removes the entire collection from memory."
    )
    @ApiResponse(responseCode = "200", description = "Collection deleted")
    @DeleteMapping("/{collection}")
    public ResponseEntity<Void> deleteCollection(@PathVariable String collection) {
        deleteCollectionUseCase.execute(collection);
        return ResponseEntity.ok().build();
    }
}
