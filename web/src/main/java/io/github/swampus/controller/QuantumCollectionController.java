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
            summary = "Perform quantum search by key",
            description = "Uses Grover’s algorithm to find an exact key in the quantum collection. " +
                    "Returns the associated value or 404 if not found."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Key found",
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

    @Operation(
            summary = "Quantum range query",
            description = """
                Uses Grover's algorithm to locate a single key in the given range [fromKey, toKey].
                Due to the probabilistic nature of Grover’s algorithm, the result may be nullable even if matching keys exist.
                Use this when you don’t care *which* key is returned, only that it lies within the range.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching key found",
                    content = @Content(schema = @Schema(implementation = RangeQueryResponse.class))),
            @ApiResponse(responseCode = "404", description = "No key in range or collection not found")
    })
    @PostMapping("/{collection}/range")
    public ResponseEntity<?> rangeQuery(
            @Parameter(description = "Collection name") @PathVariable String collection,
            @RequestBody RangeQueryRequest request) {

        Optional<String> result = rangeQueryUseCase.execute(
                collection,
                request.getFromKey(),
                request.getToKey()
        );

        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Collection or matching key not found.");
        }

        return ResponseEntity.ok(new RangeQueryResponse(result.get()));
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
