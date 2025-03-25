package io.github.swampus.controller;

import io.github.swampus.dto.*;
import io.github.swampus.usecase.*;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
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

    @PostMapping("/{collection}/entries")
    public ResponseEntity<Void> addEntry(
            @PathVariable String collection,
            @RequestBody AddEntryRequest request) {
        addEntryUseCase.execute(collection, request.getKey(), request.getValue());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{collection}/entries")
    public ResponseEntity<Void> deleteEntry(
            @PathVariable String collection,
            @RequestBody DeleteEntryRequest request) {
        deleteEntryUseCase.execute(collection, request.getKey());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{collection}/search")
    public ResponseEntity<SearchResponse> search(
            @PathVariable String collection,
            @RequestBody SearchRequest request) {

        String value = searchEntryUseCase.execute(collection, request.getKey());
        return ResponseEntity.ok(new SearchResponse(value));
    }

    @PostMapping("/{collection}/create")
    public ResponseEntity<CreateCollectionResponse> createCollection(@PathVariable String collection) {
        createCollectionUseCase.execute(collection);
        return ResponseEntity.ok(new CreateCollectionResponse(collection, "created"));
    }

    @GetMapping("/{collection}/entries")
    public ResponseEntity<GetAllEntriesResponse> getAllEntries(@PathVariable String collection) {
        Map<String, String> entries = getAllEntriesUseCase.execute(collection);
        return ResponseEntity.ok(new GetAllEntriesResponse(entries));
    }

    @DeleteMapping("/{collection}")
    public ResponseEntity<DeleteCollectionResponse> deleteCollection(@PathVariable String collection) {
        deleteCollectionUseCase.execute(collection);
        return ResponseEntity.ok(new DeleteCollectionResponse(collection, "deleted"));
    }
    @PostMapping("/{collection}/range")
    public ResponseEntity<?> rangeQuery(
            @PathVariable String collection,
            @RequestBody RangeQueryRequest request
    ) {

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
}
