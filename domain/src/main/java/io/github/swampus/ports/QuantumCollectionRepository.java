package io.github.swampus.ports;

import io.github.swampus.model.QuantumCollection;

import java.util.Optional;

public interface QuantumCollectionRepository {
    Optional<QuantumCollection> findByName(String name);

    void save(QuantumCollection collection);

    void create(String name);

    void delete(String name);

}
