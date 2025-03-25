package io.github.swampus.usecase;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.ports.QuantumCollectionRepository;
import io.github.swampus.ports.QuantumRangeSearcher;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
public class RangeQueryUseCase {
    private final QuantumCollectionRepository repository;
    private final QuantumRangeSearcher searcher;

    public Optional<String> execute(String collectionName, String fromKey, String toKey) {
        return repository.findByName(collectionName)
                .flatMap(collection -> searcher.searchInRange(collection.keys(), fromKey, toKey));
    }
}