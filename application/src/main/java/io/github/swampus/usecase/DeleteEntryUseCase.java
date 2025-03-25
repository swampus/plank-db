package io.github.swampus.usecase;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.model.QuantumCollection;
import io.github.swampus.ports.QuantumCollectionRepository;

public class DeleteEntryUseCase {
    private final QuantumCollectionRepository repository;
    public DeleteEntryUseCase(QuantumCollectionRepository repository) {
        this.repository = repository;
    }

    public void execute(String collectionName, String key) {
        repository.findByName(collectionName)
                .orElseThrow(() -> new CollectionNotFoundException(collectionName))
                .delete(key);
    }
}
