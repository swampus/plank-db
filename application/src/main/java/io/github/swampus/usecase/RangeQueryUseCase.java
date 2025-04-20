package io.github.swampus.usecase;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.model.QuantumResultModel;
import io.github.swampus.ports.QuantumCollectionRepository;
import io.github.swampus.ports.QuantumRangeSearcher;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class RangeQueryUseCase {
    private final QuantumCollectionRepository repository;
    private final QuantumRangeSearcher searcher;

    public QuantumResultModel execute(String collectionName, String fromKey, String toKey) {
        return repository.findByName(collectionName)
                .map(collection -> searcher.searchInRange(collection.keys(), fromKey, toKey))
                .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + collectionName));
    }
}