package io.github.swampus.quantum.db.internal;

import io.github.swampus.quantum.db.QuantumMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QuantumCollectionManager {
    private final Map<String, QuantumMap<String, String>> collections = new ConcurrentHashMap<>();

    public void createCollection(String name, QuantumMap<String, String> map) {
        collections.put(name, map);
    }

    public void deleteCollection(String name) {
        collections.remove(name);
    }

    public QuantumMap<String, String> getCollection(String name) {
        return collections.get(name);
    }

    public boolean exists(String name) {
        return collections.containsKey(name);
    }

    public Map<String, QuantumMap<String, String>> getAll() {
        return collections;
    }
}
