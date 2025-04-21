package io.github.swampus.usecase;

import io.github.swampus.ports.QuantumCollectionRepository;

public class DeleteCollectionUseCase {
    private final QuantumCollectionRepository repository;
    public DeleteCollectionUseCase(QuantumCollectionRepository repository) {
        this.repository = repository;
    }

    public void execute(String name) {
        repository.delete(name);
    }
}
