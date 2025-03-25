package io.github.swampus.usecase;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.model.QuantumCollection;
import io.github.swampus.ports.QuantumCollectionRepository;

public class AddEntryUseCase {
    private final QuantumCollectionRepository repository;

    public AddEntryUseCase(QuantumCollectionRepository repository) {
        this.repository = repository;
    }

    public void execute(String collectionName, String key, String value) {
        repository.findByName(collectionName)
                .orElseThrow(() -> new CollectionNotFoundException(collectionName))
                .put(key, value);
    }
}
