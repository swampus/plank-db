package io.github.swampus.controller;

import io.github.swampus.dto.*;
import io.github.swampus.usecase.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

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

    @Operation(summary = "Add key-value pair to a quantum collection")
    @ApiResponses(value = {
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

    @Operation(summary = "Delete an entry by key from a quantum collection")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entry deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Collection or key not found")
    })
    @DeleteMapping("/{collection}/entries")
    public ResponseEntity<Void> deleteEntry(
            @Parameter(description = "Collection name") @PathVariable String collection,
            @RequestBody DeleteEntryRequest request) {
        deleteEntryUseCase.execute(collection, request.getKey());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Perform quantum search by key",
            description = "Uses Grover's algorithm to locate the specified key in the collection.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Value found",
                    content = @Content(schema = @Schema(implementation = SearchResponse.class))),
            @ApiResponse(responseCode = "404", description = "Collection not found")
    })
    @PostMapping("/{collection}/search")
    public ResponseEntity<SearchResponse> search(
            @Parameter(description = "Collection name") @PathVariable String collection,
            @RequestBody SearchRequest request) {
        String value = searchEntryUseCase.execute(collection, request.getKey());
        return ResponseEntity.ok(new SearchResponse(value));
    }

    @Operation(summary = "Quantum range search (Grover)",
            description = """
                   Performs quantum search for any one key within the specified range [fromKey, toKey].
                   Due to the nature of Grover's algorithm, the result may be non-deterministic.
                   Returns a single match or 404 if none is found.
               """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Match found",
                    content = @Content(schema = @Schema(implementation = RangeQueryResponse.class))),
            @ApiResponse(responseCode = "404", description = "No matching key or collection not found")
    })
    @PostMapping("/{collection}/range")
    public ResponseEntity<?> rangeQuery(
            @Parameter(description = "Collection name") @PathVariable String collection,
            @RequestBody RangeQueryRequest request) {

        Optional<String> result = rangeQueryUseCase.execute(collection, request.getFromKey(), request.getToKey());

        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Collection or matching key not found.");
        }

        return ResponseEntity.ok(new RangeQueryResponse(result.get()));
    }

    @Operation(summary = "Get all entries in a collection")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns a map of all entries",
                    content = @Content(schema = @Schema(implementation = GetAllEntriesResponse.class))),
            @ApiResponse(responseCode = "404", description = "Collection not found")
    })
    @GetMapping("/{collection}/entries")
    public ResponseEntity<GetAllEntriesResponse> getAllEntries(
            @Parameter(description = "Collection name") @PathVariable String collection) {
        return ResponseEntity.ok(new GetAllEntriesResponse(getAllEntriesUseCase.execute(collection)));
    }

    @Operation(summary = "Create a new collection")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Collection created (or already existed)")
    })
    @PostMapping("/{collection}")
    public ResponseEntity<Void> createCollection(
            @Parameter(description = "Collection name") @PathVariable String collection) {
        createCollectionUseCase.execute(collection);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete a collection by name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Collection deleted")
    })
    @DeleteMapping("/{collection}")
    public ResponseEntity<Void> deleteCollection(
            @Parameter(description = "Collection name") @PathVariable String collection) {
        deleteCollectionUseCase.execute(collection);
        return ResponseEntity.ok().build();
    }
}
