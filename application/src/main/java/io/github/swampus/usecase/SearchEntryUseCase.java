package io.github.swampus.usecase;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.model.QuantumResultModel;
import io.github.swampus.ports.QuantumCollectionRepository;
import io.github.swampus.ports.QuantumSearcher;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SearchEntryUseCase {
    private final QuantumCollectionRepository repository;
    private final QuantumSearcher searcher;

    public QuantumResultModel execute(String collectionName, String key) {
        return repository.findByName(collectionName)
                .map(collection -> searcher.search(key, collection.keys()))
                .orElseThrow(() -> new CollectionNotFoundException(collectionName));
    }
}
