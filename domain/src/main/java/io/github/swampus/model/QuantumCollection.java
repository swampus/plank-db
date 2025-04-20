package io.github.swampus.model;

import lombok.Builder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class QuantumCollection {

    private final String name;
    private final Map<String, String> storage = new ConcurrentHashMap<>();

    public QuantumCollection(String name) {
        this.name = name;
    }

    public Set<String> keys() {
        return Set.copyOf(storage.keySet());
    }

    public String getName() {
        return name;
    }

    public void put(String key, String value) {
        storage.put(key, value);
    }

    public String get(String key) {
        return storage.get(key);
    }

    public void delete(String key) {
        storage.remove(key);
    }

    public Map<String, String> getAll() {
        return storage;
    }

    public boolean containsKey(String key) {
        return storage.containsKey(key);
    }
    public Map<String, String> entries() {
        return new HashMap<>(storage);
    }
}
