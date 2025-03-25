package io.github.swampus.controller;

import io.github.swampus.dto.AddEntryRequest;
import io.github.swampus.dto.DeleteEntryRequest;
import io.github.swampus.dto.SearchRequest;
import io.github.swampus.dto.SearchResponse;
import io.github.swampus.usecase.AddEntryUseCase;
import io.github.swampus.usecase.DeleteEntryUseCase;
import io.github.swampus.usecase.SearchEntryUseCase;
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
}
