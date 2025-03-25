package io.github.swampus.usecase;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.ports.QuantumCollectionRepository;

public class SearchEntryUseCase {
    private final QuantumCollectionRepository repository;

    public SearchEntryUseCase(QuantumCollectionRepository repository) {
        this.repository = repository;
    }

    public String execute(String collectionName, String key) {
        return repository.findByName(collectionName)
                .orElseThrow(() -> new CollectionNotFoundException(collectionName))
                .get(key);
    }
}
