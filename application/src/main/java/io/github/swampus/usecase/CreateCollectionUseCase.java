package io.github.swampus.usecase;

import io.github.swampus.ports.QuantumCollectionRepository;

public class CreateCollectionUseCase {
    private final QuantumCollectionRepository repository;
    public CreateCollectionUseCase(QuantumCollectionRepository repository) {
        this.repository = repository;
    }
    public void execute(String name) {
        repository.create(name);
    }
}
