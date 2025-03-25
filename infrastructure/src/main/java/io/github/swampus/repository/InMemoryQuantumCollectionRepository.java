package io.github.swampus.repository;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.model.QuantumCollection;
import io.github.swampus.ports.QuantumCollectionRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryQuantumCollectionRepository implements QuantumCollectionRepository {

    private final Map<String, QuantumCollection> collections = new ConcurrentHashMap<>();

    @Override
    public Optional<QuantumCollection> findByName(String name) {
        return Optional.ofNullable(collections.get(name));
    }

    @Override
    public void save(QuantumCollection collection) {
        collections.put(collection.getName(), collection);
    }

    @Override
    public void create(String name) {
        collections.putIfAbsent(name, new QuantumCollection(name));
    }

    @Override
    public void delete(String name) {
        collections.remove(name);
    }
}

