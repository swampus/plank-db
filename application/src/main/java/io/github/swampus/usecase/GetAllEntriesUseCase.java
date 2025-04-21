package io.github.swampus.usecase;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.model.QuantumCollection;
import io.github.swampus.ports.QuantumCollectionRepository;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class GetAllEntriesUseCase {

    private final QuantumCollectionRepository repository;

    public Map<String, String> execute(String collectionName) {
        QuantumCollection collection = repository.findByName(collectionName)
                .orElseThrow(() -> new CollectionNotFoundException(collectionName));
        return collection.entries();
    }
}
